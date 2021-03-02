/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.RTCStatsCollectorCallback;
import org.webrtc.RTCStatsReport;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSink;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.lifecycle.LifecycleService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import ch.threema.annotation.SameThread;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.notifications.BackgroundErrorNotification;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.ui.SingleToast;
import ch.threema.app.utils.CloseableLock;
import ch.threema.app.utils.CloseableReadWriteLock;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.MediaPlayerStateWrapper;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.RandomUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.CallStateSnapshot;
import ch.threema.app.voip.CpuMonitor;
import ch.threema.app.voip.PeerConnectionClient;
import ch.threema.app.voip.VoipAudioManager;
import ch.threema.app.voip.VoipAudioManager.AudioDevice;
import ch.threema.app.voip.activities.CallActivity;
import ch.threema.app.voip.listeners.VoipAudioManagerListener;
import ch.threema.app.voip.listeners.VoipMessageListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.voip.receivers.IncomingMobileCallReceiver;
import ch.threema.app.voip.receivers.MeteredStatusChangedReceiver;
import ch.threema.app.voip.util.SdpPatcher;
import ch.threema.app.voip.util.SdpUtil;
import ch.threema.app.voip.util.VideoCapturerUtil;
import ch.threema.app.voip.util.VoipStats;
import ch.threema.app.voip.util.VoipUtil;
import ch.threema.app.voip.util.VoipVideoParams;
import ch.threema.base.ThreemaException;
import ch.threema.base.VerificationLevel;
import ch.threema.client.ThreemaFeature;
import ch.threema.client.voip.VoipCallAnswerData;
import ch.threema.client.voip.VoipCallHangupData;
import ch.threema.client.voip.VoipCallOfferData;
import ch.threema.client.voip.VoipCallRingingData;
import ch.threema.client.voip.VoipICECandidatesData;
import ch.threema.client.voip.features.FeatureList;
import ch.threema.client.voip.features.VideoFeature;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.protobuf.callsignaling.CallSignaling;
import ch.threema.storage.models.ContactModel;
import java8.util.function.Supplier;
import java8.util.stream.StreamSupport;

import static ch.threema.app.ThreemaApplication.getAppContext;
import static ch.threema.app.ThreemaApplication.getServiceManager;
import static ch.threema.app.voip.services.VideoContext.CAMERA_BACK;
import static ch.threema.app.voip.services.VideoContext.CAMERA_FRONT;
import static ch.threema.app.voip.services.VoipStateService.VIDEO_RENDER_FLAG_NONE;

/**
 * The service keeping track of VoIP call state and the corresponding WebRTC peer connection.
 */
public class VoipCallService extends LifecycleService implements PeerConnectionClient.Events {
	private static final Logger logger = LoggerFactory.getLogger(VoipCallService.class);

	// Intent extras
	public static final String EXTRA_CALL_ID = "CALL_ID";
	public static final String EXTRA_CONTACT_IDENTITY = "CONTACT_IDENTITY";
	public static final String EXTRA_IS_INITIATOR = "IS_INITIATOR";
	public static final String EXTRA_ACTIVITY_MODE = "ACTIVITY_MODE";
	public static final String EXTRA_CANDIDATES = "CANDIDATES";
	public static final String EXTRA_AUDIO_DEVICE = "AUDIO_DEVICE";
	public static final String EXTRA_START_TIME = "START_TIME";
	public static final String EXTRA_LAUNCH_VIDEO = "LAUNCH_VIDEO";
	public static final String EXTRA_CANCEL_WEAR = "CANCEL_ACTIVITY_ON_WATCH";

	// Broadcast actions
	public static final String ACTION_HANGUP = BuildConfig.APPLICATION_ID + ".HANGUP";
	public static final String ACTION_ICE_CANDIDATES = BuildConfig.APPLICATION_ID + ".ICE_CANDIDATES";
	public static final String ACTION_MUTE_TOGGLE = BuildConfig.APPLICATION_ID + ".MUTE_TOGGLE";
	public static final String ACTION_SET_AUDIO_DEVICE = BuildConfig.APPLICATION_ID + ".SET_AUDIO_DEVICE";
	public static final String ACTION_QUERY_AUDIO_DEVICES = BuildConfig.APPLICATION_ID + ".QUERY_AUDIO_DEVICES";
	public static final String ACTION_QUERY_MIC_ENABLED = BuildConfig.APPLICATION_ID + ".QUERY_MIC_ENABLED";
	public static final String ACTION_ENABLE_DEBUG_INFO = BuildConfig.APPLICATION_ID + ".ENABLE_DEBUG_INFO";
	public static final String ACTION_DISABLE_DEBUG_INFO = BuildConfig.APPLICATION_ID + ".DISABLE_DEBUG_INFO";
	public static final String ACTION_START_CAPTURING = BuildConfig.APPLICATION_ID + ".START_CAPTURING";
	public static final String ACTION_STOP_CAPTURING = BuildConfig.APPLICATION_ID + ".STOP_CAPTURING";
	public static final String ACTION_SWITCH_CAMERA = BuildConfig.APPLICATION_ID + ".SWITCH_CAMERA";

	// Notification IDs
	private static final int INCALL_NOTIFICATION_ID = 41991;

	// Peer connection
	@Nullable private PeerConnectionClient peerConnectionClient = null;

	// Audio
	@Nullable private VoipAudioManager audioManager = null;

	// Video
	private boolean videoEnabled = true;
	@NonNull final private CloseableReadWriteLock videoQualityNegotiation
		= new CloseableReadWriteLock(new ReentrantReadWriteLock());
	@Nullable private VoipVideoParams localVideoQualityProfile;
	@Nullable private VoipVideoParams remoteVideoQualityProfile;
	@Nullable private VoipVideoParams commonVideoQualityProfile;

	private static boolean isRunning = false;

	private boolean foregroundStarted = false;
	private boolean iceConnected = false;
	private boolean iceWasConnected = false;
	private boolean isError = false;
	private boolean micEnabled = true;
	private boolean uiDebugStatsEnabled = false;

	// The contact that is being called
	@Nullable private static ContactModel contact = null;

	// Offer SDP
	private SessionDescription offerSessionDescription;

	// Services
	private VoipStateService voipStateService;
	private PreferenceService preferenceService;
	private ContactService contactService;

	// Listeners
	private VoipMessageListener voipMessageListener;
	private PhoneStateListener hangUpRtcOnDeviceCallAnswered = new PSTNCallStateListener();

	// Receivers
	private IncomingMobileCallReceiver incomingMobileCallReceiver;

	// Media players
	@Nullable
	private MediaPlayerStateWrapper mediaPlayer;

	// PeerConnection configuration
	private Boolean useOpenSLES = null;
	private Boolean disableBuiltInAEC = null;

	// Network configuration
	private @Nullable Boolean networkIsMetered; // Only used for change detection!
	private volatile boolean networkIsRelayed = false;

	// Diagnostics
	private CpuMonitor cpuMonitor;
	private long callStartedTimeMs = 0;
	private static long callStartedRealtimeMs = 0;
	private static final long ACTIVITY_STATS_INTERVAL_MS = 1000;
	private static final long LOG_STATS_INTERVAL_MS_CONNECTING = 2000;
	private static final long LOG_STATS_INTERVAL_MS_CONNECTED = 30000;
	private static final long FRAME_DETECTOR_QUERY_INTERVAL_MS = 750;

	// Timeouts
	private final Timer iceDisconnectedSoundTimer = new Timer();
	private TimerTask iceDisconnectedSoundTimeout;
	private static final int ICE_DISCONNECTED_SOUND_TIMEOUT_MS = 1000;

	// Camera handling
	private @NonNull AtomicBoolean switchCamInProgress = new AtomicBoolean(false);
	private final Object capturingLock = new Object(); // Lock whenever modifying capturing
	private volatile boolean isCapturing = false; // Always synchronize on capturingLock!

	// Managers
	private NotificationManager notificationManager;
	private TelephonyManager telephonyManager;
	private SharedPreferences sharedPreferences;

	// Broadcast receivers
	private MeteredStatusChangedReceiver meteredStatusChangedReceiver;
	private BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
		@Override
		@UiThread
		public void onReceive(Context context, Intent intent) {
			if (intent != null) {
				final String action = intent.getAction();

				if (action != null) {
					switch (action) {
						case ACTION_HANGUP:
							onCallHangUp();
							break;
						case ACTION_ICE_CANDIDATES:
							if (!intent.hasExtra(EXTRA_CALL_ID)) {
								logger.warn("Received broadcast intent without EXTRA_CALL_ID: action={}", action);
							}
							final long callId = intent.getLongExtra(EXTRA_CALL_ID, 0L);
							final String contactIdentity = intent.getStringExtra(VoipCallService.EXTRA_CONTACT_IDENTITY);
							final VoipICECandidatesData candidatesData = (VoipICECandidatesData) intent.getSerializableExtra(EXTRA_CANDIDATES);
							if (contactIdentity != null && candidatesData != null) {
								long dataCallId = candidatesData.getCallIdOrDefault(0L);
								if (callId != dataCallId) {
									logger.error("Mismatch between intent call ID ({}) and data call ID ({})", callId, dataCallId);
								} else {
									handleNewCandidate(contactIdentity, candidatesData);
								}
							}
							break;
						case ACTION_MUTE_TOGGLE:
							onToggleMic();
							break;
						case ACTION_SET_AUDIO_DEVICE:
							if (intent.hasExtra(EXTRA_AUDIO_DEVICE)) {
								onToggleAudioDevice((AudioDevice) intent.getSerializableExtra(EXTRA_AUDIO_DEVICE));
							}
							break;
						case ACTION_QUERY_AUDIO_DEVICES:
							if (audioManager != null) {
								logger.debug("Requesting audio manager notify");
								audioManager.requestAudioManagerNotify();
							} else {
								logger.error("Cannot request audio manager notify: Audio manager is null");
							}
							break;
						case ACTION_QUERY_MIC_ENABLED:
							if (audioManager != null) {
								logger.debug("Requesting mute status notify");
								audioManager.requestMicEnabledNotify();
							} else {
								logger.error("Cannot request mute status notify: Audio manager is null");
							}
							break;
						case ACTION_ENABLE_DEBUG_INFO:
							enableUIDebugStats(true);
							break;
						case ACTION_DISABLE_DEBUG_INFO:
							enableUIDebugStats(false);
							break;
						case ACTION_START_CAPTURING:
							startCapturing();
							break;
						case ACTION_STOP_CAPTURING:
							stopCapturing();
							break;
						case ACTION_SWITCH_CAMERA:
							switchCamera();
							break;
						default:
							break;
					}
				}
			}
		}
	};

	// preference change receiver
	SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = (sharedPreferences, key) -> {
		if (getString(R.string.preferences__voip_video_profile).equals(key)) {
			// profile has changed
			this.updateOwnVideoQualityProfile(
				Boolean.TRUE.equals(this.meteredStatusChangedReceiver.getMetered().getValue()),
				this.networkIsRelayed
			);
		}
	};

	//region Stats

	/**
	 * The activity stats collector is enabled when long-pressing on the callee name.
	 * It then periodically collects and shows debug information on the call screen.
	 */
	private final RTCStatsCollectorCallback activityStatsCollector = new RTCStatsCollectorCallback() {
		final private VoipStats.Builder builder = new VoipStats.Builder()
			.withSelectedCandidatePair(true)
			.withTransport(true)
			.withCrypto(true)
			.withRtp(true)
			.withTracks(true)
			.withCodecs(false)
			.withCandidatePairs(VoipStats.CandidatePairVariant.OVERVIEW);
		private @Nullable VoipStats.State previousState;

		@Override
		public void onStatsDelivered(RTCStatsReport report) {
			// Extract stats report
			final VoipStats.Extractor extractor = this.builder.extractor();
			if (peerConnectionClient != null) {
				extractor.withRtpTransceivers(peerConnectionClient.getTransceivers());
			}
			if (this.previousState != null) {
				extractor.comparedTo(this.previousState);
			}
			final VoipStats stats = extractor.extract(report);

			// Determine whether a TURN relay is being used
			final boolean usesRelay = stats.usesRelay();
			RuntimeUtil.runInAsyncTask(() -> updateNetworkRelayState(usesRelay));

			// Create debug text
			final StringBuilder builder = new StringBuilder();
			stats.addShortRepresentation(builder);
			builder.append("\n\nopensl=");
			builder.append(useOpenSLES ? "yes" : "no");
			builder.append(" aec=");
			builder.append(disableBuiltInAEC ? "no" : "yes");
			try (CloseableLock locked = videoQualityNegotiation.tryRead(50, TimeUnit.MILLISECONDS)) {
				builder.append("\nL=").append(localVideoQualityProfile);
				builder.append("\nR=").append(remoteVideoQualityProfile);
				builder.append("\nC=").append(commonVideoQualityProfile);
			} catch (CloseableReadWriteLock.NotLocked ignored) { }

			// Store previous state
			this.previousState = stats.getState();

			// Notify listeners about new debug text
			VoipUtil.sendVoipBroadcast(
				getApplicationContext(),
				CallActivity.ACTION_DEBUG_INFO,
				"TEXT",
				builder.toString()
			);
		}
	};

	/**
	 * The debugStatsCollector collects stats periodically and writes them
	 * to the debug log.
	 */
	private CallStatsCollectorCallback debugStatsCollector = null;

	class CallStatsCollectorCallback implements RTCStatsCollectorCallback {
		final private @NonNull VoipStats.Builder builder;
		private @Nullable VoipStats.State previousState;
		private boolean includeTransceivers = true;

		CallStatsCollectorCallback(@NonNull VoipStats.Builder builder) {
			this.builder = builder;
		}

		@Override
		public void onStatsDelivered(RTCStatsReport report) {
			// Get extracted stats
			final VoipStats.Extractor extractor = this.builder.extractor();
			if (this.includeTransceivers && peerConnectionClient != null) {
				extractor.withRtpTransceivers(peerConnectionClient.getTransceivers());
			}
			if (this.previousState != null) {
				extractor.comparedTo(this.previousState);
			}
			final VoipStats stats = extractor.extract(report);
			final StringBuilder builder = new StringBuilder();
			builder.append("Stats\n");
			stats.addRepresentation(builder);

			// Determine whether a TURN relay is being used
			final boolean usesRelay = stats.usesRelay();
			RuntimeUtil.runInAsyncTask(() -> updateNetworkRelayState(usesRelay));

			// Update state
			this.previousState = stats.getState();
			// Don't log transceivers in subsequent runs
			this.includeTransceivers = false;

			// Log stats
			logger.info(builder.toString());
		}
	}

	private void updateNetworkRelayState(final boolean networkIsRelayed) {
		boolean changed = this.networkIsRelayed != networkIsRelayed;
		this.networkIsRelayed = networkIsRelayed;
		if (changed) {
			// If relay status changes, update video quality profile
			this.updateOwnVideoQualityProfile(
				Boolean.TRUE.equals(meteredStatusChangedReceiver.getMetered().getValue()),
				networkIsRelayed
			);
		}
	}

	private FrameDetectorCallback frameDetector = null;

	/**
	 * This class tracks incoming frames. It can notify the passed in runnables
	 * when the call partner starts and stops sending video frames.
	 *
	 * Instances of this class are not thread safe. The methods may be called from different
	 * threads, but not concurrently.
	 *
	 * Note: Due to frequent changes to the stats API spec[1], this might require
	 * changes when updating WebRTC!
	 *
	 * [1]: https://www.w3.org/TR/webrtc-stats/
	 */
	@SameThread
	static class FrameDetectorCallback implements RTCStatsCollectorCallback {
		private @NonNull State state = State.STOPPED;
		private @Nullable Long lastFrameDetectionTimestampMs;
		private long lastFrameCount = 0;

		private final @NonNull Runnable framesStarted;
		private final @NonNull Runnable framesStopped;

		/**
		 * If no frames are sent for at least the specified number of milliseconds,
		 * consider that a "video frames stopped" event.
		 */
		@SuppressWarnings("FieldCanBeLocal")
		private static long STOP_THRESHOLD_MS = 1000;

		FrameDetectorCallback(@NonNull Runnable framesStarted, @NonNull Runnable framesStopped) {
			this.framesStarted = framesStarted;
			this.framesStopped = framesStopped;
		}

		@Override
		public void onStatsDelivered(RTCStatsReport report) {
			if (!ConfigUtils.isVideoCallsEnabled()) {
				return;
			}

			final long totalFramesReceived = getTotalFramesReceived(report);
			logger.trace("FrameDetectorCallback: Total frames received = " + totalFramesReceived);

			if (totalFramesReceived > this.lastFrameCount) {
				// Frame count increased
				this.lastFrameCount = totalFramesReceived;
				this.lastFrameDetectionTimestampMs = System.nanoTime() / 1000;
				if (this.state == State.STOPPED) {
					this.state = State.STARTED;
					logger.debug("FrameDetectorCallback: Started");
					this.framesStarted.run();
				}
			} else if (totalFramesReceived == this.lastFrameCount) {
				// Frame count stayed the same
				if (this.state == State.STARTED && this.lastFrameDetectionTimestampMs != null) {
					final long msElapsed = (System.nanoTime() / 1000) - this.lastFrameDetectionTimestampMs;
					if (msElapsed > STOP_THRESHOLD_MS) {
						this.state = State.STOPPED;
						logger.debug("FrameDetectorCallback: Stopped");
						this.framesStopped.run();
					}
				}
			} else {
				// Frame count decreased?!
				logger.warn(
					"FrameDetectorCallback: Frame count decreased from {} to {}",
					this.lastFrameCount,
					totalFramesReceived
				);
				this.lastFrameCount = totalFramesReceived;
			}
		}

		/**
		 * Extract the total number of frames received across all incoming video tracks.
		 */
		private long getTotalFramesReceived(RTCStatsReport report) {
			return StreamSupport
				// Iterate over all entries in the stats map
				.parallelStream(report.getStatsMap().values())
				// Only consider track stats
				.filter(stats -> "track".equals(stats.getType()))
				// Only consider active remote video sources
				.filter(stats -> {
					final Map<String, Object> members = stats.getMembers();
					final Object isRemoteSrc = members.get("remoteSource");
					final Object hasEnded = members.get("ended");
					final Object kind = members.get("kind");
					final Object framesReceived = stats.getMembers().get("framesReceived");
					final Object trackIdentifier = stats.getMembers().get("trackIdentifier");
					return isRemoteSrc instanceof Boolean
						&& hasEnded instanceof Boolean
						&& kind instanceof String
						&& framesReceived instanceof Long
						&& trackIdentifier instanceof String
						&& (Boolean) isRemoteSrc
						&& !((Boolean) hasEnded)
						&& kind.equals("video");
				})
				.mapToLong(stats -> {
					//noinspection ConstantConditions
					return (Long) stats.getMembers().get("framesReceived");
				})
				.sum();
		}

		public enum State {
			STOPPED,
			STARTED,
		}
	}

	//endregion

	public static boolean isRunning() {
		return isRunning;
	}

	public static long getStartTime() {
		return callStartedRealtimeMs;
	}

	public static String getOtherPartysIdentity() {
		return contact != null ? contact.getIdentity() : null;
	}

	//region Lifecycle

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		super.onBind(intent);
		return null;
	}

	@Override
	public int onStartCommand(@NonNull Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		logger.info("onStartCommand");

		// Start flag, to configure whether and how the service is restarted after being killed
		// by the system. For more details, see https://developer.android.com/reference/android/app/Service.html#constants_1
		//
		// If the VoipCallService is killed due to OOM (and onDestroy is called), the
		// PeerConnectionClient will die as well. Therefore it does not make sense to restart
		// the service without any intent (and thus without context), so we choose START_NOT_STICKY
		// here.
		final int RESTART_BEHAVIOR = START_NOT_STICKY;

		// If the service is started with action HANGUP, then we can send the hangup message
		// and stop ourselves.
		final String action = intent.getAction();
		if (VoipCallService.ACTION_HANGUP.equals(action)) {
			onCallHangUp();
			return RESTART_BEHAVIOR;
		}

		final String contactIdentity = intent.getStringExtra(EXTRA_CONTACT_IDENTITY);
		if (contactIdentity == null) {
			logger.error("Missing contact identity in intent!");
			return RESTART_BEHAVIOR;
		}

		// if the intent creation was initiated from the phone we additionally cancel a potentially already opened activity on the watch
		final boolean cancelActivityOnWearable = intent.getBooleanExtra(EXTRA_CANCEL_WEAR, false);
		if (cancelActivityOnWearable && ConfigUtils.isPlayServicesInstalled(getAppContext())) {
			voipStateService.cancelOnWearable(VoipStateService.TYPE_ACTIVITY);
		}

		final VoipICECandidatesData candidatesData =
			(VoipICECandidatesData) intent.getSerializableExtra(EXTRA_CANDIDATES);

		// Determine the Call ID. If the Call ID is set to -1L (NOTE: must be a long, not int),
		// that means "generate a new one".
		if (!intent.hasExtra(EXTRA_CALL_ID)) {
			logger.warn("onStartCommand intent without Call ID");
		}
		long callId = intent.getLongExtra(EXTRA_CALL_ID, 0L);
		if (callId == -1) {
			callId = RandomUtil.generateRandomU32();
		}

		// If candidates are sent with the intent, then we simply want to add those
		// to the existing peer connection.
		if (candidatesData != null) {
			handleNewCandidate(contactIdentity, candidatesData);
		} else {
			// Otherwise, we handle a new call.
			handleNewCall(callId, contactIdentity, intent);
		}

		return RESTART_BEHAVIOR;
	}

	@Override
	public void onCreate() {
		logger.info("onCreate");
		super.onCreate();

		isRunning = true;

		// Create CPU monitor (only in DEBUG builds below Android O,
		// access to /proc is not possible anymore in Oreo)
		if (BuildConfig.DEBUG && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			this.cpuMonitor = new CpuMonitor(this);
		}

		// Get services
		try {
			final ServiceManager serviceManager = getServiceManager();
			this.voipStateService = serviceManager.getVoipStateService();
			this.preferenceService = serviceManager.getPreferenceService();
			this.contactService = serviceManager.getContactService();
		} catch (Exception e) {
			this.abortCall(R.string.voip_error_init_call, "Cannot instantiate services", e, false);
			return;
		}

		this.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		// Create video context
		logger.debug("Creating video context");
		this.voipStateService.createVideoContext();

		// Register intent filters
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_HANGUP);
		filter.addAction(ACTION_ICE_CANDIDATES);
		filter.addAction(ACTION_MUTE_TOGGLE);
		filter.addAction(ACTION_SET_AUDIO_DEVICE);
		filter.addAction(ACTION_QUERY_AUDIO_DEVICES);
		filter.addAction(ACTION_QUERY_MIC_ENABLED);
		filter.addAction(ACTION_ENABLE_DEBUG_INFO);
		filter.addAction(ACTION_DISABLE_DEBUG_INFO);
		filter.addAction(ACTION_START_CAPTURING);
		filter.addAction(ACTION_STOP_CAPTURING);
		filter.addAction(ACTION_SWITCH_CAMERA);

		LocalBroadcastManager.getInstance(this).registerReceiver(localBroadcastReceiver, filter);

		// let lifecycle take care of resource management
		meteredStatusChangedReceiver = new MeteredStatusChangedReceiver(this, this);
		meteredStatusChangedReceiver.getMetered().observe(this, metered -> {
			// the connectivity status has changed - adjust parameters
			logger.info("Metered status changed to {}", metered);
			if (metered == null) {
				return;
			}
			boolean changed = !metered.equals(this.networkIsMetered);
			this.networkIsMetered = metered;
			if (changed && peerConnectionClient != null && preferenceService != null) {
				this.updateOwnVideoQualityProfile(metered, this.networkIsRelayed);
			}
		});

		telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		if (telephonyManager != null) {
			telephonyManager.listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_CALL_STATE);
		}

		if (preferenceService.isRejectMobileCalls()) {
			incomingMobileCallReceiver = new IncomingMobileCallReceiver();
			registerReceiver(incomingMobileCallReceiver, new IntentFilter("android.intent.action.PHONE_STATE"));
		}

		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		logger.info("onDestroy");

		if (localBroadcastReceiver != null) {
			try {
				LocalBroadcastManager.getInstance(this).unregisterReceiver(localBroadcastReceiver);
			} catch (IllegalArgumentException e) {
				// ignore if not registered due to premature destruction
			}
		}

		if (incomingMobileCallReceiver != null) {
			try {
				unregisterReceiver(incomingMobileCallReceiver);
			} catch (IllegalArgumentException e) {
				// ignore if not registered due to premature destruction
			}
		}

		// clear telephony listener
		if (telephonyManager != null) {
			telephonyManager.listen(hangUpRtcOnDeviceCallAnswered, PhoneStateListener.LISTEN_NONE);
		}

		if (sharedPreferences != null) {
			sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
		}

		this.cancelInCallNotification();

		isRunning = false;

		// Clean up resources
		this.cleanup();

		super.onDestroy();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		logger.trace("onTaskRemoved");
		super.onTaskRemoved(rootIntent);
	}

	//endregion

	@UiThread
	public void onCallHangUp() {
		final CallStateSnapshot callState = this.voipStateService.getCallState();

		logger.info("{}: Hanging up call", callState.getCallId());

		if (callState.isInitializing() || callState.isCalling()) {
			new AsyncTask<Pair<ContactModel, Long>, Void, Void>() {
				@Override
				protected Void doInBackground(Pair<ContactModel, Long>... params) {
					try {
						voipStateService.sendCallHangupMessage(
							params[0].first,
							params[0].second
						);
					} catch (ThreemaException e) {
						abortCall(R.string.an_error_occurred, "Could not send hangup message", e, false);
					}
					return null;
				}
			}.execute(new Pair<>(contact, callState.getCallId()));
		}
		if (ConfigUtils.isPlayServicesInstalled(getAppContext())){
			voipStateService.cancelOnWearable(VoipStateService.TYPE_ACTIVITY);
		}
		disconnect();
	}

	/**
	 * Handle a new incoming or outgoing call.
	 */
	@UiThread
	private void handleNewCall(final long callId, final String contactIdentity, final Intent intent) {
		logger.trace("handleNewCall ({} / {})", callId, contactIdentity);

		if (this.voipStateService == null) {
			logger.debug("voipStateService not available.");
			return;
		}

		// Do not initiate a new call if one is still running
		final CallStateSnapshot callState = this.voipStateService.getCallState();
		if (callState.isCalling()) {
			logger.info(
				"{}: Call is currently ongoing. Ignoring request to initiate new call ({}).",
				callState.getCallId(), callId
			);
			return;
		}

		// Detect whether we're initiator or responder
		final boolean isInitiator = intent.getBooleanExtra(EXTRA_IS_INITIATOR, false);
		this.voipStateService.setInitiator(isInitiator);

		logger.info(
			"{}: Handle new call with {}, we are the {}",
			callId,
			contactIdentity,
			isInitiator ? "caller" : "callee"
		);

		// Cancel any pending notifications
		if (!isInitiator) {
			this.voipStateService.cancelCallNotificationsForNewCall();
		}

		// Get contact model from intent parameters
		ContactModel newContact = null;
		try {
			newContact = getServiceManager().getContactService().getByIdentity(contactIdentity);
		} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
			logger.error(callId + ": Could not get contact model", e);
		}
		if (newContact == null) {
			// We cannot initialize a new call if the contact cannot be looked up.
			this.abortCall(R.string.voip_error_init_call, "Cannot retrieve contact for ID " + contactIdentity, false);
			return;
		} else {
			contact = newContact;
		}

		// Initialize state variables
		this.iceConnected = false;
		this.isError = false;
		this.voipStateService.setStateInitializing(callId);

		// Can we use videocalls?
		if (this.videoEnabled && !ConfigUtils.isVideoCallsEnabled()) {
			logger.info("{}: videoEnabled=false, diabled via user config", callId);
			this.videoEnabled = false;
		}
		if (this.videoEnabled && !ThreemaFeature.canVideocall(contact.getFeatureMask())) {
			logger.info("{}: videoEnabled=false, remote feature mask does not support video calls", callId);
			this.videoEnabled = false;
		}

		// If we're the responder, we also got the SDP data from the initial offer.
		if (!isInitiator) {
			// Look up CallOffer
			final VoipCallOfferData callOfferData = this.voipStateService.getCallOffer(callId);
			if (callOfferData == null) {
				abortCall(R.string.voip_error_init_call, "Call offer for Call ID " + callId + " not found", false);
				return;
			}

			// Ensure that offer SDP exists
			final VoipCallOfferData.OfferData offerData = callOfferData.getOfferData();
			if (offerData == null || offerData.getSdp() == null || offerData.getSdpType() == null) {
				abortCall(R.string.voip_error_init_call, "Call offer does not contain SDP", true);
				return;
			}
			final SessionDescription.Type sdpType = SdpUtil.getSdpType(offerData.getSdpType());
			if (sdpType == null) {
				abortCall(R.string.voip_error_init_call, String.format("handleNewCall: Invalid sdpType: {}", offerData.getSdpType()), true);
				return;
			}
			this.offerSessionDescription = new SessionDescription(sdpType, offerData.getSdp());

			// If the offerer does not signal video support, disable it
			final FeatureList offerCallFeatures = callOfferData.getFeatures();
			if (!offerCallFeatures.hasFeature(VideoFeature.NAME)) {
				logger.info("{}: videoEnabled=false, remote does not signal support for video calls", callId);
				this.videoEnabled = false;
			}
		}

		// Initialize peer connection parameters
		this.useOpenSLES = this.preferenceService.getAECMode().equals("sw");
		this.disableBuiltInAEC = this.preferenceService.getAECMode().equals("sw"); // Hardware acoustic echo cancelation
		final boolean disableBuiltInAGC = false; // Automatic gain control
		final boolean disableBuiltInNS = false; // Noise suppression
		final boolean enableLevelControl = false;
		final boolean videoCallEnabled = this.videoEnabled;
		final String videoCodec = this.preferenceService.getVideoCodec();
		final boolean videoCodecHwAcceleration = this.videoEnabled && !videoCodec.equals(PreferenceService.VIDEO_CODEC_SW);
		final boolean videoCodecEnableVP8 =  !videoCodec.equals(PreferenceService.VIDEO_CODEC_NO_VP8);
		final boolean videoCodecEnableH264HiP = !videoCodec.equals(PreferenceService.VIDEO_CODEC_NO_H264HIP);
		final SdpPatcher.RtpHeaderExtensionConfig rtpHeaderExtensionConfig =
			this.videoEnabled
				? SdpPatcher.RtpHeaderExtensionConfig.ENABLE_WITH_ONE_AND_TWO_BYTE_HEADER
				: SdpPatcher.RtpHeaderExtensionConfig.DISABLE;
		final boolean gatherContinually = true;

		boolean forceTurn;
		if (contact.getVerificationLevel() == VerificationLevel.UNVERIFIED) {
			// Force TURN if the contact is unverified, to hide the local IP address.
			// This makes sure that a stranger cannot find out your IP simply by calling you.
			logger.info("{}: Force TURN since contact is unverified", callId);
			forceTurn = true;
		} else {
			// Don't force turn for verified contacts unless the user explicitly enabled
			// the setting.
			forceTurn = this.preferenceService.getForceTURN();
			if (forceTurn) {
				logger.info("{}: Force TURN as requested by user", callId);
			}
		}

		final PeerConnectionClient.PeerConnectionParameters peerConnectionParameters = new PeerConnectionClient.PeerConnectionParameters(
				false,
				this.useOpenSLES, this.disableBuiltInAEC, disableBuiltInAGC, disableBuiltInNS, enableLevelControl,
				videoCallEnabled, videoCodecHwAcceleration, videoCodecEnableVP8, videoCodecEnableH264HiP,
				rtpHeaderExtensionConfig,
				forceTurn, gatherContinually, this.preferenceService.allowWebrtcIpv6()
		);

		// Initialize peer connection
		if (this.voipStateService.getVideoContext() == null) {
			throw new IllegalStateException("Video context is null");
		}
		this.peerConnectionClient = new PeerConnectionClient(
			getAppContext(),
			peerConnectionParameters,
			this.voipStateService.getVideoContext().getEglBaseContext(),
			callId
		);
		this.peerConnectionClient.setEventHandler(VoipCallService.this);

		try {
			boolean factoryCreated = this.peerConnectionClient
				.createPeerConnectionFactory()
				.get(10, TimeUnit.SECONDS);
			if (!factoryCreated) {
				this.abortCall(R.string.voip_error_init_call, "Peer connection factory could not be created", true);
			}
		} catch (InterruptedException e) {
			this.abortCall(R.string.voip_error_init_call, "Interrupted while creating peer connection factory", e, false);
			return;
		} catch (ExecutionException e) {
			this.abortCall(R.string.voip_error_init_call, "Exception while waiting for peer connection factory", e, true);
			return;
		} catch (TimeoutException e) {
			this.abortCall(R.string.voip_error_init_call, "Failed to create peer connection factory within 10 seconds", e, true);
			return;
		}

		// Maybe enable statistics callback
		this.enableUIDebugStats(this.uiDebugStatsEnabled);

		// directly enable camera if desired
		boolean launchVideo = false;
		if (isInitiator) {
			if (intent.getBooleanExtra(VoipCallService.EXTRA_LAUNCH_VIDEO, false)) {
				if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
					launchVideo = true;
					intent.putExtra(VoipCallService.EXTRA_LAUNCH_VIDEO, false);
				}
			}
		}

		// Start the call
		startCall(!isInitiator, launchVideo);
	}

	@UiThread
	public boolean onToggleMic() {
		micEnabled = !micEnabled;

		final long callId = this.voipStateService.getCallState().getCallId();
		logger.debug("{}, onToggleMic enabled = {}", callId, micEnabled);

		if (peerConnectionClient != null) {
			peerConnectionClient.setLocalAudioTrackEnabled(micEnabled);
		}
		this.audioManager.setMicEnabled(micEnabled);
		return micEnabled;
	}

	@UiThread
	public synchronized void onToggleAudioDevice(AudioDevice audioDevice) {
		final long callId = this.voipStateService.getCallState().getCallId();
		if (this.audioManager != null) {
			// Do the switch if possible
			if (this.audioManager.hasAudioDevice(audioDevice)) {
				this.audioManager.selectAudioDevice(audioDevice);
			} else {
				this.showSingleToast("Cannot switch to " + audioDevice, Toast.LENGTH_LONG);
				logger.error("{}: Cannot switch to {}: Device not available", callId, audioDevice);
			}
		} else {
			this.showSingleToast("Cannot change audio device", Toast.LENGTH_LONG);
			logger.error("{}: Cannot change audio device: Audio manager is null", callId);
		}
	}

	@AnyThread
	private synchronized void enableUIDebugStats(boolean enable) {
		if (this.peerConnectionClient == null) {
			logger.error("Cannot enable/disable UI debug stats: Peer connection client is null");
			return;
		}
		this.uiDebugStatsEnabled = enable;
		if (enable) {
			if (!this.peerConnectionClient.isPeriodicStatsRegistered(this.activityStatsCollector)) {
				this.peerConnectionClient.registerPeriodicStats(
					this.activityStatsCollector,
					VoipCallService.ACTIVITY_STATS_INTERVAL_MS
				);
			}
		} else {
			this.peerConnectionClient.unregisterPeriodicStats(this.activityStatsCollector);
		}
	}

	@UiThread
	private synchronized void startCall(boolean startActivity, boolean launchVideo) {
		final long callId = this.voipStateService.getCallState().getCallId();
		logger.trace("{}: startCall", callId);

		this.callStartedTimeMs = System.currentTimeMillis();
		callStartedRealtimeMs = SystemClock.elapsedRealtime();

		// Show notification
		this.showInCallNotification(this.callStartedTimeMs, callStartedRealtimeMs);

		logger.info("{}: Video calls are {}", callId, this.videoEnabled ? "enabled" : "disabled");

		// Make sure that the peerConnectionClient is initialized
		final @StringRes int initError = R.string.voip_error_init_call;
		if (this.peerConnectionClient == null) {
			this.abortCall(initError, "Cannot start call: peerConnectionClient is not initialized", false);
			return;
		} else if (contact == null) {
			this.abortCall(initError, "Cannot start call: contact is not initialized", false);
			return;
		} else if (this.videoEnabled && this.voipStateService.getVideoContext() == null) {
			this.abortCall(initError, "Cannot start call: video context is not initialized", false);
			return;
		}
		logger.info("{}: Setting up call with {}", callId, contact.getIdentity());

		// Start activity if desired
		if (startActivity) {
			final Intent intent = new Intent(this.getApplicationContext(), CallActivity.class);
			intent.putExtra(EXTRA_ACTIVITY_MODE, CallActivity.MODE_ACTIVE_CALL);
			intent.putExtra(EXTRA_CONTACT_IDENTITY, contact.getIdentity());
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			this.getApplicationContext().startActivity(intent);
		}

		// Create and audio manager that will take care of audio routing,
		// audio modes, audio device enumeration etc.
		this.audioManager = VoipAudioManager.create(getApplicationContext(), voipStateService.getRingtoneAudioFocusAbandoned());
		VoipListenerManager.audioManagerListener.add(this.audioManagerListener);
		logger.info("{}: Starting the audio manager...", callId);
		this.audioManager.start();

		// Create peer connection
		logger.info("{}: Creating peer connection, delay={}ms", callId, System.currentTimeMillis() - this.callStartedTimeMs);
		final VideoSink localVideoSink = this.voipStateService.getVideoContext().getLocalVideoSinkProxy();
		final VideoSink remoteVideoSink = this.voipStateService.getVideoContext().getRemoteVideoSinkProxy();
		peerConnectionClient.createPeerConnection(localVideoSink, remoteVideoSink);

		// Set initial video quality parameters
		this.updateOwnVideoQualityProfile(
			Boolean.TRUE.equals(this.meteredStatusChangedReceiver.getMetered().getValue()),
			this.networkIsRelayed
		);

		// Initialize peer connection
		if (this.voipStateService.isInitiator() == Boolean.TRUE) {
			this.initAsInitiator(callId, launchVideo);
		} else {
			this.initAsResponder(callId);
		}
	}

	@UiThread
	private void initAsInitiator(long callId, final boolean launchVideo) {
		logger.info("{}: Init call as initiator", callId);

		// Make sure that the peerConnectionClient is initialized
		if (this.peerConnectionClient == null) {
			this.abortCall(R.string.voip_error_init_call, "Cannot initialize: peerConnectionClient is null", false);
			return;
		}

		// Register listeners
		this.voipMessageListener = new VoipMessageListener() {
			@Override
			public synchronized void onOffer(final String identity, final VoipCallOfferData data) {
				logger.error("{}: Received offer as initiator", data.getCallIdOrDefault(0L));
			}

			@Override
			public synchronized void onAnswer(final String identity, final VoipCallAnswerData data) {
				final long callId = data.getCallIdOrDefault(0L);
				logger.info("{}: Received answer: {}", callId, data.getAction());

				// Make sure that the peerConnectionClient is initialized
				if (peerConnectionClient == null) {
					logger.error("{}: Ignoring answer: peerConnectionClient is not initialized", callId);
					return;
				}

				// Check state
				final CallStateSnapshot callState = voipStateService.getCallState();
				if (!callState.isInitializing()) {
					logger.error("{}: Ignoring answer: callState is {}", callId, callState);
					return;
				}

				// Check contact in answer
				if (contact == null) {
					logger.error("{}: Ignoring answer: contact is not initialized", callId);
					return;
				} else if (!TestUtil.compare(contact.getIdentity(), identity)) {
					logger.error("{}: Ignoring answer: Does not match current contact", callId);
					return;
				}

				// Parse action
				if (data.getAction() == null) {
					logger.error("{}: Ignoring answer: Action is null", callId);
					return;
				}
				switch (data.getAction()) {
					case VoipCallAnswerData.Action.ACCEPT:
						break;
					case VoipCallAnswerData.Action.REJECT:
						// Log event
						logger.info("{}: Call to {} was rejected (reason code: {})",
							callId, contact.getIdentity(), data.getRejectReason());

						// Stop ringing tone
						stopLoopingSound(callId);

						// Update UI to show disconnecting status
						preDisconnect(callId);

						// Disconnect after a while
						new Handler(Looper.getMainLooper()).postDelayed(
							() -> disconnect(),
							4050 /* busy sound takes 4*1 seconds. add 50ms delay budget. */
						);

						// Play busy sound
						final boolean played = playSound(R.raw.busy_tone, "busy");
						if (!played) {
							logger.error("Could not play busy tone!");
						}

						return;
					default:
						abortCall(
							"An error occured while processing the call answer",
							"Invalid call answer action: " + data.getAction(),
							false
						);
						return;
				}

				// Parse session description
				final VoipCallAnswerData.AnswerData answerData = data.getAnswerData();
				if (answerData == null) {
					logger.error("{}: Ignoring answer: Answer data is null", callId);
					return;
				}
				final SessionDescription sd = SdpUtil.getAnswerSessionDescription(answerData);
				if (sd == null) {
					abortCall(
						"An error occurred while processing the call answer",
						String.format("Received invalid answer SDP: {} / {}", answerData.getSdpType(), answerData.getSdp()),
						false
					);
					return;
				}

				// Detect video support in answer
				if (!data.getFeatures().hasFeature(VideoFeature.NAME)) {
					logger.info("{}: videoEnabled=false, remote does not signal support for video calls", callId);
					videoEnabled = false;
					VoipUtil.sendVoipBroadcast(getApplicationContext(), CallActivity.ACTION_DISABLE_VIDEO);
				}

				// Set remote description
				peerConnectionClient.setRemoteDescription(sd);

				// Now that the answer is set, we don't need to listen for further messages.
				VoipListenerManager.messageListener.remove(VoipCallService.this.voipMessageListener);
			}

			@Override
			public void onRinging(String identity, final VoipCallRingingData data) {
				long callId = data.getCallIdOrDefault(0L);
				logger.info("{}: Peer device is ringing", callId);
				startLoopingSound(callId, R.raw.ringing_tone, "ringing");
				VoipUtil.sendVoipBroadcast(getAppContext(), CallActivity.ACTION_PEER_RINGING);

				if (launchVideo) {
					startCapturing();
				}
			}

			@Override
			public void onHangup(String identity, final VoipCallHangupData data) {
				logger.info("{}: Received hangup from peer", data.getCallIdOrDefault(0L));
			}

			@Override
			public boolean handle(final String identity) {
				return contact != null && TestUtil.compare(contact.getIdentity(), identity);
			}
		};
		VoipListenerManager.messageListener.add(this.voipMessageListener);

		logger.info("{}: Creating offer...", callId);
		this.peerConnectionClient.createOffer();
	}

	@UiThread
	private void initAsResponder(long callId) {
		logger.info("{}: Init call as responder", callId);

		// Make sure that the peerConnectionClient is initialized
		if (this.peerConnectionClient == null) {
			abortCall(R.string.voip_error_init_call, "this.peerConnectionClient is null, even though it should be initialized", true);
			return;
		}

		// Parse offer session description
		if (this.offerSessionDescription == null) {
			abortCall(R.string.voip_error_init_call, "this.offerSessionDescription is null, even though it should be initialized", true);
			return;
		}

		// Set remote description
		logger.info("{}: Setting remote description", callId);
		this.peerConnectionClient.setRemoteDescription(this.offerSessionDescription);
	}

	/**
	 * A new candidate message was received.
	 */
	@UiThread
	private void handleNewCandidate(
		final String contactIdentity,
		final @NonNull VoipICECandidatesData candidatesData
	) {
		final long currentCallId = this.voipStateService.getCallState().getCallId();

		// Sanity checks
		if (contact == null) {
			logger.info("{}: Ignore candidates from broadcast, contact hasn't been initialized yet", currentCallId);
			return;
		}
		if (!TestUtil.compare(contactIdentity, contact.getIdentity())) {
			logger.info("{}: Ignore candidates from broadcast targeted at another identity (current {}, target {})",
				currentCallId, contact.getIdentity(), contactIdentity);
			return;
		}

		logger.info("{}: Process candidates from broadcast", currentCallId);
		this.processCandidates(candidatesData);
	}

	/**
	 * Called as soon as the peer connection has been established.
	 */
	@AnyThread
	private synchronized void callConnected(final long callId) {
		final long delta = System.currentTimeMillis() - this.callStartedTimeMs;
		if (BuildConfig.DEBUG) {
			this.showSingleToast("Call " + callId + " connected: delay=" + delta + "ms", Toast.LENGTH_LONG);
		}
		if (this.peerConnectionClient == null || this.isError) {
			this.abortCall(R.string.voip_error_call, callId + ": Call is connected in closed or error state", false);
			return;
		}

		// Update state
		this.voipStateService.setStateCalling(callId);

		// Stop ringing tone
		this.stopLoopingSound(callId);

		// Play pickup sound
		final boolean played = this.playSound(R.raw.threema_pickup, "pickup");
		if (!played) {
			logger.error("{}: Could not play pickup sound!", callId);
		}

		// Start call duration counter
		VoipUtil.sendVoipBroadcast(getApplicationContext(), CallActivity.ACTION_CONNECTED);

		// Send inital configuration
		try (CloseableLock ignored = this.videoQualityNegotiation.read()) {
			if (this.localVideoQualityProfile != null) {
				this.peerConnectionClient.sendSignalingMessage(this.localVideoQualityProfile);
			}
		}

		// Notify listeners
		if (contact == null) {
			logger.error("{}: contact is null in callConnected()", callId);
		} else {
			final String contactIdentity = contact.getIdentity();
			final Boolean isInitiator = this.voipStateService.isInitiator();
			VoipListenerManager.callEventListener.handle(listener -> {
				if (isInitiator == null) {
					logger.error("{}: voipStateService.isInitiator() is null in callConnected()", callId);
				} else {
					listener.onStarted(contactIdentity, isInitiator);
				}
			});
		}
	}

	/**
	 * This is run to initialize the disconnecting process.
	 *
	 * The method is only needed if there is a delay between starting and finishing the
	 * disconnection, e.g. when blocking the UI for a few seconds to play the "busy" sound.
	 */
	@AnyThread
	private synchronized void preDisconnect(long callId) {
		if (this.voipStateService != null && !this.voipStateService.getCallState().isIdle()) {
			this.voipStateService.setStateDisconnecting(callId);
			VoipUtil.sendVoipBroadcast(getApplicationContext(), CallActivity.ACTION_PRE_DISCONNECT);
		}
	}

	/**
	 * Clean up resources if they haven't been cleaned yet.
	 *
	 * This method can safely be called multiple times.
	 */
	private void cleanup() {
		logger.info("Cleaning up resources");

		// Stop timers
		synchronized (this.iceDisconnectedSoundTimer) {
			if (this.iceDisconnectedSoundTimeout != null) {
				this.iceDisconnectedSoundTimeout.cancel();
				this.iceDisconnectedSoundTimeout = null;
			}
		}

		// Remove listeners
		if (this.voipMessageListener != null) {
			VoipListenerManager.messageListener.remove(this.voipMessageListener);
			this.voipMessageListener = null;
		}

		// Close peerConnectionClient
		if (this.peerConnectionClient != null) {
			// Note: This needs to be here to ensure that the stats-after-closing contains all
			//       candidate pairs.
			this.iceConnected = false;

			synchronized (this) {
				// Unregister debug stats collector & do a final stats collection
				final VoipStats.Builder statsBuilder = new VoipStats.Builder()
					.withSelectedCandidatePair(false)
					.withTransport(true)
					.withCrypto(true)
					.withRtp(true)
					.withTracks(true)
					.withCodecs(false)
					.withCandidatePairs(VoipStats.CandidatePairVariant.OVERVIEW_AND_DETAILED);
				this.peerConnectionClient.unregisterPeriodicStats(this.debugStatsCollector);
				this.debugStatsCollector = new CallStatsCollectorCallback(statsBuilder);
				this.peerConnectionClient.setAfterClosingStatsCallback(this.debugStatsCollector);

				// Unregister frame detector
				if (this.frameDetector != null) {
					this.peerConnectionClient.unregisterPeriodicStats(this.frameDetector);
					this.frameDetector = null;
				}
			}
			this.peerConnectionClient.close();
			this.peerConnectionClient = null;
		}

		// Stop audio manager
		if (this.audioManager != null) {
			VoipListenerManager.audioManagerListener.remove(this.audioManagerListener);
			this.audioManager.stop();
			this.audioManager = null;
		}

		// Stop media players
		if (this.mediaPlayer != null) {
			logger.info("Stopping and releasing ringing tone media player");
			this.mediaPlayer.stop();
			this.mediaPlayer.release();
			this.mediaPlayer = null;
		}

		// Stop CPU monitor
		if (this.cpuMonitor != null) {
			this.cpuMonitor.pause();
		}

		// Update state
		if (this.voipStateService != null) {
			// Release video context
			this.voipStateService.releaseVideoContext();
			this.voipStateService.setVideoRenderMode(VIDEO_RENDER_FLAG_NONE);
			this.voipStateService.setStateIdle();
		}
	}

	/**
	 * Disconnect from remote resources, dispose of local resources, and exit.
	 *
	 * If a message is specified, it is appended to the "call has ended" toast.
	 */
	@UiThread
	private synchronized void disconnect(@Nullable String message) {
		final CallStateSnapshot callState = this.voipStateService.getCallState();
		final long callId = callState.getCallId();
		logger.info(
			"{}: disconnect (isConnected? {} | isError? {} | message: {})",
			callId, this.iceConnected, this.isError, message
		);

		// If the call is still connected, notify listeners about the finishing
		if (this.voipStateService != null) {
			if (callState.isCalling() && contact != null) {
				// Notify listeners
				final String contactIdentity = contact.getIdentity();
				final Boolean isInitiator = this.voipStateService.isInitiator();
				final Integer duration = this.voipStateService.getCallDuration();
				VoipListenerManager.callEventListener.handle(listener -> {
					if (isInitiator == null) {
						logger.error("isInitiator is null in disconnect()");
					} else if (duration == null) {
						logger.error("duration is null in disconnect()");
					} else {
						listener.onFinished(contactIdentity, isInitiator, duration);
					}
				});
			}
			this.voipStateService.cancelOnWearable(VoipStateService.TYPE_ACTIVITY);
		}

		if (ConfigUtils.isPlayServicesInstalled(getAppContext())){
			voipStateService.cancelOnWearable(VoipStateService.TYPE_ACTIVITY);
		}

		this.preDisconnect(callId);

		this.cleanup();

		stopForeground(true);

		if (this.iceConnected && !this.isError) {
			VoipUtil.sendVoipBroadcast(this, CallActivity.ACTION_DISCONNECTED);
		} else {
			VoipUtil.sendVoipBroadcast(this, CallActivity.ACTION_CANCELLED);
		}

		String toastMsg = getString(R.string.voip_call_finished);
		if (message != null) {
			toastMsg += ": " + message;
		}
		this.showSingleToast(toastMsg, Toast.LENGTH_LONG);

		stopSelf();
	}

	@UiThread
	private synchronized void disconnect() {
		this.disconnect(null);
	}

	/**
	 * Add or remove ICE candidates.
	 */
	private void processCandidates(@NonNull VoipICECandidatesData data) {
		// Null check
		if (this.peerConnectionClient == null) {
			logger.warn("Ignored ICE candidate message, peerConnectionClient is null");
			return;
		}

		// IPv6 check
		if (!this.preferenceService.allowWebrtcIpv6()) {
			final int prevSize = data.getCandidates().length;
			data.filter(candidate -> !SdpUtil.isIpv6Candidate(candidate.getCandidate()));
			final int newSize = data.getCandidates().length;
			if (newSize < prevSize) {
				logger.info("Ignored {} remote IPv6 candidate (disabled via preferences)", prevSize - newSize);
			}
		}

		// Add or remove candidates
		final IceCandidate[] candidates = SdpUtil.getIceCandidates(data.getCandidates());
		for (IceCandidate candidate : candidates) {
			this.peerConnectionClient.addRemoteIceCandidate(candidate);
		}

		// Log candidates
		logger.info("Added {} VoIP ICE candidate(s):", candidates.length);
		for (IceCandidate candidate : candidates) {
			logger.info("  Incoming candidate: {}", candidate.sdp);
		}
	}

	/**
	 * Show a toast. Runs on the UI thread.
	 */
	@AnyThread
	private void showSingleToast(final String msg, final int length) {
		RuntimeUtil.runOnUiThread(() -> SingleToast.getInstance().showBottom(msg, length));
	}

	public VoipCallService() {
		super();
	}

	//region Error handling / reporting

	// abort call:
	// - with / without user message
	// - with / without error notification

	/**
	 * Abort call due to an error.
	 *
	 * The message(s) will be logged, followed by a disconnect.
	 *
	 * @param userMessage A user facing message that's shown in the post-call toast message.
	 * @param internalMessage An (optional) internal message that's being logged.
	 * @param throwable A (optional) {@link Throwable} that's logged.
	 * @param showErrorNotification If set to true, the message and throwable will be shown as a {@link BackgroundErrorNotification}.
	 */
	@AnyThread
	private synchronized void abortCall(
		@NonNull final String userMessage,
		@Nullable final String internalMessage,
		@Nullable final Throwable throwable,
		boolean showErrorNotification
	) {
		// If internal message is not specified, reuse user message
		final String description = internalMessage != null ? internalMessage : userMessage;

		if (this.voipStateService != null) {
			// Log error
			final long callId = this.voipStateService.getCallState().getCallId();
			if (throwable != null) {
				logger.error(callId + ": Aborting call: " + description, throwable);
			} else {
				logger.error(callId + ": Aborting call: " + description);
			}
		}

		// Update isError
		boolean wasError = this.isError;
		this.isError = true;

		// If an error occurs during initialization of the service, before `startForeground`
		// was called, Android complains:
		//
		//    android.app.RemoteServiceException: Context.startForegroundService()
		//        did not then call Service.startForeground()
		//
		// See https://issuetracker.google.com/issues/76112072#comment36.
		//
		// To avoid this, we start the foreground notification (which will be killed again
		// as soon as we disconnect).
		if (!this.foregroundStarted) {
			this.showInCallNotification(this.callStartedTimeMs, callStartedRealtimeMs);
		}

		RuntimeUtil.runOnUiThread(() -> {
			// If desired, show an error notification (but ensure that only
			// one notification is generated by checking `wasError`)
			if (showErrorNotification && !wasError) {
				BackgroundErrorNotification.showNotification(
					getAppContext(),
					getString(R.string.voip_error_call),
					description,
					"VoipCallService",
					true,
					throwable
				);
			}

			// Disconnect
			disconnect(userMessage);
		});
	}

	/**
	 * User message string resource will be resolved.
	 * @see #abortCall(String, String, Throwable, boolean)
	 */
	@AnyThread
	private synchronized void abortCall(
		@StringRes final int userMessage,
		@Nullable final String internalMessage,
		@Nullable final Throwable throwable,
		boolean showErrorNotification
	) {
		this.abortCall(getString(userMessage), internalMessage, throwable, showErrorNotification);
	}

	/**
	 * Throwable defaults to `null`.
	 * @see #abortCall(String, String, Throwable, boolean)
	 */
	@AnyThread
	private synchronized void abortCall(@NonNull final String userMessage, @Nullable final String internalMessage, boolean showErrorNotification) {
		this.abortCall(userMessage, internalMessage, null, showErrorNotification);
	}

	/**
	 * Throwable defaults to `null`.
	 * User message string resource will be resolved.
	 * @see #abortCall(String, String, Throwable, boolean)
	 */
	@AnyThread
	private synchronized void abortCall(@StringRes final int userMessage, @Nullable final String internalMessage, boolean showErrorNotification) {
		this.abortCall(userMessage, internalMessage, null, showErrorNotification);
		if (ConfigUtils.isPlayServicesInstalled(getAppContext())){
			voipStateService.cancelOnWearable(VoipStateService.TYPE_ACTIVITY);
		}
	}

	//endregion

	//region Peer connection events

	// -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
	// Send local peer connection SDP and ICE candidates to remote party.
	// All callbacks are invoked from peer connection client looper thread.

	@Override
	@AnyThread
	public void onLocalDescription(long callId, final SessionDescription sdp) {
		logger.info("{}: onLocalDescription", callId);
		RuntimeUtil.runInAsyncTask(() -> {
			synchronized (VoipCallService.this) {
				final CallStateSnapshot callState = voipStateService.getCallState();
				logger.info("{}: Sending {} in call state {}", callId, sdp.type, callState.getName());
				if (callState.isInitializing() || callState.isRinging()) {
					try {
						if (this.voipStateService.isInitiator() == Boolean.TRUE) {
							this.voipStateService.sendCallOfferMessage(contact, callId, sdp, this.videoEnabled);
						} else {
							this.voipStateService.sendAcceptCallAnswerMessage(contact, callId, sdp, this.videoEnabled);
						}
					} catch (ThreemaException | IllegalArgumentException e) {
						this.abortCall(R.string.voip_error_init_call, "Could not send offer or answer message", e, false);
					}
				} else {
					logger.info("{}: Discarding local description (wrong state)", callId);
				}
			}
		});
	}

	@Override
	@AnyThread
	public void onRemoteDescriptionSet(long callId) {
		logger.info("{}: onRemoteDescriptionSet", callId);

		if (this.peerConnectionClient == null) {
			logger.error("{}: Cannot create answer: peerConnectionClient is not initialized", callId);
			return;
		}

		if (this.voipStateService.isInitiator() == Boolean.FALSE) {
			logger.info("{}: Creating answer...", callId);
			peerConnectionClient.createAnswer();
		}
	}

	/**
	 * Send a local ICE candidates.
	 *
	 * Note: If the callState is not RINGING, INITIALIZING or CALLING,
	 *       then the candidate will be disposed.
	 */
	private void sendIceCandidate(long callId, @NonNull final IceCandidate candidate) {
		try {
			// Make sure we're in a call state where ICE candidates are needed,
			// to prevent a "candidate leak" otherwise.
			final CallStateSnapshot callState = this.voipStateService.getCallState();
			if (!(callState.isRinging() || callState.isInitializing() || callState.isCalling())) {
				logger.info("Disposing ICE candidate, callState is {}", callState.getName());
				return;
			}

			// Log candidate
			logger.info("Sending VoIP ICE candidate: {}", candidate.sdp);

			// Send
			this.voipStateService.sendICECandidatesMessage(contact, callId, new IceCandidate[]{ candidate });
		} catch (ThreemaException | IllegalArgumentException e) {
			logger.error("Could not send ICE candidate", e);
		}
	}

	@Override
	@AnyThread
	public void onIceCandidate(long callId, final IceCandidate candidate) {
		logger.trace("{}: onIceCandidate", callId);

		// Send candidate
		logger.trace("{}: onIceCandidate: {}", callId, candidate.sdp);
		VoipCallService.this.sendIceCandidate(callId, candidate);
	}

	@Override
	@AnyThread
	public void onIceChecking(long callId) {
		logger.info("{}: ICE checking", callId);
		synchronized (this) {
			if (this.peerConnectionClient != null) {
				// Register debug stats collector (fast interval until connected)
				final VoipStats.Builder statsBuilder = new VoipStats.Builder()
					.withSelectedCandidatePair(false)
					.withTransport(true)
					.withCrypto(true)
					.withRtp(true)
					.withTracks(true)
					.withCodecs(false)
					.withCandidatePairs(VoipStats.CandidatePairVariant.OVERVIEW_AND_DETAILED);
				this.peerConnectionClient.unregisterPeriodicStats(this.debugStatsCollector);
				this.debugStatsCollector = new CallStatsCollectorCallback(statsBuilder);
				this.peerConnectionClient.registerPeriodicStats(
					this.debugStatsCollector,
					VoipCallService.LOG_STATS_INTERVAL_MS_CONNECTING
				);
			}
		}
	}

	@Override
	@AnyThread
	public void onIceConnected(long callId) {
		logger.info("{}: ICE connected", callId);
		this.iceConnected = true;
		if (this.iceWasConnected) {
			// If we were previously connected, then the connection problem sound
			// is scheduled or playing right now. Cancel and stop it.
			synchronized (this.iceDisconnectedSoundTimer) {
				if (this.iceDisconnectedSoundTimeout != null) {
					this.iceDisconnectedSoundTimeout.cancel();
					this.iceDisconnectedSoundTimeout = null;
				}
			}
			boolean wasPlaying = this.mediaPlayer != null;
			this.stopLoopingSound(callId);

			// Notify activity about reconnection
			VoipUtil.sendVoipBroadcast(getApplicationContext(), CallActivity.ACTION_RECONNECTED);

			// Play pickup sound
			if (wasPlaying) {
				final boolean played = this.playSound(R.raw.threema_pickup, "pickup");
				if (!played) {
					logger.error("{}: Could not play pickup sound!", callId);
				}
			}
		} else {
			// This is the initial "connected" event
			this.iceWasConnected = true;
			this.callConnected(callId);
			synchronized (this) {
				// Register debug stats collector (slow interval since we're connected)
				if (this.peerConnectionClient != null) {
					final VoipStats.Builder statsBuilder = new VoipStats.Builder()
						.withSelectedCandidatePair(true)
						.withTransport(true)
						.withCrypto(true)
						.withRtp(true)
						.withTracks(true)
						.withCodecs(false)
						.withCandidatePairs(VoipStats.CandidatePairVariant.OVERVIEW);
					this.peerConnectionClient.unregisterPeriodicStats(this.debugStatsCollector);
					this.debugStatsCollector = new CallStatsCollectorCallback(statsBuilder);
					this.peerConnectionClient.registerPeriodicStats(this.debugStatsCollector, VoipCallService.LOG_STATS_INTERVAL_MS_CONNECTED);
					if (this.videoEnabled) {
						this.frameDetector = new FrameDetectorCallback(
							this.remoteVideoStateDetector::onIncomingVideoFramesStarted,
							this.remoteVideoStateDetector::onIncomingVideoFramesStopped
						);
						this.peerConnectionClient.registerPeriodicStats(this.frameDetector, VoipCallService.FRAME_DETECTOR_QUERY_INTERVAL_MS);
					}
				}
			}
		}
	}

	@Override
	@AnyThread
	public void onIceDisconnected(final long callId) {
		// ICE was disconnected. This can be a real closing of the connection,
		// or just a temporary connectivity issue that can be recovered.
		logger.info("{}: ICE disconnected", callId);
		this.iceConnected = false;

		// Notify activity about connectivity problems
		VoipUtil.sendVoipBroadcast(getApplicationContext(), CallActivity.ACTION_RECONNECTING);

		// Start problem sound with some delay
		synchronized (this.iceDisconnectedSoundTimer) {
			this.iceDisconnectedSoundTimeout = new TimerTask() {
				@Override
				public void run() {
					VoipCallService.this.startLoopingSound(callId, R.raw.threema_problem, "problem");
					VoipCallService.this.iceDisconnectedSoundTimeout = null;
				}
			};
			this.iceDisconnectedSoundTimer.schedule(this.iceDisconnectedSoundTimeout, ICE_DISCONNECTED_SOUND_TIMEOUT_MS);
		}
	}

	@Override
	public void onIceFailed(long callId) {
		logger.warn("{}: ICE failed", callId);
		this.iceConnected = false;

		if (this.iceWasConnected) {
			// If we were previously connected, this means that the connection was closed.
			RuntimeUtil.runOnUiThread(() -> VoipCallService.this.disconnect(getString(R.string.voip_connection_lost)));
		} else {
			// Otherwise we could never establish a connection in the first place.
			VoipUtil.sendVoipBroadcast(getApplicationContext(), CallActivity.ACTION_CONNECTING_FAILED);

			// Send hangup message to notify peer that the connection attempt was aborted
			if (contact != null) {
				try {
					this.voipStateService.sendCallHangupMessage(contact, callId);
				} catch (ThreemaException e) {
					logger.error(callId + ": Could not send hangup message", e);
				}
			}

			// Play problem sound and disconnect
			final boolean played = playSound(
				R.raw.threema_problem,
				"problem",
				() -> RuntimeUtil.runOnUiThread(
					() -> VoipCallService.this.disconnect(getString(R.string.voip_connection_failed))
				)
			);
			if (!played) {
				logger.error("{}: Could not play problem sound!", callId);
			}
		}
	}

	@Override
	public void onIceGatheringStateChange(long callId, PeerConnection.IceGatheringState newState) {
		logger.trace("{}: onIceGatheringStateChange", callId);
	}

	@Override
	@AnyThread
	public void onPeerConnectionClosed(long callId) {
		logger.trace("{}: onPeerConnectionClosed", callId);
		logger.info("{}: Peer connection closed", callId);

		// Play disconnect sound
		final boolean played = this.playSound(R.raw.threema_hangup, "disconnect");
		if (!played) {
			logger.error("{}: Could not play disconnect sound!", callId);
		}

		// Call disconnect method
		RuntimeUtil.runOnUiThread(VoipCallService.this::disconnect);
	}

	@Override
	@AnyThread
	public void onError(long callId, final @NonNull String description, boolean abortCall) {
		// An error occurred in the peer connection. If the `abortCall` flag is set, we should
		// abort the call.
		if (abortCall) {
			this.abortCall(
				"Peer connection error: " + description,
				callId + ": " + description,
				false
			);
		}
	}

	@Override
	@WorkerThread
	public void onSignalingMessage(long callId, @NonNull CallSignaling.Envelope envelope) {
		if (envelope.hasCaptureStateChange()) {
			this.handleCaptureStateChange(envelope.getCaptureStateChange());
		} else if (envelope.hasVideoQualityProfile()) {
			this.handleVideoQualityProfileChange(envelope.getVideoQualityProfile());
		} else {
			logger.warn("{}: onSignalingMessage: Unknown envelope variant", callId);
		}
	}

	@Override
	public void onCameraFirstFrameAvailable() {
		// This event is triggered if the capturing camera reports the first available frame.
		// In general, we track the capturing state using the `isCapturing` variable, but in case
		// of a bug, there may be a mismatch between the assumed capturing state and the actual
		// capturing state. Therefore, to be on the safe side, this even will always override the
		// assumed capturing state by sending a "outgoing camera started" broadcast if the event
		// is received when the capturing state is assumed to be off.
		new Thread(() -> { // Start a thread to reduce the chance for deadlocks.
			synchronized (this.capturingLock) {
				if (!this.isCapturing) {
					logger.error("WARNING: Received 'onCameraFirstFrameAvailable' event even though capturing should be off!");
					VoipUtil.sendVoipBroadcast(
						getAppContext(),
						CallActivity.ACTION_OUTGOING_VIDEO_STARTED
					);
				}
			}
		}).start();
	}

	//endregion

	//region Sound playback

	private interface OnSoundComplete {
		void onComplete();
	}

	/**
	 * Instantiate and start the looping sound media player.
	 */
	@AnyThread
	private synchronized void startLoopingSound(long callId,
	                                            int rawResource,
	                                            final String soundName) {
		if (this.mediaPlayer != null) {
			logger.error("{}: Not playing {} sound, mediaPlayer is not null!", callId, soundName);
			return;
		}
		logger.info("{}: Playing {} sound...", callId, soundName);

		// Initialize media player
		this.mediaPlayer = new MediaPlayerStateWrapper();
		this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
		this.mediaPlayer.setLooping(true);

		// Load and play resource
		AssetFileDescriptor afd = null;
		try {
			afd = getResources().openRawResourceFd(rawResource);
			this.mediaPlayer.setDataSource(afd);
			this.mediaPlayer.prepare();
			this.mediaPlayer.start();
		} catch (Exception e) {
			logger.error("I/O Error", e);
			if (this.mediaPlayer != null) {
				this.mediaPlayer.release();
				this.mediaPlayer = null;
			}
		} finally {
			if (afd != null) {
				try {
					afd.close();
				} catch (IOException e) {
					//
				}
			}
		}
	}

	/**
	 * Stop the currently playing looping sound.
	 */
	@AnyThread
	private synchronized void stopLoopingSound(long callId) {
		if (this.mediaPlayer != null) {
			logger.info("{}: Stopping ringing tone...", callId);
			this.mediaPlayer.stop();
			this.mediaPlayer.release();
		}
		this.mediaPlayer = null;
	}

	/**
	 * Play a one-time sound.
	 */
	@AnyThread
	private synchronized boolean playSound(int rawResource,
	                                       final String soundName,
	                                       @Nullable final OnSoundComplete onComplete) {
		logger.info("Playing {} sound...", soundName);

		// Initialize media player
		final MediaPlayerStateWrapper soundPlayer = new MediaPlayerStateWrapper();
		soundPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
		soundPlayer.setLooping(false);

		// Load and play resource
		AssetFileDescriptor afd = null;
		try {
			afd = getResources().openRawResourceFd(rawResource);
			soundPlayer.setDataSource(afd);
			soundPlayer.prepare();
		} catch (IOException e) {
			logger.error("Could not play " + soundName + " sound", e);
			soundPlayer.release();
			return false;
		} finally {
			if (afd != null) {
				try {
					afd.close();
				} catch (IOException e) {
					//
				}
			}
		}

		soundPlayer.setStateListener(new MediaPlayerStateWrapper.StateListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				mp.release();
				if (onComplete != null) {
					onComplete.onComplete();
				}
			}

			@Override
			public void onPrepared(MediaPlayer mp) {}
		});

		soundPlayer.start();

		return true;
	}

	@AnyThread
	private synchronized boolean playSound(final int rawResource, final String soundName) {
		return this.playSound(rawResource, soundName, null);
	}

	//endregion

	//region Notifications

	/**
	 * Show the ongoing notification that is shown as long as the call is active.
	 * @param callStartedTimeMs Timestamp at which the call was started (wall time).
	 * @param elapsedTimeMs Timestamp at which the call was started (elapsed monotonic time since boot).
	 */
	private synchronized void showInCallNotification(long callStartedTimeMs, long elapsedTimeMs) {
		logger.trace("showInCallNotification");

		// Prepare hangup action
		final Intent hangupIntent = new Intent(this, VoipCallService.class);
		hangupIntent.setAction(ACTION_HANGUP);
		final PendingIntent hangupPendingIntent = PendingIntent.getService(
			this,
			(int)System.currentTimeMillis(),
			hangupIntent,
			PendingIntent.FLAG_UPDATE_CURRENT);

		// Prepare open action
		final Intent openIntent = new Intent(this, CallActivity.class);
		openIntent.putExtra(EXTRA_ACTIVITY_MODE, CallActivity.MODE_ACTIVE_CALL);
		openIntent.putExtra(EXTRA_CONTACT_IDENTITY, contact.getIdentity());
		openIntent.putExtra(EXTRA_START_TIME, elapsedTimeMs);
		final PendingIntent openPendingIntent = PendingIntent.getActivity(
				this,
				(int)System.currentTimeMillis(),
				openIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		// Prepare notification
		final NotificationCompat.Builder notificationBuilder = new NotificationBuilderWrapper(this, NotificationService.NOTIFICATION_CHANNEL_IN_CALL, null)
				.setContentTitle(NameUtil.getDisplayNameOrNickname(contact, true))
				.setContentText(getString(R.string.voip_title))
				.setColor(getResources().getColor(R.color.accent_light))
				.setLocalOnly(true)
				.setOngoing(true)
				.setUsesChronometer(true)
				.setWhen(callStartedTimeMs)
				.setSmallIcon(R.drawable.ic_phone_locked_white_24dp)
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.setContentIntent(openPendingIntent)
				.addAction(R.drawable.ic_call_end_grey600_24dp, getString(R.string.voip_hangup), hangupPendingIntent);

		final Bitmap avatar = contactService.getAvatar(contact, false);
		notificationBuilder.setLargeIcon(avatar);
		Notification notification = notificationBuilder.build();
		notification.flags |= NotificationCompat.FLAG_NO_CLEAR | NotificationCompat.FLAG_ONGOING_EVENT;

		// Launch notification
		this.foregroundStarted = true;
		startForeground(INCALL_NOTIFICATION_ID, notification);

		//call listener
		ListenerManager.voipCallListeners.handle(listener -> listener.onStart(contact.getIdentity(), elapsedTimeMs));
	}

	private void cancelInCallNotification() {
		if (notificationManager != null) {
			notificationManager.cancel(INCALL_NOTIFICATION_ID);
			//call listener
			ListenerManager.voipCallListeners.handle(listener -> listener.onEnd());
		}
	}

	//endregion

	//region Video capturing

	/**
	 * Start capturing (asynchronously).
	 */
	@AnyThread
	private void startCapturing() {
		new Thread(() -> {
			if (this.peerConnectionClient != null) {
				try (CloseableLock ignored = this.videoQualityNegotiation.read()) {
					synchronized (this.capturingLock) {
						// Start capturing
						final VideoCapturer videoCapturer = this.peerConnectionClient.startCapturing(
							this.commonVideoQualityProfile
						);
						this.isCapturing = true;

						// Query cameras
						if (videoCapturer instanceof CameraVideoCapturer) {
							final VideoContext videoContext = this.voipStateService.getVideoContext();
							if (videoContext != null) {
								Pair<String,String> primaryCameraNames = VideoCapturerUtil.getPrimaryCameraNames(getAppContext());
								videoContext.setFrontCameraName(primaryCameraNames.first);
								videoContext.setBackCameraName(primaryCameraNames.second);
								videoContext.setCameraVideoCapturer((CameraVideoCapturer) videoCapturer);
							}
						}

						// Notify listeners
						VoipUtil.sendVoipBroadcast(getAppContext(), CallActivity.ACTION_OUTGOING_VIDEO_STARTED);
					}
				}
			}
		}, "StartCapturingThread").start();
	}

	/**
	 * Stop capturing (asynchronously).
	 */
	@AnyThread
	private void stopCapturing() {
		new Thread(() -> {
			if (peerConnectionClient != null) {
				synchronized (this.capturingLock) {
					peerConnectionClient.stopCapturing();
					this.isCapturing = false;
					VoipUtil.sendVoipBroadcast(getAppContext(), CallActivity.ACTION_OUTGOING_VIDEO_STOPPED);
				}
			}
		}, "StopCapturingThread").start();
	}

	/**
	 * Update our own video quality profile, change outgoing video parameters
	 * and notify the peer about this change.
	 */
	@AnyThread
	private void updateOwnVideoQualityProfile(
		boolean networkIsMetered,
		boolean networkIsRelayed
	) {
		logger.debug("updateOwnVideoQualityProfile: metered={} relayed={}", networkIsMetered, networkIsRelayed);
		try (CloseableLock ignored = this.videoQualityNegotiation.write()) {
			// Get own params from settings
			final VoipVideoParams ownParams = VoipVideoParams.getParamsFromSetting(
				preferenceService.getVideoCallsProfile(),
				networkIsMetered
			);
			this.localVideoQualityProfile = ownParams;
			if (this.commonVideoQualityProfile == null) {
				this.commonVideoQualityProfile = ownParams;
			}
			if (this.peerConnectionClient != null) {
				// Notify peer about profile change
				// Note: When changing the local video quality profile before the peer connection
				//       was created, there will not yet be a data channel so signaling messages
				//       cannot be sent. However, this is no a problem since the local quality
				//       profile will be sent in `callConnected()`. That means that all quality
				//       profile signaling messages in states other than CALLING can be skipped.
				if (this.voipStateService.getCallState().isCalling()) {
					this.peerConnectionClient.sendSignalingMessage(ownParams);
				}

				// Adjust outgoing video stream
				try {
					final VoipVideoParams common = ownParams.findCommonProfile(
						this.remoteVideoQualityProfile,
						networkIsRelayed
					);
					this.commonVideoQualityProfile = common;
					synchronized (this.capturingLock) {
						try {
							this.peerConnectionClient.changeOutgoingVideoParams(common);
						} catch (NullPointerException e) {
							// This race condition can happen in rare cases if the peerConnectionClient
							// has been discarded since the last null check. Ignore it.
						}
					}
				} catch (RuntimeException e) {
					this.abortCall("Could not determine common video quality profile", null, e, true);
				}
			}
		}
	}

	/**
	 * Update the peer video quality profile and change outgoing video parameters.
	 */
	@AnyThread
	private void updatePeerVideoQualityProfile(@NonNull VoipVideoParams peerParams) {
		try (CloseableLock ignored = this.videoQualityNegotiation.write()) {
			this.remoteVideoQualityProfile = peerParams;
			if (this.peerConnectionClient != null) {
				// Adjust outgoing video stream
				try {
					final VoipVideoParams common = peerParams.findCommonProfile(
						this.localVideoQualityProfile,
						this.networkIsRelayed
					);
					this.commonVideoQualityProfile = common;
					synchronized (this.capturingLock) {
						try {
							this.peerConnectionClient.changeOutgoingVideoParams(common);
						} catch (NullPointerException e) {
							// This race condition can happen in rare cases if the peerConnectionClient
							// has been discarded since the last null check. Ignore it.
						}
					}
				} catch (RuntimeException e) {
					this.abortCall("Could not determine common video quality profile", null, e, true);
				}
			}
		}
	}

	/**
	 * Switch between front- and back-camera.
	 */
	private void switchCamera() {
		if (switchCamInProgress.get()) {
			logger.debug("Ignoring camera switch request, already in progress");
			return;
		}

		synchronized (this.capturingLock) {
			final CameraVideoCapturer capturer = this.voipStateService.getVideoContext().getCameraVideoCapturer();
			if (capturer == null) {
				logger.debug("Ignoring camera switch request, no capturer initialized");
				return;
			}
			logger.debug("Switching camera");
			switchCamInProgress.set(true);

			final @VideoContext.CameraOrientation int newCameraOrientation;
			final String newCameraName;
			if (this.voipStateService.getVideoContext().getCameraOrientation() == CAMERA_FRONT) {
				newCameraOrientation = CAMERA_BACK;
				newCameraName = this.voipStateService.getVideoContext().getBackCameraName();
			} else {
				newCameraOrientation = CAMERA_FRONT;
				newCameraName = this.voipStateService.getVideoContext().getFrontCameraName();
			}

			if (newCameraName == null) {
				logger.debug("Ignoring camera switch request, no camera with orientation='{}'", newCameraOrientation);
				return;
			}

			capturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
				@Override
				public void onCameraSwitchDone(boolean isFront) {
					voipStateService.getVideoContext().setCameraOrientation(newCameraOrientation);

					logger.info("Switched camera to {}", isFront ? "front cam" : "rear cam");

					VoipUtil.sendVoipBroadcast(getApplicationContext(), CallActivity.ACTION_CAMERA_CHANGED);

					Toast.makeText(
						getAppContext(),
						isFront ? R.string.voip_switch_cam_front : R.string.voip_switch_cam_rear,
						Toast.LENGTH_SHORT
					).show();
					this.resetInProgress();
				}

				@Override
				public void onCameraSwitchError(String s) {
					logger.info("Error while switching camera: {}", s);
					this.resetInProgress();
				}

				private void resetInProgress() {
					switchCamInProgress.set(false);
				}
			}, newCameraName);
		}
	}

	//endregion

	//region Remote video state

	private final @NonNull RemoteVideoStateDetector remoteVideoStateDetector
		= new RemoteVideoStateDetector(this::getApplicationContext);

	/**
	 * This class handles remote video state changes (combining the
	 * information from the capturing state signaling messages and
	 * the frame detector).
	 *
	 * The ACTION_INCOMING_VIDEO_STARTED and ACTION_INCOMING_VIDEO_STOPPED broadcasts
	 * should never be sent outside this class.
	 */
	private static class RemoteVideoStateDetector {
		private final @NonNull Supplier<Context> appContextSupplier;

		// Never access these variables from non-synchronized
		// methods to avoid data races!
		private volatile boolean incomingVideoFrames = false;
		private volatile boolean incomingVideoSignaled = false;
		private volatile boolean incomingVideo = false;

		RemoteVideoStateDetector(@NonNull Supplier<Context> appContextSupplier) {
			this.appContextSupplier = appContextSupplier;
		}

		/**
		 * Called by the {@link #frameDetector}. Remote has started sending video frames.
		 *
		 * Notify application if {@link #incomingVideo} was false.
		 */
		synchronized void onIncomingVideoFramesStarted() {
			this.incomingVideoFrames = true;
			if (!this.incomingVideo) { // Incoming video was false
				this.incomingVideo = true;
				logger.info("Incoming video started (reason: frames)");
				VoipUtil.sendVoipBroadcast(
					this.appContextSupplier.get(),
					CallActivity.ACTION_INCOMING_VIDEO_STARTED
				);
			}
		}

		/**
		 * Called by the {@link #frameDetector}. Remote has stopped sending video frames.
		 *
		 * Notify application if {@link #incomingVideo} was false.
		 */
		synchronized void onIncomingVideoFramesStopped() {
			this.incomingVideoFrames = false;
			if (this.incomingVideo) { // Incoming video was true...
				if (!this.incomingVideoSignaled) { // ...due to the frame detector
					this.incomingVideo = false;
					logger.info("Incoming video stopped (reason: frames)");
					VoipUtil.sendVoipBroadcast(
						this.appContextSupplier.get(),
						CallActivity.ACTION_INCOMING_VIDEO_STOPPED
					);
				}
				// Otherwise the remote still signals an enabled camera, so ignore the transition.
			}
		}

		/**
		 * Called by {@link #handleCaptureStateChange(CallSignaling.CaptureState)}.
		 * Remote has signaled that video capturing has started.
		 */
		synchronized void onRemoteVideoCapturingEnabled() {
			this.incomingVideoSignaled = true;
			if (!this.incomingVideo) {
				// Signaling always results in the video being shown
				this.incomingVideo = true;
				logger.info("Incoming video started (reason: signaling)");
				VoipUtil.sendVoipBroadcast(
					this.appContextSupplier.get(),
					CallActivity.ACTION_INCOMING_VIDEO_STARTED
				);
			}
		}

		/**
		 * Called by {@link #handleCaptureStateChange(CallSignaling.CaptureState)}.
		 * Remote has signaled that video capturing has stopped.
		 */
		synchronized void onRemoteVideoCapturingDisabled() {
			this.incomingVideoSignaled = false;
			if (this.incomingVideo) { // Incoming video was true...
				if (!this.incomingVideoFrames) { // ...due to the signaling state
					this.incomingVideo = false;
					logger.info("Incoming video stopped (reason: signaling)");
					VoipUtil.sendVoipBroadcast(
						this.appContextSupplier.get(),
						CallActivity.ACTION_INCOMING_VIDEO_STOPPED
					);
				}
			}
		}
	}

	//endregion

	//region Listeners

	private VoipAudioManagerListener audioManagerListener = new VoipAudioManagerListener() {
		@Override
		public void onAudioFocusLost(boolean temporary) {
			// WARNING: This method is currently not being called,
			// see commit ff68bb215c8e55f03b75128ebb40ae423585c5d9.

			logger.info("Audio focus lost. Transient = " + temporary);

			if (temporary) {
				if (peerConnectionClient != null) {
					peerConnectionClient.setLocalAudioTrackEnabled(false);
					peerConnectionClient.setRemoteAudioTrackEnabled(false);
					showSingleToast(getAppContext().getString(R.string.audio_mute_due_to_focus_loss), Toast.LENGTH_LONG);
				}
			} else {
				// lost forever - disconnect
				BackgroundErrorNotification.showNotification(
					getAppContext(),
					R.string.audio_focus_loss,
					R.string.audio_focus_loss_complete,
					"VoipCallService",
					false,
					null
				);
				RuntimeUtil.runOnUiThread(() -> disconnect("Audio Focus lost"));
			}
		}

		@Override
		public void onAudioFocusGained() {
			logger.info("Audio focus gained");
			if (peerConnectionClient != null) {
				peerConnectionClient.setLocalAudioTrackEnabled(micEnabled);
				peerConnectionClient.setRemoteAudioTrackEnabled(true);
			}
		}
	};

	private class PSTNCallStateListener extends PhoneStateListener {
		@Override
		public void onCallStateChanged(int state, String phoneNumber) {
			super.onCallStateChanged(state, phoneNumber);
			if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
				Toast.makeText(getAppContext(), R.string.voip_another_pstn_call, Toast.LENGTH_LONG).show();
				onCallHangUp();
				logger.info("hanging up due to regular phone call");
			}
		}
	}

	//endregion

	//region Signaling message handlers

	/**
	 * The call partner enabled or disabled capturing for a device.
	 *
	 * @param captureStateChange The received signaling message.
	 */
	@AnyThread
	private void handleCaptureStateChange(@NonNull CallSignaling.CaptureState captureStateChange) {
		logger.info(
			"Signaling: Call partner changed {} capturing state to {}",
			captureStateChange.getDevice(),
			captureStateChange.getState()
		);

		// Handle camera capturing state changes
		if (CallSignaling.CaptureState.CaptureDevice.CAMERA == captureStateChange.getDevice()) {
			switch (captureStateChange.getState()) {
				case ON:
					this.remoteVideoStateDetector.onRemoteVideoCapturingEnabled();
					break;
				case OFF:
					this.remoteVideoStateDetector.onRemoteVideoCapturingDisabled();
					break;
				default:
					logger.warn("Unknown capture state received");
			}
		}
	}

	/**
	 * The call partner changed the video quality profile.
	 *
	 * @param videoQualityProfile The received signaling message.
	 */
	@AnyThread
	private void handleVideoQualityProfileChange(@NonNull CallSignaling.VideoQualityProfile videoQualityProfile) {
		logger.info("Signaling: Call partner changed video profile to {}", videoQualityProfile.getProfile());

		final VoipVideoParams profile = VoipVideoParams.fromSignalingMessage(videoQualityProfile);
		if (profile != null) {
			this.updatePeerVideoQualityProfile(profile);
		}
	}

	//endregion
}
