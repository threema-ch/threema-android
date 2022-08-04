/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;

import org.slf4j.Logger;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData;
import ch.threema.storage.models.ContactModel;

import static ch.threema.app.voip.services.VoipCallService.EXTRA_CALL_ID;
import static ch.threema.app.voip.services.VoipCallService.EXTRA_CONTACT_IDENTITY;

/**
 * A small intent service that rejects an incoming call.
 */
public class CallRejectService extends IntentService {
	private static final String name = "CallRejectService";
	private static final Logger logger = LoggingUtil.getThreemaLogger(name);
	private static final int CALL_REJECT_NOTIFICATION_ID = 27349;

	public static final String EXTRA_REJECT_REASON = "REJECT_REASON";

	private VoipStateService voipStateService = null;
	private ContactService contactService = null;

	public CallRejectService() {
		super(name);
	}

	@Override
	protected void onHandleIntent(@Nullable Intent intent) {
		logger.info("onHandleIntent");

		// Ignore null intents
		if (intent == null) {
			logger.debug("Empty Intent");
			return;
		}

		// Intent parameters
		final String contactIdentity = intent.getStringExtra(EXTRA_CONTACT_IDENTITY);
		final long callId = intent.getLongExtra(EXTRA_CALL_ID, 0L);
		final byte rejectReason = intent.getByteExtra(EXTRA_REJECT_REASON, VoipCallAnswerData.RejectReason.UNKNOWN);

		// Set logging prefix
		VoipUtil.setLoggerPrefix(logger, callId);

		// Services
		try {
			final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (this.voipStateService == null) {
				this.voipStateService = serviceManager.getVoipStateService();
			}
			if (this.contactService == null) {
				this.contactService = serviceManager.getContactService();
			}
		} catch (Exception e) {
			logger.error("Could not initialize services", e);
			return;
		}

		// Cancel current notification
		this.voipStateService.cancelCallNotification(contactIdentity, CallActivity.ACTION_CANCELLED);

		// Get contact
		final ContactModel contact = this.contactService.getByIdentity(contactIdentity);
		if (contact == null) {
			logger.error("Could not get contact model for \"{}\"", contactIdentity);
			return;
		}

		try {
			// Reject call
			logger.debug("Rejecting call from {} (reason {})", contactIdentity, rejectReason);
			voipStateService.sendRejectCallAnswerMessage(contact, callId, rejectReason);
		} catch (ThreemaException e) {
			logger.error("Could not send reject answer message", e);
		}

		// Reset state
		this.voipStateService.setStateIdle();

		// Clear the candidates cache
		voipStateService.clearCandidatesCache(contactIdentity);
	}

	@Override
	public void onCreate() {
		logger.info("onCreate");

		super.onCreate();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForeground(CALL_REJECT_NOTIFICATION_ID, getForegroundNotification());
		}
	}

	@Override
	public void onDestroy() {
		logger.info("onDestroy");

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			stopForeground(true);
		}

		super.onDestroy();
	}

	@TargetApi(Build.VERSION_CODES.O)
	private Notification getForegroundNotification() {
		createChannel();
		NotificationCompat.Builder builder =
			(new NotificationCompat.Builder(this.getApplicationContext(), NotificationService.NOTIFICATION_CHANNEL_REJECT_SERVICE))
				.setGroup(NotificationService.NOTIFICATION_CHANNEL_REJECT_SERVICE)
				.setContentTitle(getString(R.string.voip_reject_channel_name))
				.setSmallIcon(R.drawable.ic_call_grey600_24dp)
				.setPriority(NotificationCompat.PRIORITY_MIN)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return builder.build();
	}

	@TargetApi(Build.VERSION_CODES.O)
	private void createChannel() {
		NotificationChannel channel = new NotificationChannel(
			NotificationService.NOTIFICATION_CHANNEL_REJECT_SERVICE,
			getResources().getString(R.string.voip_reject_channel_name),
			NotificationManager.IMPORTANCE_LOW);
		channel.enableVibration(false);
		channel.enableLights(false);
		channel.setShowBadge(false);
		getSystemService(NotificationManager.class).createNotificationChannel(channel);
	}
}
