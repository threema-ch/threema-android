/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.voip.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.slf4j.Logger;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DNDUtil;
import ch.threema.app.utils.IdUtil;
import ch.threema.app.utils.MediaPlayerStateWrapper;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.SoundUtil;
import ch.threema.app.voip.CallState;
import ch.threema.app.voip.CallStateSnapshot;
import ch.threema.app.voip.Config;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.voip.receivers.VoipMediaButtonReceiver;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.connection.MessageQueue;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallHangupMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallOfferMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingData;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallRingingMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesData;
import ch.threema.domain.protocol.csp.messages.voip.VoipICECandidatesMessage;
import ch.threema.domain.protocol.csp.messages.voip.VoipMessage;
import ch.threema.domain.protocol.csp.messages.voip.features.VideoFeature;
import ch.threema.storage.models.ContactModel;
import java8.util.concurrent.CompletableFuture;

import static ch.threema.app.ThreemaApplication.INCOMING_CALL_NOTIFICATION_ID;
import static ch.threema.app.ThreemaApplication.getAppContext;
import static ch.threema.app.notifications.NotificationBuilderWrapper.VIBRATE_PATTERN_INCOMING_CALL;
import static ch.threema.app.notifications.NotificationBuilderWrapper.VIBRATE_PATTERN_SILENT;
import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_CALL;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE;
import static ch.threema.app.voip.activities.CallActivity.EXTRA_ACCEPT_INCOMING_CALL;
import static ch.threema.app.voip.services.CallRejectWorkerKt.KEY_CALL_ID;
import static ch.threema.app.voip.services.CallRejectWorkerKt.KEY_CONTACT_IDENTITY;
import static ch.threema.app.voip.services.CallRejectWorkerKt.KEY_REJECT_REASON;
import static ch.threema.app.voip.services.VoipCallService.ACTION_ICE_CANDIDATES;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_ACTIVITY_MODE;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CALL_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CANCEL_WEAR;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CANDIDATES;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CONTACT_IDENTITY;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_IS_INITIATOR;

/**
 * The service keeping track of VoIP call state.
 *
 * This class is (intended to be) thread safe.
 */
@AnyThread
public class VoipStateService implements AudioManager.OnAudioFocusChangeListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("VoipStateService");
	private final static String LIFETIME_SERVICE_TAG = "VoipStateService";

	public static final int VIDEO_RENDER_FLAG_NONE = 0x00;
	public static final int VIDEO_RENDER_FLAG_INCOMING = 0x01;
	public static final int VIDEO_RENDER_FLAG_OUTGOING = 0x02;

	// system managers
	private final AudioManager audioManager;
	private final NotificationManagerCompat notificationManagerCompat;
	private final NotificationManager notificationManager;

	// Threema services
	private final ContactService contactService;
	private final RingtoneService ringtoneService;
	private final PreferenceService preferenceService;
	private final MessageService messageService;
	private final LifetimeService lifetimeService;

	// Message sending
	private final MessageQueue messageQueue;

	// App context
	private final Context appContext;

	// State
	private volatile Boolean initiator = null;
	private final CallState callState = new CallState();
	private Long callStartTimestamp = null;
	private boolean isPeerRinging = false;

	// Map that stores incoming offers
	private final HashMap<Long, VoipCallOfferData> offerMap = new HashMap<>();

	// Flag for designating current user configuration
	private int videoRenderMode = VIDEO_RENDER_FLAG_NONE;

	// Candidate cache
	private final Map<String, List<VoipICECandidatesData>> candidatesCache;

	// Call cache
	private final Set<Long> recentCallIds = new HashSet<>();

	// Notifications
	private final List<String> callNotificationTags = new ArrayList<>();
	private MediaPlayerStateWrapper ringtonePlayer;
	private @NonNull CompletableFuture<Void> ringtoneAudioFocusAbandoned = CompletableFuture.completedFuture(null);

	// Video
	private @Nullable VideoContext videoContext;
	private @NonNull CompletableFuture<VideoContext> videoContextFuture = new CompletableFuture<>();

	// Pending intents
	private @Nullable PendingIntent acceptIntent;

	// Connection status
	private boolean connectionAcquired = false;

	// Timeouts
	private static final int RINGING_TIMEOUT_SECONDS = 60;
	private static final int VOIP_CONNECTION_LINGER = 1000 * 5;

	private final AtomicBoolean timeoutReject = new AtomicBoolean(true);

	private ScreenOffReceiver screenOffReceiver;

	private class ScreenOffReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			muteRingtone();
		}
	}

	public VoipStateService(ContactService contactService,
	                        RingtoneService ringtoneService,
	                        PreferenceService preferenceService,
	                        MessageService messageService,
	                        MessageQueue messageQueue,
	                        LifetimeService lifetimeService,
	                        final Context appContext) {
		this.contactService = contactService;
		this.ringtoneService = ringtoneService;
		this.preferenceService = preferenceService;
		this.messageService = messageService;
		this.lifetimeService = lifetimeService;
		this.messageQueue = messageQueue;
		this.appContext = appContext;
		this.candidatesCache = new HashMap<>();
		this.notificationManagerCompat = NotificationManagerCompat.from(appContext);
		this.notificationManager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
		this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
	}

	//region Logging

	// Note: Because the VoipStateService is not tied to a single call ID, we need to specify
	//       the call ID for every logging call. These helper methods provide some boilerplate
	//       code to make this easier.

	private static void logCallTrace(long callId, String message) {
		logger.trace("[cid={}]: {}", callId, message);
	}

	private static void logCallTrace(long callId, @NonNull String message, Object... arguments) {
		logger.trace("[cid=" + callId + "]: " + message, arguments);
	}

	private static void logCallDebug(long callId, String message) {
		logger.debug("[cid={}]: {}", callId, message);
	}

	private static void logCallDebug(long callId, @NonNull String message, Object... arguments) {
		logger.debug("[cid=" + callId + "]: " + message, arguments);
	}

	private static void logCallInfo(long callId, String message) {
		logger.info("[cid={}]: {}", callId, message);
	}

	private static void logCallInfo(long callId, @NonNull String message, Object... arguments) {
		logger.info("[cid=" + callId + "]: " + message, arguments);
	}

	private static void logCallWarning(long callId, String message) {
		logger.warn("[cid={}]: {}", callId, message);
	}

	private static void logCallWarning(long callId, @NonNull String message, Object... arguments) {
		logger.warn("[cid=" + callId + "]: " + message, arguments);
	}

	private static void logCallError(long callId, String message) {
		logger.error("[cid={}]: {}", callId, message);
	}

	private static void logCallError(long callId, String message, Throwable t) {
		logger.error("[cid=" + callId + "]: " + message, t);
	}

	private static void logCallError(long callId, @NonNull String message, Object... arguments) {
		logger.error("[cid=" + callId + "]: " + message, arguments);
	}

	//endregion

	//region State transitions

	/**
	 * Get the current call state as an immutable snapshot.
	 *
	 * Note: Does not require locking, since the {@link CallState}
	 * class is thread safe.
	 */
	public CallStateSnapshot getCallState() {
		return this.callState.getStateSnapshot();
	}

	/**
	 * Called for every state transition.
	 *
	 * Note: Most reactions to state changes should be done in the `setStateXXX` methods.
	 *       This method should only be used for actions that apply to multiple state transitions.
	 *
	 * @param oldState The previous call state.
	 * @param newState The new call state.
	 */
	private void onStateChange(
		@NonNull CallStateSnapshot oldState,
		@NonNull CallStateSnapshot newState
	) {
		logger.info("Call state change from {} to {}", oldState.getName(), newState.getName());
		logger.debug(
			"  State{{},id={},counter={}} → State{{},id={},counter={}}",
			oldState.getName(), oldState.getCallId(), oldState.getIncomingCallCounter(),
			newState.getName(), newState.getCallId(), newState.getIncomingCallCounter()
		);

		// As soon as the callers state changes from initializing to another state, the callee is
		// not ringing anymore
		if (oldState.isInitializing()) {
			isPeerRinging = false;
		}

		// Clear pending accept intent
		if (!newState.isRinging()) {
			this.acceptIntent = null;
			this.stopRingtone();
		}

		// Ensure bluetooth media button receiver is registered when a call starts
		if (newState.isRinging() || newState.isInitializing()) {
			audioManager.registerMediaButtonEventReceiver(new ComponentName(appContext, VoipMediaButtonReceiver.class));
		}

		// Ensure bluetooth media button receiver is deregistered when a call ends
		if (newState.isDisconnecting() || newState.isIdle()) {
			audioManager.unregisterMediaButtonEventReceiver(new ComponentName(appContext, VoipMediaButtonReceiver.class));
		}

		long callId = oldState.getCallId();
		if (callId != 0L) {
			recentCallIds.add(callId);
		}

		// Enable rejecting calls after a timeout
		enableTimeoutReject();
	}

	/**
	 * Set the current call state to RINGING.
	 */
	public synchronized void setStateRinging(long callId) {
		if (this.callState.isRinging()) {
			return;
		}

		this.ringtoneAudioFocusAbandoned = new CompletableFuture<>();

		// Transition call state
		final CallStateSnapshot prevState = this.callState.getStateSnapshot();
		this.callState.setRinging(callId);
		this.onStateChange(prevState, this.callState.getStateSnapshot());
	}

	/**
	 * Set the current call state to INITIALIZING.
	 */
	public synchronized void setStateInitializing(long callId) {
		if (this.callState.isInitializing()) {
			return;
		}

		// Transition call state
		final CallStateSnapshot prevState = this.callState.getStateSnapshot();
		this.callState.setInitializing(callId);
		this.onStateChange(prevState, this.callState.getStateSnapshot());

		// Make sure connection is open
		if (!this.connectionAcquired) {
			this.lifetimeService.acquireUnpauseableConnection(LIFETIME_SERVICE_TAG);
			this.connectionAcquired = true;
		}

		// Send cached candidates and clear cache
		synchronized (this.candidatesCache) {
			logCallInfo(callId, "Processing cached candidates for {} ID(s)", this.candidatesCache.size());

			// Note: We're sending all cached candidates. The broadcast receiver
			// is responsible for dropping the ones that aren't of interest.
			for (Map.Entry<String, List<VoipICECandidatesData>> entry : this.candidatesCache.entrySet()) {
				logCallInfo(
					callId,
					"Broadcasting {} candidates data messages from {}",
					entry.getValue().size(), entry.getKey()
				);
				for (VoipICECandidatesData data : entry.getValue()) {
					// Broadcast candidates
					Intent intent = new Intent();
					intent.setAction(ACTION_ICE_CANDIDATES);
					intent.putExtra(EXTRA_CALL_ID, data.getCallIdOrDefault(0L));
					intent.putExtra(EXTRA_CONTACT_IDENTITY, entry.getKey());
					intent.putExtra(EXTRA_CANDIDATES, data);
					LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
				}
			}
			this.clearCandidatesCache();
		}
	}

	/**
	 * Set the current call state to CALLING.
	 */
	public synchronized void setStateCalling(long callId) {
		if (this.callState.isCalling()) {
			return;
		}

		// Transition call state
		final CallStateSnapshot prevState = this.callState.getStateSnapshot();
		this.callState.setCalling(callId);
		this.onStateChange(prevState, this.callState.getStateSnapshot());

		// Record the start timestamp of the call.
		// The SystemClock.elapsedRealtime function (returning milliseconds)
		// is guaranteed to be monotonic.
		this.callStartTimestamp = SystemClock.elapsedRealtime();
	}

	/**
	 * Set the current call state to DISCONNECTING.
	 */
	public synchronized void setStateDisconnecting(long callId) {
		if (this.callState.isDisconnecting()) {
			return;
		}

		// Transition call state
		final CallStateSnapshot prevState = this.callState.getStateSnapshot();
		this.callState.setDisconnecting(callId);
		this.onStateChange(prevState, this.callState.getStateSnapshot());

		// Reset start timestamp
		this.callStartTimestamp = null;

		// Clear the candidates cache
		this.clearCandidatesCache();
	}

	/**
	 * Set the current call state to IDLE.
	 */
	public synchronized void setStateIdle() {
		if (this.callState.isIdle()) {
			return;
		}

		// Transition call state
		final CallStateSnapshot prevState = this.callState.getStateSnapshot();
		this.callState.setIdle();
		this.onStateChange(prevState, this.callState.getStateSnapshot());

		// Reset start timestamp
		this.callStartTimestamp = null;

		// Reset initiator flag
		this.initiator = null;

		// Remove offer data
		long callId = prevState.getCallId();
		logger.debug("Removing information for call {} from offerMap", callId);
		this.offerMap.remove(callId);

		// Release Threema connection
		if (this.connectionAcquired) {
			this.lifetimeService.releaseConnectionLinger(LIFETIME_SERVICE_TAG, VOIP_CONNECTION_LINGER);
			this.connectionAcquired = false;
		}
	}

	/**
	 * Set the current state of the peer device regarding ringing.
	 *
	 * @param isPeerRinging the current peer ringing state
	 */
	public void setPeerRinging(boolean isPeerRinging) {
		this.isPeerRinging = isPeerRinging;
	}

	/**
	 * Check whether the peer device is currently ringing. This function returns {@code true} from
	 * the time the other device rings until the call state changes on this device.
	 *
	 * @return {@code true} if the other device is ringing, {@code false} otherwise
	 */
	public boolean isPeerRinging() {
		return this.isPeerRinging;
	}

	//endregion

	/**
	 * Return whether the VoIP service is currently initialized as initiator or responder.
	 *
	 * Note: This is only initialized once a call is being set up. That means that the flag
	 * will be `null` when a call is ringing, but hasn't been accepted yet.
	 */
	@Nullable
	public Boolean isInitiator() {
		return this.initiator;
	}

	/**
	 * Return whether the VoIP service is currently initialized as initiator or responder.
	 */
	public void setInitiator(boolean isInitiator) {
		this.initiator = isInitiator;
	}

	/**
	 * Create a new accept intent for the specified call ID / identity.
	 */
	public static Intent createAcceptIntent(long callId, @NonNull String identity) {
		final Intent intent = new Intent(getAppContext(), CallActivity.class);
		intent.putExtra(EXTRA_CALL_ID, callId);
		intent.putExtra(EXTRA_CONTACT_IDENTITY, identity);
		intent.putExtra(EXTRA_IS_INITIATOR, false);
		intent.putExtra(EXTRA_ACCEPT_INCOMING_CALL, true);
		intent.putExtra(EXTRA_ACTIVITY_MODE, CallActivity.MODE_ACTIVE_CALL);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return intent;
	}

	/**
	 * Create a new reject intent for the specified call ID / identity.
	 */
	private static Intent createRejectIntent(long callId, @NonNull String identity) {
		final Intent intent = new Intent(getAppContext(), CallRejectService.class);
		intent.putExtra(EXTRA_CALL_ID, callId);
		intent.putExtra(EXTRA_CONTACT_IDENTITY, identity);
		intent.putExtra(EXTRA_IS_INITIATOR, false);
		intent.putExtra(CallRejectService.EXTRA_REJECT_REASON, VoipCallAnswerData.RejectReason.REJECTED);
		return intent;
	}

	/**
	 * Creates a reject work request builder.
	 *
	 * @param callId the call id of the call to be rejected
	 * @param identity the contact identity of the call partner
	 * @param rejectReason the reject reason
	 * @return a work request builder
	 */
	private static OneTimeWorkRequest.Builder createRejectWorkRequestBuilder(long callId, @NonNull String identity, byte rejectReason) {
		return new OneTimeWorkRequest.Builder(RejectIntentServiceWorker.class)
			.setInputData(new Data.Builder()
				.putLong(KEY_CALL_ID, callId)
				.putString(KEY_CONTACT_IDENTITY, identity)
				.putByte(KEY_REJECT_REASON, rejectReason
				).build()
			);
	}

	/**
	 * Validate offer data, return true if it's valid.
	 */
	private boolean validateOfferData(@Nullable VoipCallOfferData.OfferData offer) {
		if (offer == null) {
			logger.error("Offer data is null");
			return false;
		}
		final String sdpType = offer.getSdpType();
		if (!sdpType.equals("offer")) {
			logger.error("Offer data is invalid: Sdp type is {}, not offer", sdpType);
			return false;
		}
		final String sdp = offer.getSdp();
		if (sdp == null) {
			logger.error("Offer data is invalid: Sdp is null");
			return false;
		}
		return true;
	}

	/**
	 * Return the {@link VoipCallOfferData} associated with this Call ID (if any).
	 */
	public @Nullable VoipCallOfferData getCallOffer(long callId) {
		return this.offerMap.get(callId);
	}

	//region Handle call messages

	/**
	 * Handle an incoming VoipCallOfferMessage.
	 * @return true if messages was successfully processed
	 */
	@WorkerThread
	public synchronized boolean handleCallOffer(final VoipCallOfferMessage msg) {
		// Unwrap data
		final VoipCallOfferData callOfferData = msg.getData();
		if (callOfferData == null) {
			logger.warn("Call offer received from {}. Data is null, ignoring.", msg.getFromIdentity());
			return true;
		}
		final long callId = callOfferData.getCallIdOrDefault(0L);
		logCallInfo(
			callId,
			"Call offer received from {} (Features: {})",
			msg.getFromIdentity(), callOfferData.getFeatures()
		);
		logCallInfo(callId, "{}", callOfferData.getOfferData());

		// Get contact and receiver
		final ContactModel contact = this.contactService.getByIdentity(msg.getFromIdentity());
		if (contact == null) {
			logCallError(callId, "Could not fetch contact for identity {}", msg.getFromIdentity());
			return true;
		}

		// Handle some reasons for rejecting calls...
		Byte rejectReason = null; // Set to non-null in order to reject the call
		boolean silentReject = false; // Set to true if you don't want a "missed call" chat message
		if (!ConfigUtils.isCallsEnabled()) {
			// Calls disabled
			logCallInfo(callId, "Rejecting call from {} (disabled)", contact.getIdentity());
			rejectReason = VoipCallAnswerData.RejectReason.DISABLED;
			silentReject = true;
		} else if (!this.validateOfferData(callOfferData.getOfferData())) {
			// Offer invalid
			logCallWarning(callId, "Rejecting call from {} (invalid offer)", contact.getIdentity());
			rejectReason = VoipCallAnswerData.RejectReason.UNKNOWN;
			silentReject = true;
		} else if (!this.callState.isIdle()) {
			// Another call is already active
			logCallInfo(callId, "Rejecting call from {} (busy)", contact.getIdentity());
			rejectReason = VoipCallAnswerData.RejectReason.BUSY;
		} else if (VoipUtil.isPSTNCallOngoing(this.appContext)) {
			// A PSTN call is ongoing
			logCallInfo(callId, "Rejecting call from {} (PSTN call ongoing)", contact.getIdentity());
			rejectReason = VoipCallAnswerData.RejectReason.BUSY;
		} else if (DNDUtil.getInstance().isMutedWork()) {
			// Called outside working hours
			logCallInfo(callId, "Rejecting call from {} (called outside of working hours)", contact.getIdentity());
			rejectReason = VoipCallAnswerData.RejectReason.OFF_HOURS;
		} else if (ConfigUtils.hasInvalidCredentials()) {
			logCallInfo(callId, "Rejecting call from {} (credentials have been revoked)", contact.getIdentity());
			rejectReason = VoipCallAnswerData.RejectReason.UNKNOWN;
		}

		if (rejectReason != null) {
			try {
				this.sendRejectCallAnswerMessage(contact, callId, rejectReason, !silentReject);
			} catch (ThreemaException e) {
				logger.error(callId + ": Could not send reject call message", e);
			}
			return true;
		}

		// Prefetch TURN servers
		Config.getTurnServerCache().prefetchTurnServers();

		// Reset fetch cache
		ch.threema.app.routines.UpdateFeatureLevelRoutine.removeTimeCache(contact);

		// Store offer in offer map
		logger.debug("Adding information for call {} to offerMap", callId);
		this.offerMap.put(callId, callOfferData);

		// If the call is accepted, let VoipCallService know
		// and set flag to cancel on watch to true as this call flow is initiated and handled from the Phone
		final Intent answerIntent = createAcceptIntent(callId, msg.getFromIdentity());
		Bundle bundle = new Bundle();
		bundle.putBoolean(EXTRA_CANCEL_WEAR, true);
		answerIntent.putExtras(bundle);
		final PendingIntent accept = PendingIntent.getActivity(
			this.appContext,
			-IdUtil.getTempId(contact),
			answerIntent,
			PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT | PENDING_INTENT_FLAG_IMMUTABLE
		);
		this.acceptIntent = accept;

		// If the call is rejected, start the CallRejectService
		final Intent rejectIntent = createRejectIntent(
			callId,
			msg.getFromIdentity()
		);

		final PendingIntent reject = PendingIntent.getService(
			this.appContext,
			-IdUtil.getTempId(contact),
			rejectIntent,
			PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT | PENDING_INTENT_FLAG_IMMUTABLE);

		final ContactMessageReceiver messageReceiver = this.contactService.createReceiver(contact);

		boolean isMuted = DNDUtil.getInstance().isMutedPrivate(messageReceiver, null);

		// Set state to RINGING
		this.setStateRinging(callId);

		// Show call notification
		final Notification notification = this.showNotification(contact, accept, reject, msg, isMuted);

		// Play ringtone
		this.playRingtone(notification, messageReceiver, isMuted);

		// Send "ringing" message to caller
		try {
			this.sendCallRingingMessage(contact, callId);
		} catch (ThreemaException e) {
			logger.error(callId + ": Could not send ringing message", e);
		}

		// Reject the call after a while
		OneTimeWorkRequest rejectWork = createRejectWorkRequestBuilder(callId, msg.getFromIdentity(), VoipCallAnswerData.RejectReason.TIMEOUT)
			.setInitialDelay(RINGING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.build();
		WorkManager.getInstance(appContext).enqueue(rejectWork);

		// Notify listeners
		VoipListenerManager.messageListener.handle(listener -> {
			final String identity = msg.getFromIdentity();
			if (listener.handle(identity)) {
				listener.onOffer(identity, msg.getData());
			}
		});
		VoipListenerManager.callEventListener.handle(listener -> listener.onRinging(msg.getFromIdentity()));

		return true;
	}

	/**
	 * Handle an incoming VoipCallAnswerMessage.
	 * @return true if messages was successfully processed
	 */
	@WorkerThread
	public synchronized boolean handleCallAnswer(final VoipCallAnswerMessage msg) {
		final VoipCallAnswerData callAnswerData = msg.getData();
		if (callAnswerData != null) {
			// Validate Call ID
			final long callId = callAnswerData.getCallIdOrDefault(0L);
			if (!this.isCallIdValid(callId)) {
				logger.info(
					"Call answer received for an invalid call ID ({}, local={}), ignoring",
					callId, this.callState.getCallId()
				);
				return true;
			}

			// Ensure that an answer wasn't already received
			if (this.callState.answerReceived()) {
				logCallWarning(callId, "Received extra answer, ignoring");
				return true;
			}

			// Ensure that action was set
			if (callAnswerData.getAction() == null) {
			    logCallWarning(callId, "Call answer received without action, ignoring");
			    return true;
			}

			switch (callAnswerData.getAction()) {
				// Call was accepted
				case VoipCallAnswerData.Action.ACCEPT:
					logCallInfo(callId, "Call answer received from {}: accept", msg.getFromIdentity());
					logCallInfo(callId, "Answer features: {}", callAnswerData.getFeatures());
					logCallInfo(callId, "Answer data: {}", callAnswerData.getAnswerData());
					VoipUtil.sendVoipBroadcast(this.appContext, CallActivity.ACTION_CALL_ACCEPTED);
					break;

				// Call was rejected
				case VoipCallAnswerData.Action.REJECT:
					// TODO: only for tests!
					VoipListenerManager.callEventListener.handle(listener -> {
						listener.onRejected(callId, msg.getFromIdentity(), false, callAnswerData.getRejectReason());
					});
					logCallInfo(callId, "Call answer received from {}: reject/{}",
						msg.getFromIdentity(), callAnswerData.getRejectReasonName());
					break;

				default:
					logCallInfo(callId, "Call answer received from {}: Unknown action: {}", callAnswerData.getAction());
					break;
			}

			// Mark answer as received
			this.callState.setAnswerReceived();

			// Notify listeners
			VoipListenerManager.messageListener.handle(listener -> {
				final String identity = msg.getFromIdentity();
				if (listener.handle(identity)) {
					listener.onAnswer(identity, callAnswerData);
				}
			});
		}

		return true;
	}

	/**
	 * Handle an incoming VoipICECandidatesMessage.
	 * @return true if messages was successfully processed
	 */
	@WorkerThread
	public synchronized boolean handleICECandidates(final VoipICECandidatesMessage msg) {
		// Unwrap data
		final VoipICECandidatesData candidatesData = msg.getData();
		if (candidatesData == null) {
			logger.warn("Call ICE candidate message received from {}. Data is null, ignoring", msg.getFromIdentity());
			return true;
		}
		if (candidatesData.getCandidates() == null) {
			logger.warn("Call ICE candidate message received from {}. Candidates are null, ignoring", msg.getFromIdentity());
			return true;
		}

		// Validate Call ID
		final long callId = candidatesData.getCallIdOrDefault(0L);
		if (!this.isCallIdValid(callId)) {
			logger.info(
				"Call ICE candidate message received from {} for an invalid Call ID ({}, local={}), ignoring",
				msg.getFromIdentity(), callId, this.callState.getCallId()
			);
			return true;
		}

		// The "removed" flag is deprecated, see ANDR-1145 / SE-66
		if (candidatesData.isRemoved()) {
			logCallInfo(callId, "Call ICE candidate message received from {} with removed=true, ignoring");
			return true;
		}

		logCallInfo(
			callId,
			"Call ICE candidate message received from {} ({} candidates)",
			msg.getFromIdentity(), candidatesData.getCandidates().length
		);
		for (VoipICECandidatesData.Candidate candidate : candidatesData.getCandidates()) {
			logCallInfo(callId, "  Incoming ICE candidate: {}", candidate.getCandidate());
		}

		// Handle candidates depending on state
		if (this.callState.isIdle() || this.callState.isRinging()) {
			// If the call hasn't been started yet, cache the candidate(s)
			this.cacheCandidate(msg.getFromIdentity(), candidatesData);
		} else if (this.callState.isInitializing() || this.callState.isCalling()) {
			// Otherwise, send candidate(s) directly to call service via broadcast
			Intent intent = new Intent();
			intent.setAction(ACTION_ICE_CANDIDATES);
			intent.putExtra(EXTRA_CALL_ID, msg.getData().getCallIdOrDefault(0L));
			intent.putExtra(EXTRA_CONTACT_IDENTITY, msg.getFromIdentity());
			intent.putExtra(EXTRA_CANDIDATES, candidatesData);
			LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
		} else {
			logCallWarning(callId, "Received ICE candidates in invalid call state ({})", this.callState);
		}

		// Otherwise, ignore message.

		return true;
	}

	/**
	 * Handle incoming Call Ringing message
	 * @return true if message was successfully processed
	 */
	@WorkerThread
	public synchronized boolean handleCallRinging(final VoipCallRingingMessage msg) {
		final CallStateSnapshot state = this.callState.getStateSnapshot();

		// Validate Call ID
		//
		// NOTE: Ringing messages from older Threema versions may not have any associated data!
		final long callId = msg.getData() == null
			? 0L
			: msg.getData().getCallIdOrDefault(0L);
		if (!this.isCallIdValid(callId)) {
			logger.info(
				"Call ringing message received from {} for an invalid Call ID ({}, local={}), ignoring",
				msg.getFromIdentity(), callId, state.getCallId()
			);
			return true;
		}

		logCallInfo(callId, "Call ringing message received from {}", msg.getFromIdentity());

		// Check whether we're in the correct state for a ringing message
		if (!state.isInitializing()) {
			logCallWarning(
				callId,
				"Call ringing message from {} ignored, call state is {}",
				msg.getFromIdentity(), state.getName()
			);
			return true;
		}

		// Notify listeners
		VoipListenerManager.messageListener.handle(listener -> {
			final String identity = msg.getFromIdentity();
			if (listener.handle(identity)) {
				listener.onRinging(identity, msg.getData());
			}
		});

		return true;
	}

	/**
	 * Handle remote call hangup messages.
	 * A hangup can happen either before or during a call.
	 * @return true if message was successfully processed
	 */
	@WorkerThread
	public synchronized boolean handleRemoteCallHangup(final VoipCallHangupMessage msg) {
		// Validate Call ID
		//
		// NOTE: Hangup messages from older Threema versions may not have any associated data!
		// NOTE: If a remote hangup message arrives with an invalid call id that does not appear
		// in the call history, it is a missed call
		final long callId = msg.getData() == null
			? 0L
			: msg.getData().getCallIdOrDefault(0L);
		if (!this.isCallIdValid(callId)) {
			if (isMissedCall(msg, callId)) {
				handleMissedCall(msg, callId);
				return true;
			}
			logger.info(
				"Call hangup message received from {} for an invalid Call ID ({}, local={}), ignoring",
				msg.getFromIdentity(), callId, this.callState.getCallId()
			);
			return true;
		}

		logCallInfo(callId, "Call hangup message received from {}", msg.getFromIdentity());

		final String identity = msg.getFromIdentity();

		final CallStateSnapshot prevState = this.callState.getStateSnapshot();
		final Integer duration = getCallDuration();

		// Detect whether this is an incoming or outgoing call.
		//
		// NOTE: When a call hasn't been accepted yet, the `isInitiator` flag is not yet set.
		//       however, in that case we can be sure that it's an incoming call.
		final boolean incoming = this.isInitiator() != Boolean.TRUE;

		// Reset state
		this.setStateIdle();

		// Cancel call notification for that person
		this.cancelCallNotification(msg.getFromIdentity(), CallActivity.ACTION_DISCONNECTED);

		// Notify listeners
		VoipListenerManager.messageListener.handle(listener -> {
			if (listener.handle(identity)) {
				listener.onHangup(identity, msg.getData());
			}
		});
		if (incoming && (prevState.isIdle() || prevState.isRinging() || prevState.isInitializing())) {
			VoipListenerManager.callEventListener.handle(
				listener -> {
					final boolean accepted = prevState.isInitializing();
					listener.onMissed(callId, identity, accepted, msg.getDate());
				}
			);
		} else if (prevState.isCalling() && duration != null) {
			VoipListenerManager.callEventListener.handle(listener -> {
				listener.onFinished(callId, msg.getFromIdentity(), !incoming, duration);
			});
		}

		return true;
	}

	/**
	 * Handle a missed call.
	 *
	 * @param msg the hangup message of the missed call
	 * @param callId the call id of the missed call
	 */
	private void handleMissedCall(@NonNull final VoipCallHangupMessage msg, final long callId) {
		logger.info("Missed call received from {} with call id {}", msg.getFromIdentity(), callId);
		VoipListenerManager.callEventListener.handle(
			listener -> listener.onMissed(callId, msg.getFromIdentity(), false, msg.getDate())
		);
	}

	//endregion

	/**
	 * Return whether the specified call ID belongs to the current call.
	 *
	 * NOTE: Do not use this method to validate the call ID in an offer,
	 *       that doesn't make sense :)
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	private synchronized boolean isCallIdValid(long callId) {
		// If the passed in Call ID matches the current Call ID, everything is fine
		final long currentCallId = this.callState.getCallId();
		if (callId == currentCallId) {
			return true;
		}

		// ANDR-1140: If we are the initiator, then we will have initialized the call ID to a
		// random value. If however the remote device does not yet support call IDs, then returned
		// messages will not contain a Call ID. Accept the messages anyways.
		final boolean isInitiatior = this.isInitiator() == Boolean.TRUE;
		if (isInitiatior && callId == 0L) {
			return true;
		}

		// Otherwise, there's a call ID mismatch.
		return false;
	}

	/**
	 * Check whether this hangup voip message is a missed call.
	 *
	 * @param msg the received voip message that is checked for a missed call
	 * @param callId the call id
	 * @return {@code true} if it is a missed call, {@code false} otherwise
	 */
	public boolean isMissedCall(VoipMessage msg, long callId) {
		if (recentCallIds.contains(callId)) {
			logger.info("No missed call: call id {} is contained in recent call ids", callId);
			return false;
		}
		// Limit the check to the last 4 calls. Note that only call status messages with the
		// contact of this hangup message are considered.
		if (contactService.createReceiver(contactService.getByIdentity(msg.getFromIdentity())).hasVoipCallStatus(callId, 4)) {
			logger.info("No missed call: call id {} found in database", callId);
			return false;
		}

		return true;
	}


	/**
	 * Send a call offer to the specified contact.
	 *
	 * @param videoCall Whether to enable video calls in this offer.
	 * @throws ThreemaException if enqueuing the message fails.
	 * @throws IllegalArgumentException if the session description is not valid for an offer message.
	 * @throws IllegalStateException if the call state is not INITIALIZING
	 */
	public synchronized void sendCallOfferMessage(
		@NonNull ContactModel receiver,
		final long callId,
		@NonNull SessionDescription sessionDescription,
		boolean videoCall
	) throws ThreemaException, IllegalArgumentException, IllegalStateException {
		switch (sessionDescription.type) {
			case OFFER:
				// OK
				break;
			case ANSWER:
			case PRANSWER:
				throw new IllegalArgumentException("A " + sessionDescription.type +
						" session description is not valid for an offer message");
		}

		final CallStateSnapshot state = this.callState.getStateSnapshot();
		if (!state.isInitializing()) {
			throw new IllegalStateException("Called sendCallOfferMessage in state " + state.getName());
		}

		final VoipCallOfferData callOfferData = new VoipCallOfferData()
			.setCallId(callId)
			.setOfferData(
				new VoipCallOfferData.OfferData()
					.setSdpType(sessionDescription.type.canonicalForm())
					.setSdp(sessionDescription.description)
			);
		if (videoCall) {
			callOfferData.addFeature(new VideoFeature());
		}

		final VoipCallOfferMessage voipCallOfferMessage = new VoipCallOfferMessage();
		voipCallOfferMessage.setData(callOfferData);
		voipCallOfferMessage.setToIdentity(receiver.getIdentity());

		this.messageQueue.enqueue(voipCallOfferMessage);
		logCallInfo(callId, "Call offer enqueued to {}", voipCallOfferMessage.getToIdentity());
		logCallInfo(callId, "  Offer features: {}", callOfferData.getFeatures());
		logCallInfo(callId, "  Offer data: {}", callOfferData.getOfferData());
		this.messageService.sendProfilePicture(new MessageReceiver[] {contactService.createReceiver(receiver)});
	}

	//region Send call messages

	/**
	 * Accept a call from the specified contact.
	 * @throws ThreemaException if enqueuing the message fails.
	 * @throws IllegalArgumentException if the session description is not valid for an offer message.
	 */
	public void sendAcceptCallAnswerMessage(
		@NonNull ContactModel receiver,
		final long callId,
		@NonNull SessionDescription sessionDescription,
		boolean videoCall
	) throws ThreemaException, IllegalArgumentException {
		this.sendCallAnswerMessage(
			receiver,
			callId,
			sessionDescription,
			VoipCallAnswerData.Action.ACCEPT,
			null,
			videoCall
		);
	}

	/**
	 * Reject a call from the specified contact.
	 * @throws ThreemaException if enqueuing the message fails.
	 */
	public void sendRejectCallAnswerMessage(
		final @NonNull ContactModel receiver,
		final long callId,
		byte reason
	) throws ThreemaException, IllegalArgumentException {
		this.sendRejectCallAnswerMessage(receiver, callId, reason, true);
	}

	/**
	 * Reject a call from the specified contact.
	 * @throws ThreemaException if enqueuing the message fails.
	 */
	public void sendRejectCallAnswerMessage(
		final @NonNull ContactModel receiver,
		final long callId,
		byte reason,
		boolean notifyListeners
	) throws ThreemaException, IllegalArgumentException {
		logCallInfo(callId, "Sending reject call answer message (reason={})", reason);
		this.sendCallAnswerMessage(receiver, callId, null, VoipCallAnswerData.Action.REJECT, reason, null);

		// Notify listeners
		if (notifyListeners) {
			logCallInfo(callId, "Notifying listeners about call rejection");
			VoipListenerManager.callEventListener.handle(listener -> {
				switch (reason) {
					case VoipCallAnswerData.RejectReason.BUSY:
					case VoipCallAnswerData.RejectReason.TIMEOUT:
					case VoipCallAnswerData.RejectReason.OFF_HOURS:
						listener.onMissed(callId, receiver.getIdentity(), false, null);
						break;
					default:
						listener.onRejected(callId, receiver.getIdentity(), true, reason);
						break;
				}
			});
		}
	}

	/**
	 * Send a call answer method.
	 *
	 * @param videoCall If set to TRUE, then the `video` call feature
	 *     will be sent along in the answer.
	 * @throws ThreemaException
	 * @throws IllegalArgumentException
	 * @throws IllegalStateException
	 */
	private void sendCallAnswerMessage(
		@NonNull ContactModel receiver,
		final long callId,
		@Nullable SessionDescription sessionDescription,
	    byte action,
	    @Nullable Byte rejectReason,
		@Nullable Boolean videoCall
	) throws ThreemaException, IllegalArgumentException, IllegalStateException {
		logCallInfo(callId, "Sending call answer message");
		final VoipCallAnswerData callAnswerData = new VoipCallAnswerData()
			.setCallId(callId)
			.setAction(action);

		if (action == VoipCallAnswerData.Action.ACCEPT && sessionDescription != null) {
			switch (sessionDescription.type) {
				case ANSWER:
				case PRANSWER:
					// OK
					break;
				case OFFER:
					throw new IllegalArgumentException("A " + sessionDescription.type +
							" session description is not valid for an answer message");
			}

			callAnswerData.setAnswerData(
				new VoipCallAnswerData.AnswerData()
					.setSdpType(sessionDescription.type.canonicalForm())
					.setSdp(sessionDescription.description)
			);

			if (Boolean.TRUE.equals(videoCall)) {
				callAnswerData.addFeature(new VideoFeature());
			}
		} else if (action == VoipCallAnswerData.Action.REJECT && rejectReason != null) {
			callAnswerData.setRejectReason(rejectReason);
		} else {
			throw new IllegalArgumentException("Invalid action, missing session description or missing reject reason");
		}

		final VoipCallAnswerMessage voipCallAnswerMessage = new VoipCallAnswerMessage();
		voipCallAnswerMessage.setData(callAnswerData);
		voipCallAnswerMessage.setToIdentity(receiver.getIdentity());

		logCallInfo(callId, "Call answer enqueued to {}: {}", voipCallAnswerMessage.getToIdentity(), callAnswerData.getAction());
		logCallInfo(callId, "  Answer features: {}", callAnswerData.getFeatures());
		messageQueue.enqueue(voipCallAnswerMessage);
		this.messageService.sendProfilePicture(new MessageReceiver[] {contactService.createReceiver(receiver)});
	}

	/**
	 * Send ice candidates to the specified contact.
	 * @throws ThreemaException if enqueuing the message fails.
	 */
	synchronized void sendICECandidatesMessage(
		@NonNull ContactModel receiver,
		final long callId,
		@NonNull IceCandidate[] iceCandidates
	) throws ThreemaException {
		final CallStateSnapshot state = this.callState.getStateSnapshot();
		if (!(state.isRinging() || state.isInitializing() || state.isCalling())) {
			logger.warn("Called sendICECandidatesMessage in state {}, ignoring", state.getName());
			return;
		}

		// Build message
		final List<VoipICECandidatesData.Candidate> candidates = new LinkedList<>();
		for (IceCandidate c : iceCandidates) {
			if (c != null) {
				candidates.add(new VoipICECandidatesData.Candidate(c.sdp, c.sdpMid, c.sdpMLineIndex, null));
			}
		}
		final VoipICECandidatesData voipICECandidatesData = new VoipICECandidatesData()
			.setCallId(callId)
			.setCandidates(candidates.toArray(new VoipICECandidatesData.Candidate[candidates.size()]));
		final VoipICECandidatesMessage voipICECandidatesMessage = new VoipICECandidatesMessage();
		voipICECandidatesMessage.setData(voipICECandidatesData);
		voipICECandidatesMessage.setToIdentity(receiver.getIdentity());

		// Enqueue
		messageQueue.enqueue(voipICECandidatesMessage);

		// Log
		logCallInfo(callId, "Call ICE candidate message enqueued to {}", voipICECandidatesMessage.getToIdentity());
		for (VoipICECandidatesData.Candidate candidate : Objects.requireNonNull(voipICECandidatesData.getCandidates())) {
			logCallInfo(callId, "  Outgoing ICE candidate: {}", candidate.getCandidate());
		}

	}

	/**
	 * Send a ringing message to the specified contact.
	 */
	private synchronized void sendCallRingingMessage(
		@NonNull ContactModel contactModel,
		final long callId
	) throws ThreemaException, IllegalStateException {
		final CallStateSnapshot state = this.callState.getStateSnapshot();
		if (!state.isRinging()) {
			throw new IllegalStateException("Called sendCallRingingMessage in state " + state.getName());
		}

		final VoipCallRingingData callRingingData = new VoipCallRingingData()
			.setCallId(callId);

		final VoipCallRingingMessage msg = new VoipCallRingingMessage();
		msg.setToIdentity(contactModel.getIdentity());
		msg.setData(callRingingData);

		messageQueue.enqueue(msg);
		logCallInfo(callId, "Call ringing message enqueued to {}", msg.getToIdentity());
	}

	/**
	 * Send a hangup message to the specified contact.
	 */
	synchronized void sendCallHangupMessage(
		final @NonNull ContactModel contactModel,
		final long callId
	) throws ThreemaException {
		final CallStateSnapshot state = this.callState.getStateSnapshot();
		final String peerIdentity = contactModel.getIdentity();

		final VoipCallHangupData callHangupData = new VoipCallHangupData()
			.setCallId(callId);

		final VoipCallHangupMessage msg = new VoipCallHangupMessage();
		msg.setData(callHangupData);
		msg.setToIdentity(peerIdentity);

		final Integer duration = getCallDuration();
		final boolean outgoing = this.isInitiator() == Boolean.TRUE;

		messageQueue.enqueue(msg);
		logCallInfo(
			callId,
			"Call hangup message enqueued to {} (prevState={}, duration={})",
			msg.getToIdentity(), state, duration
		);

		// Notify the VoIP call event listener
		if (duration == null && (state.isInitializing() || state.isCalling() || state.isDisconnecting())) {
			// Connection was never established
			VoipListenerManager.callEventListener.handle(
				listener -> {
					if (outgoing) {
						listener.onAborted(callId, peerIdentity);
					} else {
						listener.onMissed(callId, peerIdentity, true, null);
					}
				}
			);
		}
		// Note: We don't call listener.onFinished here, that's already being done
		// in VoipCallService#disconnect.
	}

	//endregion

	/**
	 * Accept an incoming call.
	 * @return true if call was accepted, false otherwise (e.g. if no incoming call was active)
	 */
	public boolean acceptIncomingCall() {
		if (this.acceptIntent == null) {
			return false;
		}
		try {
			this.acceptIntent.send();
			this.acceptIntent = null;
			return true;
		} catch (PendingIntent.CanceledException e) {
			logger.error("Cannot send pending accept intent: It was cancelled");
			this.acceptIntent = null;
			return false;
		}
	}

	/**
	 * Clear the canddidates cache for the specified identity.
	 */
	void clearCandidatesCache(String identity) {
		logger.debug("Clearing candidates cache for {}", identity);
		synchronized (this.candidatesCache) {
			this.candidatesCache.remove(identity);
		}
	}

	/**
	 * Clear the candidates cache for all identities.
	 */
	private void clearCandidatesCache() {
		logger.debug("Clearing candidates cache for all identities");
		synchronized (this.candidatesCache) {
			this.candidatesCache.clear();
		}
	}

	/**
	 * Mute ringtone if call is in ringing state
	 */
	public boolean muteRingtone() {
		final CallStateSnapshot currentCallState = this.getCallState();
		final boolean incoming = this.isInitiator() != Boolean.TRUE;

		if (incoming && currentCallState.isRinging()) {
			this.stopRingtone();
			logger.info("Muting ringtone as requested by user");
			return true;
		}
		return false;
	}

	/**
	 * Cancel a pending call notification for the specified identity.
	 *
	 * @param cancelReason Either CallActivity.ACTION_CANCELLED (if a call was cancelled before
	 *                     being established) or CallActivity.ACTION_DISCONNECTED (if a previously
	 *                     established call was disconnected).
	 */
	void cancelCallNotification(@NonNull String identity, @NonNull String cancelReason) {
		// Cancel fullscreen activity launched by notification first
		VoipUtil.sendVoipBroadcast(appContext, cancelReason);
		appContext.stopService(new Intent(ThreemaApplication.getAppContext(), VoipCallService.class));

		this.stopRingtone();

		synchronized (this.callNotificationTags) {
			if (this.callNotificationTags.contains(identity)) {
				logger.info("Cancelling call notification for {}", identity);
				this.notificationManagerCompat.cancel(identity, INCOMING_CALL_NOTIFICATION_ID);
				this.callNotificationTags.remove(identity);
			} else {
				logger.warn("No call notification found for {}, number of tags: {}", identity, this.callNotificationTags.size());
				if (this.callNotificationTags.size() == 0) {
					this.notificationManagerCompat.cancel(identity, INCOMING_CALL_NOTIFICATION_ID);
				}
			}
			if (this.callNotificationTags.size() == 0) {
				unregisterScreenOffReceiver();
			}
		}
	}

	/**
	 * Cancel all pending call notifications.
	 */
	public void cancelCallNotificationsForNewCall() {
		this.stopRingtone();

		synchronized (this.callNotificationTags) {
			logger.info("Cancelling all {} call notifications", this.callNotificationTags.size());
			for (String tag : this.callNotificationTags) {
				this.notificationManagerCompat.cancel(tag, INCOMING_CALL_NOTIFICATION_ID);
			}
			this.callNotificationTags.clear();
		}
		unregisterScreenOffReceiver();
	}

	private void registerScreenOffReceiver() {
		if (screenOffReceiver == null) {
			screenOffReceiver = new ScreenOffReceiver();
			appContext.registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
		}
	}

	private void unregisterScreenOffReceiver() {
		if (screenOffReceiver != null) {
			appContext.unregisterReceiver(screenOffReceiver);
			screenOffReceiver = null;
		}
	}

	/**
	 * Return the current call duration in seconds.
	 *
	 * Return null if the call state is not CALLING.
	 */
	@Nullable Integer getCallDuration() {
		final Long start = this.callStartTimestamp;
		if (start == null) {
			return null;
		} else {
			final long seconds = (SystemClock.elapsedRealtime() - start) / 1000;
			if (seconds > Integer.MAX_VALUE) {
				return Integer.MAX_VALUE;
			}
			return (int) seconds;
		}
	}

	/**
	 * Disable automatically rejecting the call after a timeout.
	 */
	public synchronized void disableTimeoutReject() {
		this.timeoutReject.set(false);
	}

	/**
	 * Enable automatically rejecting the call after a timeout.
	 */
	public synchronized void enableTimeoutReject() {
		this.timeoutReject.set(true);
	}

	/**
	 * Return if the call should be auto rejected. Normally every call should be rejected after a
	 * timeout. If the timeout is reached just after the user accepted the call (but the call did
	 * not start yet), then this returns false and the call should not be rejected based on the
	 * timeout.
	 */
	public synchronized boolean isTimeoutReject() {
		return timeoutReject.get();
	}

	// Private helper methods

	/**
	 * Show a call notification.
	 */
	@Nullable
	@WorkerThread
	private Notification showNotification(
		@NonNull ContactModel contact,
		@Nullable PendingIntent accept,
		@NonNull PendingIntent reject,
		final VoipCallOfferMessage msg,
		boolean isMuted
	) {
		final long timestamp = System.currentTimeMillis();
		final Bitmap avatar = this.contactService.getAvatar(contact, false);
		final PendingIntent inCallPendingIntent = createLaunchPendingIntent(contact.getIdentity(), msg);
		Notification notification = null;

		if (notificationManagerCompat.areNotificationsEnabled()) {
			final NotificationCompat.Builder nbuilder = new NotificationBuilderWrapper(this.appContext, NOTIFICATION_CHANNEL_CALL, isMuted);

			// Content
			nbuilder.setContentTitle(appContext.getString(R.string.voip_notification_title))
					.setContentText(appContext.getString(R.string.voip_notification_text, NameUtil.getDisplayNameOrNickname(contact, true)))
					.setOngoing(true)
					.setWhen(timestamp)
					.setAutoCancel(false)
					.setShowWhen(true);

			// We want a full screen notification
			// Set up the main intent to send the user to the incoming call screen
			nbuilder.setFullScreenIntent(inCallPendingIntent, true);
			nbuilder.setContentIntent(inCallPendingIntent);

			// Icons and colors
			nbuilder.setLargeIcon(avatar)
					.setSmallIcon(R.drawable.ic_phone_locked_white_24dp)
					.setColor(this.appContext.getResources().getColor(R.color.accent_light));

			// Alerting
			nbuilder.setPriority(NotificationCompat.PRIORITY_MAX)
					.setCategory(NotificationCompat.CATEGORY_CALL);

			// Privacy
			nbuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
					// TODO
					.setPublicVersion(new NotificationCompat.Builder(appContext, ConfigUtils.supportsNotificationChannels() ? NOTIFICATION_CHANNEL_CALL : null)
							.setContentTitle(appContext.getString(R.string.voip_notification_title))
							.setContentText(appContext.getString(R.string.notification_hidden_text))
							.setSmallIcon(R.drawable.ic_phone_locked_white_24dp)
							.setColor(appContext.getResources().getColor(R.color.accent_light))
							.build());

			// Add identity to notification for DND priority override
			String contactLookupUri = contactService.getAndroidContactLookupUriString(contact);
			if (contactLookupUri != null) {
				nbuilder.addPerson(contactLookupUri);
			}

			if (preferenceService.isVoiceCallVibrate() && !isMuted) {
				nbuilder.setVibrate(VIBRATE_PATTERN_INCOMING_CALL);
			} else if (!ConfigUtils.supportsNotificationChannels()) {
				nbuilder.setVibrate(VIBRATE_PATTERN_SILENT);
			}

			// Actions
			final SpannableString rejectString = new SpannableString(appContext.getString(R.string.voip_reject));
			rejectString.setSpan(new ForegroundColorSpan(Color.RED), 0, rejectString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

			final SpannableString acceptString = new SpannableString(appContext.getString(R.string.voip_accept));
			acceptString.setSpan(new ForegroundColorSpan(Color.GREEN), 0, acceptString.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

			nbuilder.addAction(R.drawable.ic_call_end_grey600_24dp, rejectString, reject)
					.addAction(R.drawable.ic_call_grey600_24dp, acceptString, accept != null ? accept : inCallPendingIntent);

			// Build notification
			notification = nbuilder.build();

			// Set flags
			notification.flags |= NotificationCompat.FLAG_INSISTENT | NotificationCompat.FLAG_NO_CLEAR | NotificationCompat.FLAG_ONGOING_EVENT;

			synchronized (this.callNotificationTags) {
				this.notificationManagerCompat.notify(contact.getIdentity(), INCOMING_CALL_NOTIFICATION_ID, notification);
				this.callNotificationTags.add(contact.getIdentity());
			}
		} else {
			// notifications disabled in system settings - fire inCall pending intent to show CallActivity
			try {
				inCallPendingIntent.send();
			} catch (PendingIntent.CanceledException e) {
				logger.error("Could not send inCallPendingIntent", e);
			}
		}

		// register screen off receiver
		registerScreenOffReceiver();

		return notification;
	}

	private void playRingtone(@Nullable Notification notification, MessageReceiver messageReceiver, boolean isMuted) {
		final Uri ringtoneUri = this.ringtoneService.getVoiceCallRingtone(messageReceiver.getUniqueIdString());

		if (ringtoneUri != null) {
			if (ringtonePlayer != null) {
				stopRingtone();
			}

			boolean isSystemMuted = DNDUtil.getInstance().isSystemMuted(messageReceiver, notification, notificationManager, notificationManagerCompat);

			if (!isMuted && !isSystemMuted ) {
				audioManager.requestAudioFocus(this, AudioManager.STREAM_RING, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
				ringtonePlayer = new MediaPlayerStateWrapper();
				ringtonePlayer.setStateListener(new MediaPlayerStateWrapper.StateListener() {
					@Override
					public void onCompletion(MediaPlayer mp) {
					}

					@Override
					public void onPrepared(MediaPlayer mp) {
						ringtonePlayer.start();
					}
				});
				ringtonePlayer.setLooping(true);
				if (Build.VERSION.SDK_INT <= 21) {
					ringtonePlayer.setAudioStreamType(AudioManager.STREAM_RING);
				} else {
					ringtonePlayer.setAudioAttributes(SoundUtil.getAudioAttributesForCallNotification());
				}

				try {
					ringtonePlayer.setDataSource(appContext, ringtoneUri);
					ringtonePlayer.prepareAsync();
				} catch (Exception e) {
					stopRingtone();
				}
			}
		}
	}

	private synchronized void stopRingtone() {
		if (ringtonePlayer != null) {
			ringtonePlayer.stop();
			ringtonePlayer.reset();
			ringtonePlayer.release();
			ringtonePlayer = null;
		}

		try {
			audioManager.abandonAudioFocus(this);
		} catch (Exception e) {
			logger.info("Failed to abandon audio focus");
		} finally {
			this.ringtoneAudioFocusAbandoned.complete(null);
		}
	}

	private PendingIntent createLaunchPendingIntent(
		@NonNull String identity,
		@Nullable VoipCallOfferMessage msg
	) {
		final Intent intent = new Intent(Intent.ACTION_MAIN, null);
		intent.setClass(appContext, CallActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.setData((Uri.parse("foobar://"+ SystemClock.elapsedRealtime())));
		intent.putExtra(EXTRA_ACTIVITY_MODE, CallActivity.MODE_INCOMING_CALL);
		intent.putExtra(EXTRA_CONTACT_IDENTITY, identity);
		intent.putExtra(EXTRA_IS_INITIATOR, false);
		if (msg != null) {
			final VoipCallOfferData data = msg.getData();
			intent.putExtra(EXTRA_CALL_ID, data.getCallIdOrDefault(0L));
		}

		// PendingIntent that can be used to launch the InCallActivity.  The
		// system fires off this intent if the user pulls down the windowshade
		// and clicks the notification's expanded view.  It's also used to
		// launch the InCallActivity immediately when when there's an incoming
		// call (see the "fullScreenIntent" field below).
		return PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PENDING_INTENT_FLAG_IMMUTABLE);
	}

	/**
	 * Add a new ICE candidate to the cache.
	 */
	private void cacheCandidate(String identity, VoipICECandidatesData data) {
		logCallDebug(data.getCallIdOrDefault(0L), "Caching candidate from {}", identity);
		synchronized (this.candidatesCache) {
			if (this.candidatesCache.containsKey(identity)) {
				List<VoipICECandidatesData> candidates = this.candidatesCache.get(identity);
				candidates.add(data);
			} else {
				List<VoipICECandidatesData> candidates = new LinkedList<>();
				candidates.add(data);
				this.candidatesCache.put(identity, candidates);
			}
		}
	}

	/**
	 * Create a new video context.
	 *
	 * Throws an `IllegalStateException` if a video context already exists.
 	 */
	void createVideoContext() throws IllegalStateException {
		logger.trace("createVideoContext");
		if (this.videoContext != null) {
			throw new IllegalStateException("Video context already exists");
		}
		this.videoContext = new VideoContext();
		this.videoContextFuture.complete(this.videoContext);
	}

	/**
	 * Return a reference to the video context instance.
	 */
	@Nullable
	public VideoContext getVideoContext() {
		return this.videoContext;
	}

	/**
	 * Return a future that resolves with the video context instance.
	 */
	@NonNull
	public CompletableFuture<VideoContext> getVideoContextFuture() {
		return this.videoContextFuture;
	}

	/**
	 * Release resources associated with the video context instance.
	 *
	 * It's safe to call this method multiple times.
	 */
	void releaseVideoContext() {
		if (this.videoContext != null) {
			this.videoContext.release();
			this.videoContext = null;
			this.videoContextFuture = new CompletableFuture<>();
		}
	}

	public int getVideoRenderMode() {
		return videoRenderMode;
	}

	public void setVideoRenderMode(int videoRenderMode) {
		this.videoRenderMode = videoRenderMode;
	}

	@Override
	public void onAudioFocusChange(int focusChange) {
		logger.info("Audio Focus change: " + focusChange);
	}

	public synchronized CompletableFuture<Void> getRingtoneAudioFocusAbandoned() {
		return this.ringtoneAudioFocusAbandoned;
	}
}
