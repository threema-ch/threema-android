/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

package ch.threema.app;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StrictMode;
import android.provider.ContactsContract;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDexApplication;
import androidx.preference.PreferenceManager;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.datatheorem.android.trustkit.TrustKit;
import com.datatheorem.android.trustkit.reporting.BackgroundReporter;
import com.mapbox.mapboxsdk.Mapbox;

import net.sqlcipher.database.SQLiteException;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.exceptions.DatabaseMigrationFailedException;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.grouplinks.IncomingGroupJoinRequestListener;
import ch.threema.app.jobs.WorkSyncJobService;
import ch.threema.app.jobs.WorkSyncService;
import ch.threema.app.listeners.BallotVoteListener;
import ch.threema.app.listeners.ContactListener;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.listeners.ContactTypingListener;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.listeners.DistributionListListener;
import ch.threema.app.listeners.GroupListener;
import ch.threema.app.listeners.NewSyncedContactsListener;
import ch.threema.app.listeners.ServerMessageListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.ContactMessageReceiver;
import ch.threema.app.messagereceiver.GroupMessageReceiver;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.push.PushService;
import ch.threema.app.receivers.ConnectivityChangeReceiver;
import ch.threema.app.receivers.PinningFailureReportBroadcastReceiver;
import ch.threema.app.receivers.RestrictBackgroundChangedReceiver;
import ch.threema.app.routines.OnFirstConnectRoutine;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.AppRestrictionService;
import ch.threema.app.services.AvatarCacheService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.services.ThreemaPushService;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.services.UpdateSystemServiceImpl;
import ch.threema.app.services.UserService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.stores.IdentityStore;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ConnectionIndicatorUtil;
import ch.threema.app.utils.ConversationNotificationUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LinuxSecureRandom;
import ch.threema.app.utils.LoggingUEH;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.utils.WidgetUtil;
import ch.threema.app.utils.WorkManagerUtil;
import ch.threema.app.voip.Config;
import ch.threema.app.voip.listeners.VoipCallEventListener;
import ch.threema.app.voip.managers.VoipListenerManager;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.listeners.WebClientWakeUpListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.SessionAndroidService;
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.app.workers.IdentityStatesWorker;
import ch.threema.app.workers.ShareTargetUpdateWorker;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.base.utils.Utils;
import ch.threema.domain.models.AppVersion;
import ch.threema.domain.protocol.csp.connection.ConnectionState;
import ch.threema.domain.protocol.csp.connection.ConnectionStateListener;
import ch.threema.domain.protocol.csp.connection.ThreemaConnection;
import ch.threema.localcrypto.MasterKey;
import ch.threema.localcrypto.MasterKeyLockedException;
import ch.threema.logging.backend.DebugLogFileBackend;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.NonceDatabaseBlobService;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.ConversationModel;
import ch.threema.storage.models.DistributionListModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.ServerMessageModel;
import ch.threema.storage.models.WebClientSessionModel;
import ch.threema.storage.models.ballot.BallotModel;
import ch.threema.storage.models.ballot.GroupBallotModel;
import ch.threema.storage.models.ballot.IdentityBallotModel;
import ch.threema.storage.models.ballot.LinkBallotModel;
import ch.threema.storage.models.data.status.VoipStatusDataModel;
import ch.threema.storage.models.group.IncomingGroupJoinRequestModel;

import static android.app.NotificationManager.ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED;
import static android.app.NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED;
import static android.app.NotificationManager.EXTRA_BLOCKED_STATE;
import static android.app.NotificationManager.EXTRA_NOTIFICATION_CHANNEL_GROUP_ID;
import static ch.threema.app.services.PreferenceService.Theme_DARK;
import static ch.threema.app.services.PreferenceService.Theme_LIGHT;

public class ThreemaApplication extends MultiDexApplication implements DefaultLifecycleObserver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ThreemaApplication");

	public static final String INTENT_DATA_CONTACT = "identity";
	public static final String INTENT_DATA_CONTACT_READONLY = "readonly";
	public static final String INTENT_DATA_TEXT = "text";
	public static final String INTENT_DATA_ID_BACKUP = "idbackup";
	public static final String INTENT_DATA_ID_BACKUP_PW = "idbackuppw";
	public static final String INTENT_DATA_PASSPHRASE_CHECK = "check";
	public static final String INTENT_DATA_IS_FORWARD = "is_forward";
	public static final String INTENT_DATA_TIMESTAMP = "timestamp";
	public static final String INTENT_DATA_EDITFOCUS = "editfocus";
	public static final String INTENT_DATA_GROUP = "group";
	public static final String INTENT_DATA_GROUP_API = "group_api";
	public static final String INTENT_DATA_GROUP_LINK = "group_link";
	public static final String INTENT_DATA_DISTRIBUTION_LIST = "distribution_list";
	public static final String INTENT_DATA_ARCHIVE_FILTER = "archiveFilter";
	public static final String INTENT_DATA_QRCODE = "qrcodestring";
	public static final String INTENT_DATA_QRCODE_TYPE_OK = "qrcodetypeok";
	public static final String INTENT_DATA_MESSAGE_ID = "messageid";
	public static final String INTENT_DATA_INCOMING_GROUP_REQUEST = "groupRequest";
	public static final String INTENT_DATA_GROUP_REQUEST_NOTIFICATION_ID = "groupRequestNotificationId";
	public static final String EXTRA_VOICE_REPLY = "voicereply";
	public static final String EXTRA_OUTPUT_FILE = "output";
	public static final String EXTRA_ORIENTATION = "rotate";
	public static final String EXTRA_FLIP = "flip";
	public static final String EXTRA_EXIF_ORIENTATION = "rotateExif";
	public static final String EXTRA_EXIF_FLIP = "flipExif";
	public static final String INTENT_DATA_CHECK_ONLY = "check";
	public static final String INTENT_DATA_ANIM_CENTER = "itemPos";
	public static final String INTENT_DATA_PICK_FROM_CAMERA = "useCam";
	public static final String INTENT_PUSH_REGISTRATION_COMPLETE = "registrationComplete";
	public static final String INTENT_DATA_PIN = "ppin";
	public static final String INTENT_DATA_HIDE_RECENTS = "hiderec";
	public static final String INTENT_ACTION_FORWARD = "ch.threema.app.intent.FORWARD";

	public static final String CONFIRM_TAG_CLOSE_BALLOT = "cb";

	// Notification IDs
	public static final int PASSPHRASE_SERVICE_NOTIFICATION_ID = 587;
	public static final int NEW_MESSAGE_NOTIFICATION_ID = 723;
	public static final int MASTER_KEY_LOCKED_NOTIFICATION_ID = 724;
	public static final int NEW_MESSAGE_LOCKED_NOTIFICATION_ID = 725;
	public static final int NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID = 726;
	public static final int SAFE_FAILED_NOTIFICATION_ID = 727;
	public static final int SERVER_MESSAGE_NOTIFICATION_ID = 730;
	public static final int NOT_ENOUGH_DISK_SPACE_NOTIFICATION_ID = 731;
	public static final int UNSENT_MESSAGE_NOTIFICATION_ID = 732;
	public static final int NETWORK_BLOCKED_NOTIFICATION_ID = 733;
	public static final int WORK_SYNC_NOTIFICATION_ID = 735;
	public static final int NEW_SYNCED_CONTACTS_NOTIFICATION_ID = 736;
	public static final int WEB_RESUME_FAILED_NOTIFICATION_ID = 737;
	public static final int IDENTITY_SYNC_NOTIFICATION_ID = 748;
	public static final int INCOMING_CALL_NOTIFICATION_ID = 800;
	public static final int GROUP_RESPONSE_NOTIFICATION_ID = 801;
	public static final int GROUP_REQUEST_NOTIFICATION_ID = 802;

	private static final String THREEMA_APPLICATION_LISTENER_TAG = "al";
	public static final String AES_KEY_FILE = "key.dat";
	public static final String ECHO_USER_IDENTITY = "ECHOECHO";
	public static final String PHONE_LINKED_PLACEHOLDER = "***";
	public static final String EMAIL_LINKED_PLACEHOLDER = "***@***";

	private static final String ACTIVITY_CONNECTION_TAG = "threemaApplication";
	private static final long ACTIVITY_CONNECTION_LIFETIME = 60000;

	public static final int MAX_BLOB_SIZE_MB = 100;
	public static final int MAX_BLOB_SIZE = MAX_BLOB_SIZE_MB * 1024 * 1024;
	public static final int MIN_PIN_LENGTH = 4;
	public static final int MAX_PIN_LENGTH = 8;
	public static final int MIN_GROUP_MEMBERS_COUNT = 1;
	public static final int MIN_PW_LENGTH_BACKUP = 8;
	public static final int MAX_PW_LENGTH_BACKUP = 256;
	public static final int MIN_PW_LENGTH_ID_EXPORT_LEGACY = 4; // extremely ancient versions of the app on some platform accepted four-letter passwords when generating ID exports

	private static final int WORK_SYNC_JOB_ID = 63339;

	private static final String WORKER_IDENTITY_STATES_PERIODIC_NAME = "IdentityStates";
	public static final String WORKER_SHARE_TARGET_UPDATE = "ShareTargetUpdate";
	private static final String EXIT_REASON_LOGGING_TIMESTAMP = "exit_reason_timestamp";

	private static Context context;

	private static volatile ServiceManager serviceManager;
	private static volatile AppVersion appVersion;
	private static volatile MasterKey masterKey;

	private static Date lastLoggedIn;
	private static boolean isDeviceIdle;
	private static boolean ipv6 = false;
	private static HashMap<String, String> messageDrafts = new HashMap<>();
	private static HashMap<String, String> quoteDrafts = new HashMap<>();

	public static final ExecutorService sendMessageExecutorService = Executors.newFixedThreadPool(4);
	public static final ExecutorService sendMessageSingleThreadExecutorService = Executors.newSingleThreadExecutor();
	public static final ExecutorService voiceMessageThumbnailExecutorService = Executors.newFixedThreadPool(4);

	private static boolean checkAppReplacingState(Context context) {
		// workaround https://code.google.com/p/android/issues/detail?id=56296
		if (context.getResources() == null) {
			logger.debug("App is currently installing. Killing it.");
			android.os.Process.killProcess(android.os.Process.myPid());

			return false;
		}

		return true;
	}

	private void logStackTrace(StackTraceElement[] stackTraceElements) {
		for (int i = 1; i < stackTraceElements.length; i++) {
			logger.info("\tat " + stackTraceElements[i]);
		}
	}

	private static void showNotesGroupNotice(GroupModel groupModel, @GroupService.GroupState int oldState, @GroupService.GroupState int newState) {
		if (oldState != newState) {
			try {
				GroupService groupService = serviceManager.getGroupService();
				MessageService messageService = serviceManager.getMessageService();
				if (groupService != null && messageService != null) {
					String notice = null;

					if (newState == GroupService.NOTES) {
						notice = serviceManager.getContext().getString(R.string.status_create_notes);
					} else if (newState == GroupService.PEOPLE && oldState != GroupService.UNDEFINED) {
						notice = serviceManager.getContext().getString(R.string.status_create_notes_off);
					}

					if (notice != null) {
						messageService.createStatusMessage(notice, groupService.createReceiver(groupModel));
					}
				}
			} catch (ThreemaException e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	public void onCreate() {
		if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
				.detectLeakedSqlLiteObjects()
				.detectLeakedClosableObjects()
				.penaltyLog()
				.penaltyListener(Executors.newSingleThreadExecutor(), v -> {
					logger.info("STRICTMODE VMPolicy: " + v.getCause());
					logStackTrace(v.getStackTrace());
				})
				.build());
/*
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
				.detectAll()   // or .detectAll() for all detectable problems
//				.penaltyFlashScreen()
				.penaltyListener(Executors.newSingleThreadExecutor(), v -> {
					logger.info("STRICTMODE ThreadPolicy: " + v.getCause());
					logStackTrace(v.getStackTrace());
				})
				.build());
*/		}

		super.onCreate();

		// always log database migration
		setupLogging(null);

		AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

		context = getApplicationContext();

		if (!checkAppReplacingState(context)) {
			return;
		}

		// Initialize TrustKit for CA pinning
		if (!ConfigUtils.isOnPremBuild()) {
			TrustKit.initializeWithNetworkSecurityConfiguration(this);
		}

		// Set unhandled exception logger
		LoggingUEH loggingUEH = new LoggingUEH(getAppContext());
		loggingUEH.setRunOnUncaughtException(() -> {
			// Hack: must delete message queue file if we crash, or else we might get into a loop
			// if the message queue contents caused the crash.
			final File messageQueueFile = new File(
				getAppContext().getFilesDir(),
				MessageServiceImpl.MESSAGE_QUEUE_SAVE_FILE
			);
			FileUtil.deleteFileOrWarn(
				messageQueueFile,
				"message queue file",
				LoggingUtil.getThreemaLogger("LoggingUEH.runOnUncaughtException")
			);
		});
		Thread.setDefaultUncaughtExceptionHandler(loggingUEH);

		ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

		// Instantiate our own SecureRandom implementation to make sure this gets used everywhere
		new LinuxSecureRandom();

		// Prepare app version object
		appVersion = new AppVersion(
				ConfigUtils.getAppVersion(getAppContext()),
				"A",
				Locale.getDefault().getLanguage(),
				Locale.getDefault().getCountry(),
				Build.MODEL,
				Build.VERSION.RELEASE
		);

		// Create master key
		File filesDir = getAppContext().getFilesDir();
		if (filesDir != null) {
			filesDir.mkdirs();

			if (filesDir.exists() && filesDir.isDirectory()) {
				File masterKeyFile = new File(filesDir, AES_KEY_FILE);

				try {
					boolean reset = !masterKeyFile.exists();

					if (reset) {
						/*
						 *
						 * IMPORTANT
						 *
						 * If the MasterKey file does not exists, remove every file that is encrypted with this
						 * non-existing MasterKey file
						 *
						 * 1. Database
						 * 2. Settings
						 * 3. Message Queue
						 *
						 * TODO: move this into a separate method/file
						 *
						 */
						//remove database, its encrypted with the wrong master key

						logger.info("master key is missing or does not match. rename database files.");

						File databaseFile = getAppContext().getDatabasePath(DatabaseServiceNew.DATABASE_NAME);
						if (databaseFile.exists()) {
							File databaseBackup = new File(databaseFile.getPath() + ".backup");
							if (!databaseFile.renameTo(databaseBackup)) {
								FileUtil.deleteFileOrWarn(databaseFile, "threema database", logger);
							}
						}

						databaseFile = getAppContext().getDatabasePath(DatabaseServiceNew.DATABASE_NAME_V4);
						if (databaseFile.exists()) {
							File databaseBackup = new File(databaseFile.getPath() + ".backup");
							if (!databaseFile.renameTo(databaseBackup)) {
								FileUtil.deleteFileOrWarn(databaseFile, "threema4 database", logger);
							}
						}

						databaseFile = getAppContext().getDatabasePath(NonceDatabaseBlobService.DATABASE_NAME);
						if (databaseFile.exists()) {
							FileUtil.deleteFileOrWarn(databaseFile, "nonce database", logger);
						}

						databaseFile = getAppContext().getDatabasePath(NonceDatabaseBlobService.DATABASE_NAME_V4);
						if (databaseFile.exists()) {
							FileUtil.deleteFileOrWarn(databaseFile, "nonce4 database", logger);
						}

						//remove all settings!
						logger.info("initialize", "remove preferences");
						PreferenceStore preferenceStore = new PreferenceStore(getAppContext(), masterKey);
						preferenceStore.clear();

						//remove message queue
						//TODO: create a static getter for the file
						File messageQueueFile = new File(filesDir, MessageServiceImpl.MESSAGE_QUEUE_SAVE_FILE);
						if (messageQueueFile.exists()) {
							logger.info("remove message queue file");
							FileUtil.deleteFileOrWarn(messageQueueFile, "message queue file", logger);
						}
					} else {
						logger.info("OK, masterKeyFile exists");
					}

					masterKey = new MasterKey(masterKeyFile, null, true);

					if (!masterKey.isLocked()) {
						reset();
					}
				} catch (IOException e) {
					logger.error("IOException", e);
				}

				// Register "Connectivity Action" broadcast receiver.
				// This is called when a change in network connectivity has occurred.
				// Note: This is deprecated on API 28+!
				getAppContext().registerReceiver(
					new ConnectivityChangeReceiver(),
					new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
				);

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					// Register "Restrict Background Changed" broadcast receiver.
					// This is called when a change in the background metered network activity restriction has occurred.
					getAppContext().registerReceiver(
						new RestrictBackgroundChangedReceiver(),
						new IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
					);
				}

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					// Register "Device Idle Mode Changed" broadcast receiver.
					// This is called when the state of isDeviceIdleMode() changes. This broadcast
					// is only sent to registered receivers.
					getAppContext().registerReceiver(new BroadcastReceiver() {
						@TargetApi(Build.VERSION_CODES.M)
						@Override
						public void onReceive(Context context, Intent intent) {
							final PowerManager powerManager = (PowerManager) context
								.getApplicationContext()
								.getSystemService(Context.POWER_SERVICE);
							if (powerManager != null && powerManager.isDeviceIdleMode()) {
								logger.info("*** Device going to deep sleep");

								isDeviceIdle = true;

								try {
									// Pause connection
									serviceManager.getLifetimeService().pause();
								} catch (Exception e) {
									logger.error("Exception while pausing connection", e);
								}

								if (BackupService.isRunning()) {
									context.stopService(new Intent(context, BackupService.class));
								}
							} else {
								logger.info("*** Device waking up");
								try {
									serviceManager.getLifetimeService().unpause();
								} catch (Exception e) {
									logger.error("Exception while unpausing connection", e);
								}
								isDeviceIdle = false;
							}
						}
					}, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));

					// Register "Notification Policy Changed" broadcast receiver.
					// This is called when the state of getNotificationPolicy() changes.
					getAppContext().registerReceiver(new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							try {
								NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
								NotificationManager.Policy policy = notificationManager.getNotificationPolicy();
								logger.info("*** Notification Policy changed: " + policy.toString());
							} catch (Exception e) {
								logger.error("Could not get notification policy", e);
							}
						}
					}, new IntentFilter(ACTION_NOTIFICATION_POLICY_CHANGED));
				}

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					// Register "Notification Channel Group Block State Changed" broadcast receiver.
					// This is called when a NotificationChannelGroup is blocked or unblocked.
					// This broadcast is only sent to the app that owns the channel group that has changed.
					getAppContext().registerReceiver(new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							try {
								boolean blockedState = intent.getBooleanExtra(EXTRA_BLOCKED_STATE, false);
								String groupName = intent.getStringExtra(EXTRA_NOTIFICATION_CHANNEL_GROUP_ID);
								logger.info(
									"*** Channel group {} blocked: {}",
									groupName != null ? groupName : "<not specified>",
									blockedState
								);
							} catch (Exception e) {
								logger.error("Could not get data from intent", e);
							}
						}
					}, new IntentFilter(ACTION_NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED));
				}

				// Add a local broadcast receiver to receive PinningFailureReports
				PinningFailureReportBroadcastReceiver receiver = new PinningFailureReportBroadcastReceiver();
				LocalBroadcastManager.getInstance(context).registerReceiver(receiver, new IntentFilter(BackgroundReporter.REPORT_VALIDATION_EVENT));

				// Register a broadcast receiver for changes in app restrictions
				if (ConfigUtils.isWorkRestricted()) {
					getAppContext().registerReceiver(new BroadcastReceiver() {
						@Override
						public void onReceive(Context context, Intent intent) {
							AppRestrictionService.getInstance().reload();
							Intent syncIntent = new Intent();
							syncIntent.putExtra(WorkSyncService.EXTRA_WORK_UPDATE_RESTRICTIONS_ONLY, true);
							WorkSyncService.enqueueWork(getAppContext(), syncIntent, true);
						}
					}, new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED));
				}

				// Start the Threema Push Service (if enabled in config)
				ThreemaPushService.tryStart(logger, getAppContext());
			}
		}
	}

	@Override
	public void onStart(@NonNull LifecycleOwner owner) {
		logger.info("*** Lifecycle: App now visible");
	}

	@Override
	public void onStop(@NonNull LifecycleOwner owner) {
		logger.info("*** Lifecycle: App now stopped");
		if (masterKey == null || masterKey.isLocked() || serviceManager == null) {
			return;
		}

		try {
			serviceManager.getMessageService().saveMessageQueue(masterKey);
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public void onCreate(@NonNull LifecycleOwner owner) {
		logger.info("*** Lifecycle: App now created");
	}

	@Override
	public void onResume(@NonNull LifecycleOwner owner) {
		logger.info("*** Lifecycle: App now resumed");
		if (serviceManager != null && serviceManager.getLifetimeService() != null) {
			serviceManager.getLifetimeService().acquireConnection(ACTIVITY_CONNECTION_TAG);
		}
	}

	@Override
	public void onPause(@NonNull LifecycleOwner owner) {
		logger.info("*** Lifecycle: App now paused");
		if (serviceManager != null && serviceManager.getLifetimeService() != null) {
			serviceManager.getLifetimeService().releaseConnectionLinger(ACTIVITY_CONNECTION_TAG, ACTIVITY_CONNECTION_LIFETIME);
		}
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner) {
		logger.info("*** Lifecycle: App now destroyed");
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();

		logger.info("*** App is low on memory");
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		if (serviceManager != null) {
			ConfigUtils.setLocaleOverride(getAppContext(), serviceManager.getPreferenceService());
		}

		super.onConfigurationChanged(newConfig);
	}

	@SuppressLint("SwitchIntDef")
	@Override
	public void onTrimMemory(int level) {

		super.onTrimMemory(level);

		switch (level) {
			case TRIM_MEMORY_RUNNING_MODERATE:
				logger.trace("onTrimMemory (level={})", level);
				break;
			case TRIM_MEMORY_UI_HIDDEN:
				logger.debug("onTrimMemory (level={}, ui hidden)", level);
				/* fallthrough */
			default:
				if (level != TRIM_MEMORY_UI_HIDDEN) { // See above
					logger.debug("onTrimMemory (level={})", level);
				}

				/* save our master key now if necessary, as we may get killed and if the user was still in the
			     * initial setup procedure, this can lead to trouble as the database may already be there
			     * but we may no longer be able to access it due to missing master key
				 */
				try {
					if (getMasterKey() != null && !getMasterKey().isProtected()) {
						if (serviceManager != null && serviceManager.getPreferenceService().getWizardRunning()) {
							getMasterKey().setPassphrase(null);
						}
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}

				/* take the opportunity to save the message queue */
				try {
					if (serviceManager != null)
						serviceManager.getMessageService().saveMessageQueueAsync();
				} catch (Exception e) {
					logger.error("Exception", e);
				}

				try {
					if (serviceManager != null) {
						serviceManager.getAvatarCacheService().clear();
					}
				} catch (Exception e) {
					logger.error("Exception", e);
				}

				break;
		}
	}

	@Nullable
	public static ServiceManager getServiceManager() {
		return serviceManager;
	}

	@NonNull
	public static ServiceManager requireServiceManager() throws NullPointerException {
		return Objects.requireNonNull(serviceManager);
	}

	public static MasterKey getMasterKey() {
		return masterKey;
	}

	public static void putMessageDraft(String chatId, CharSequence value, @Nullable AbstractMessageModel quotedMessageModel) {
		if (value == null || value.toString().trim().length() < 1) {
			messageDrafts.remove(chatId);
			quoteDrafts.remove(chatId);
		} else {
			messageDrafts.put(chatId, value.toString());
			if (quotedMessageModel != null) {
				quoteDrafts.put(chatId, quotedMessageModel.getApiMessageId());
			} else {
				quoteDrafts.remove(chatId);
			}
		}
		try {
			getServiceManager().getPreferenceService().setMessageDrafts(messageDrafts);
			getServiceManager().getPreferenceService().setQuoteDrafts(quoteDrafts);
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	public static String getMessageDraft(String chatId) {
		if (messageDrafts.containsKey(chatId)) {
			return messageDrafts.get(chatId);
		}
		return null;
	}

	public static String getQuoteDraft(String chatId) {
		if (quoteDrafts.containsKey(chatId)) {
			return quoteDrafts.get(chatId);
		}
		return null;
	}

	private static void retrieveMessageDraftsFromStorage() {
		try {
			messageDrafts = getServiceManager().getPreferenceService().getMessageDrafts();
			quoteDrafts = getServiceManager().getPreferenceService().getQuoteDrafts();
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@SuppressLint("ApplySharedPref")
	private static void resetPreferences(SharedPreferences prefs) {
		// Fix master key preference state if necessary (could be wrong if user kills app
		// while disabling master key passphrase).
		if (masterKey.isProtected() && prefs != null && !prefs.getBoolean(getAppContext().getString(R.string.preferences__masterkey_switch), false)) {
			logger.debug("Master key is protected, but switch preference is disabled - fixing");
			prefs.edit().putBoolean(getAppContext().getString(R.string.preferences__masterkey_switch), true).commit();
		}

		// If device is in AEC exclusion list and the user did not choose a preference yet,
		// update the shared preference.
		if (prefs != null && prefs.getString(getAppContext().getString(R.string.preferences__voip_echocancel), "none").equals("none")) {
			// Determine whether device is excluded from hardware AEC
			final String modelInfo = Build.MANUFACTURER + ";" + Build.MODEL;
			boolean exclude = !Config.allowHardwareAec();

			// Set default preference
			final SharedPreferences.Editor editor = prefs.edit();
			if (exclude) {
				logger.debug("Device {} is on AEC exclusion list, switching to software echo cancellation", modelInfo);
				editor.putString(getAppContext().getString(R.string.preferences__voip_echocancel), "sw");
			} else {
				logger.debug("Device {} is not on AEC exclusion list", modelInfo);
				editor.putString(getAppContext().getString(R.string.preferences__voip_echocancel), "hw");
			}
			editor.commit();
		}

		try {
			PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_chat, true);
			PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_privacy, true);
			PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_appearance, true);
			PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_notifications, true);
			PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_media, true);
			PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_calls, true);
			PreferenceManager.setDefaultValues(getAppContext(), R.xml.preference_troubleshooting, true);
		} catch (Exception e) {
			logger.error("Exception", e);
		}

		// set default theme depending on app type
		if (prefs != null) {
			if (TestUtil.empty(prefs.getString(getAppContext().getString(R.string.preferences__theme), null))) {
				prefs.edit().putString(getAppContext().getString(R.string.preferences__theme), String.valueOf(
					ConfigUtils.isWorkBuild() ?
						Theme_DARK:
						Theme_LIGHT)
				).apply();
			}
		}
	}

	private static void setupLogging(PreferenceStore preferenceStore) {
		// check if a THREEMA_MESSAGE_LOG exist on the
		final File forceMessageLog = new File(Environment.getExternalStorageDirectory() + "/ENABLE_THREEMA_MESSAGE_LOG");
		final File forceDebugLog = new File(Environment.getExternalStorageDirectory() + "/ENABLE_THREEMA_DEBUG_LOG");

		// enable message logging if necessary
		if (preferenceStore == null || preferenceStore.getBoolean(getAppContext().getString(R.string.preferences__message_log_switch))
			|| forceMessageLog.exists() || forceDebugLog.exists()) {
			DebugLogFileBackend.setEnabled(true);
		} else {
			DebugLogFileBackend.setEnabled(false);
		}

		// temporary - testing native crash in CompletableFuture while loading emojis
		if (preferenceStore != null) {
			final File forceAndroidEmojis = new File(Environment.getExternalStorageDirectory() + "/FORCE_SYSTEM_EMOJIS");
			if (forceAndroidEmojis.exists()) {
				preferenceStore.save(getAppContext().getString(R.string.preferences__emoji_style), "1");
			}
		}
	}

	public static synchronized void reset() {

		//set default preferences
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getAppContext());
		resetPreferences(sharedPreferences);

		// init state bitmap cache singleton
		StateBitmapUtil.init(getAppContext());

		// init connection state colors
		ConnectionIndicatorUtil.init(getAppContext());

		try {
			// Load preference store
			PreferenceStore preferenceStore = new PreferenceStore(getAppContext(), masterKey);
			ipv6 = preferenceStore.getBoolean(getAppContext().getString(R.string.preferences__ipv6_preferred));

			// Set logging to "always on"
			setupLogging(null);

			// Make database key from master key
			String databaseKey = "x\"" + Utils.byteArrayToHexString(masterKey.getKey()) + "\"";

			// Migrate database to v4 format if necessary
			int sqlcipherVersion = 4;
			try {
				DatabaseServiceNew.tryMigrateToV4(getAppContext(), databaseKey);
			} catch (DatabaseMigrationFailedException m) {
				logger.error("Exception", m);
				Toast.makeText(getAppContext(), "Database migration failed. Please free some space on your internal memory.", Toast.LENGTH_LONG).show();
				sqlcipherVersion = 3;
			}

			UpdateSystemService updateSystemService = new UpdateSystemServiceImpl();

			DatabaseServiceNew databaseServiceNew = new DatabaseServiceNew(getAppContext(), databaseKey, updateSystemService, sqlcipherVersion);
			databaseServiceNew.executeNull();

			// Migrate nonce database to unencrypted DB
			int nonceSqlcipherVersion = 4;

			// do not attempt a nonce DB migration if the main DB is still on version 3
			if (sqlcipherVersion == 4) {
				try {
					NonceDatabaseBlobService.tryMigrateToV4(getAppContext(), databaseKey);
				} catch (DatabaseMigrationFailedException m) {
					logger.error("Exception", m);
					Toast.makeText(getAppContext(), "Nonce database migration failed. Please free some space on your internal memory.", Toast.LENGTH_LONG).show();
					nonceSqlcipherVersion = 3;
				}
			} else {
				nonceSqlcipherVersion = 3;
			}

			logger.info(
				"*** App launched. Device/Android Version/Flavor: {} Version: {} Build: {}",
				ConfigUtils.getDeviceInfo(getAppContext(), false),
				BuildConfig.VERSION_NAME,
				ConfigUtils.getBuildNumber(getAppContext())
			);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				ActivityManager activityManager = (ActivityManager) getAppContext().getSystemService(Context.ACTIVITY_SERVICE);
				try {
					List<ApplicationExitInfo> applicationExitInfos = activityManager.getHistoricalProcessExitReasons(null, 0, 0);

					if (applicationExitInfos.size() > 0) {
						for (ApplicationExitInfo exitInfo : applicationExitInfos) {
							long timestampOfLastLog = 0L;
							if (sharedPreferences != null) {
								timestampOfLastLog = sharedPreferences.getLong(EXIT_REASON_LOGGING_TIMESTAMP, timestampOfLastLog);
							}

							if (exitInfo.getTimestamp() > timestampOfLastLog) {
								logger.info(String.format(Locale.US, "*** App last exited at %s with reason: %d, description: %s", DateUtils.formatDateTime(getAppContext(), exitInfo.getTimestamp(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME), exitInfo.getReason(), exitInfo.getDescription()));
								if (exitInfo.getReason() == ApplicationExitInfo.REASON_ANR) {
									try {
										InputStream traceInputStream = exitInfo.getTraceInputStream();
										if (traceInputStream != null) {
											BufferedReader r = new BufferedReader(new InputStreamReader(traceInputStream));
											StringBuilder s = new StringBuilder();
											for (String line; (line = r.readLine()) != null; ) {
												s.append(line).append('\n');
											}
											logger.info(s.toString());
										}
									} catch (IOException e) {
										logger.error("Error getting ANR trace", e);
									}
								}
							}
						}

						if (sharedPreferences != null) {
							sharedPreferences.edit().putLong(EXIT_REASON_LOGGING_TIMESTAMP, System.currentTimeMillis()).apply();
						}
					}
				} catch (IllegalArgumentException e) {
					logger.error("Unable to get reason of last exit", e);
				}
			}

			// Set up logging
			setupLogging(preferenceStore);

			IdentityStore identityStore = new IdentityStore(preferenceStore);

			NonceDatabaseBlobService nonceDatabaseBlobService = new NonceDatabaseBlobService(getAppContext(), masterKey, nonceSqlcipherVersion, identityStore);
			logger.info("Nonce count: " + nonceDatabaseBlobService.getCount());

			final ThreemaConnection connection = new ThreemaConnection(
					identityStore,
					new NonceFactory(nonceDatabaseBlobService),
					null,
					getIPv6());

			connection.setVersion(appVersion);

			// Whenever the connection is established, check whether the
			// push token needs to be updated.
			connection.addConnectionStateListener((newConnectionState, address) -> {
				if (newConnectionState == ConnectionState.LOGGEDIN) {
					final Context appContext = getAppContext();
					if (PushService.servicesInstalled(appContext)) {
						if (PushUtil.isPushEnabled(appContext)) {
							if (PushUtil.pushTokenNeedsRefresh(appContext)) {
								PushUtil.enqueuePushTokenUpdate(appContext, false, false);
							} else {
								logger.debug("Push token is still fresh. No update needed");
							}
						}
					}
				}
			});

			// Instantiate service manager
			serviceManager = new ServiceManager(
					connection,
					databaseServiceNew,
					identityStore,
					preferenceStore,
					masterKey,
					updateSystemService
			);

			connection.setServerAddressProvider(serviceManager.getServerAddressProviderService().getServerAddressProvider());

			// get application restrictions
			if (ConfigUtils.isWorkBuild()) {
				AppRestrictionService.getInstance()
					.reload();
			}

			final OnFirstConnectRoutine firstConnectRoutine = new OnFirstConnectRoutine(serviceManager.getUserService());
			connection.addConnectionStateListener(new ConnectionStateListener() {
				@Override
				public void updateConnectionState(ConnectionState connectionState, InetSocketAddress socketAddress) {
					logger.info(
						"ThreemaConnection state changed: {} (port={}, ipv6={})",
						connectionState,
						socketAddress.getPort(),
						socketAddress.getAddress() instanceof Inet6Address
					);

					if (connectionState == ConnectionState.LOGGEDIN) {
						lastLoggedIn = new Date();
						if (firstConnectRoutine.getRunCount() == 0) {
							logger.debug("Run feature mask update");
							new Thread(firstConnectRoutine).start();
						}
					}
				}
			});

			/* cancel any "new message" notification */
			NotificationManager notificationManager = (NotificationManager) getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				notificationManager.cancel(NEW_MESSAGE_LOCKED_NOTIFICATION_ID);
			}

			/* trigger a connection now, just to be sure we're up-to-date and any broken connection
			   (e.g. from before a reboot) is preempted.
			 */
			serviceManager.getLifetimeService().acquireConnection("resetConnection");
			serviceManager.getLifetimeService().releaseConnectionLinger("resetConnection", ACTIVITY_CONNECTION_LIFETIME);
			configureListeners();

			//mark all SENDING messages as SENDFAILED

			databaseServiceNew
					.getMessageModelFactory()
					.markUnqueuedMessagesAsFailed();

			databaseServiceNew
					.getGroupMessageModelFactory()
					.markUnqueuedMessagesAsFailed();

			databaseServiceNew
					.getDistributionListMessageModelFactory()
					.markUnqueuedMessagesAsFailed();

			retrieveMessageDraftsFromStorage();

			// process webclient wakeups
			SessionWakeUpServiceImpl.getInstance().processPendingWakeupsAsync();

			// start threema safe scheduler
			serviceManager.getThreemaSafeService().scheduleUpload();

			new Thread(() -> {
				// schedule work synchronization
				scheduleWorkSync(preferenceStore);
				// schedule identity states / feature masks etc.
				scheduleIdentityStatesSync(preferenceStore);
				// schedule shortcut update
				if (preferenceStore.getBoolean(getAppContext().getString(R.string.preferences__direct_share))) {
					scheduleShareTargetShortcutUpdate();

				}
			}, "scheduleSync").start();

			initMapLibre();

			// setup locale override
			ConfigUtils.setLocaleOverride(getAppContext(), serviceManager.getPreferenceService());
		} catch (MasterKeyLockedException e) {
			logger.error("Exception", e);
		} catch (SQLiteException e) {
			logger.error("Exception", e);
		} catch (ThreemaException e) {
			// no identity
			logger.info("No valid identity.");
		}
	}

	private static void initMapLibre() {
		if (ConfigUtils.hasNoMapLibreSupport()) {
			logger.debug("*** MapLibre disabled due to faulty firmware");
		} else {
			Mapbox.getInstance(getAppContext());
			logger.debug("*** MapLibre enabled");
		}
	}

	private static long getSchedulePeriod(PreferenceStore preferenceStore, int key) {
		Integer schedulePeriod = preferenceStore.getInt(getAppContext().getString(key));
		if (schedulePeriod == null || schedulePeriod == 0) {
			schedulePeriod = (int) DateUtils.DAY_IN_MILLIS;
		} else {
			schedulePeriod *= (int) DateUtils.SECOND_IN_MILLIS;
		}
		return (long) schedulePeriod;
	}

	@WorkerThread
	private static boolean scheduleIdentityStatesSync(PreferenceStore preferenceStore) {
		long schedulePeriod = getSchedulePeriod(preferenceStore, R.string.preferences__identity_states_check_interval);

		logger.info("Initializing Identity States sync. Requested schedule period: {} ms", schedulePeriod);

		try {
			WorkManager workManager = WorkManager.getInstance(context);

			if (WorkManagerUtil.cancelExistingWorkManagerInstance(
				workManager,
				WORKER_IDENTITY_STATES_PERIODIC_NAME,
				schedulePeriod
			)) {
				logger.debug("Scheduling new job");

				// schedule the start of the service according to schedule period
				Constraints constraints = new Constraints.Builder()
					.setRequiredNetworkType(NetworkType.CONNECTED)
					.build();

				PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(IdentityStatesWorker.class, schedulePeriod, TimeUnit.MILLISECONDS)
					.setConstraints(constraints)
					.addTag(String.valueOf(schedulePeriod))
					.setInitialDelay(1000, TimeUnit.MILLISECONDS)
					.build();

				workManager.enqueueUniquePeriodicWork(WORKER_IDENTITY_STATES_PERIODIC_NAME, ExistingPeriodicWorkPolicy.REPLACE, workRequest);
				return true;
			}
		} catch (IllegalStateException e) {
			logger.error("Unable to initialize WorkManager", e);
		}
		return false;
	}

	private static boolean scheduleWorkSync(PreferenceStore preferenceStore) {
		if (!ConfigUtils.isWorkBuild()) {
			return false;
		}

		long schedulePeriod = getSchedulePeriod(preferenceStore, R.string.preferences__work_sync_check_interval);

		logger.info("Scheduling Work Sync. Schedule period: {}", schedulePeriod);

		// schedule the start of the service according to schedule period
		JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
		if (jobScheduler != null) {
			ComponentName serviceComponent = new ComponentName(context, WorkSyncJobService.class);
			JobInfo.Builder builder = new JobInfo.Builder(WORK_SYNC_JOB_ID, serviceComponent)
				.setPeriodic(schedulePeriod)
				.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
			jobScheduler.schedule(builder.build());
			return true;
		}
		logger.debug("unable to schedule work sync");
		return false;
	}

	@WorkerThread
	public static boolean scheduleShareTargetShortcutUpdate() {
		logger.info("Scheduling share target shortcut update work");

		long schedulePeriod = DateUtils.MINUTE_IN_MILLIS * 15;

		try {
			WorkManager workManager = WorkManager.getInstance(context);

			if (WorkManagerUtil.cancelExistingWorkManagerInstance(
				workManager,
				WORKER_SHARE_TARGET_UPDATE,
				schedulePeriod
			)) {
				logger.debug("Create new worker");

				// schedule the start of the service according to schedule period
				Constraints constraints = new Constraints.Builder()
					.setRequiredNetworkType(NetworkType.CONNECTED)
					.build();

				PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(ShareTargetUpdateWorker.class, schedulePeriod, TimeUnit.MILLISECONDS)
					.setConstraints(constraints)
					.addTag(String.valueOf(schedulePeriod))
					.setInitialDelay(3, TimeUnit.MINUTES)
					.build();

				workManager.enqueueUniquePeriodicWork(WORKER_SHARE_TARGET_UPDATE, ExistingPeriodicWorkPolicy.REPLACE, workRequest);
			} else {
				logger.debug("Reusing existing worker");
			}
		} catch (IllegalStateException e) {
			logger.error("Unable to schedule share target update work", e);
			return false;
		}

		return true;
	}

	private static void configureListeners() {
		ListenerManager.groupListeners.add(new GroupListener() {
			@Override
			public void onCreate(GroupModel newGroupModel) {
				try {
					serviceManager.getConversationService().refresh(newGroupModel);
					serviceManager.getMessageService().createStatusMessage(
							serviceManager.getContext().getString(R.string.status_create_group),
							serviceManager.getGroupService().createReceiver(newGroupModel));
				} catch (ThreemaException e) {
					logger.error("Exception", e);
				}
			}

			@Override
			public void onRename(GroupModel groupModel) {
				new Thread(() -> {
					try {
						MessageReceiver messageReceiver = serviceManager.getGroupService().createReceiver(groupModel);
						serviceManager.getConversationService().refresh(groupModel);
						serviceManager.getMessageService().createStatusMessage(
							serviceManager.getContext().getString(R.string.status_rename_group, groupModel.getName()),
							messageReceiver);
						ShortcutUtil.updatePinnedShortcut(messageReceiver);
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}

			@Override
			public void onUpdatePhoto(GroupModel groupModel) {
				new Thread(() -> {
					try {
						MessageReceiver messageReceiver = serviceManager.getGroupService().createReceiver(groupModel);
						serviceManager.getConversationService().refresh(groupModel);
						serviceManager.getMessageService().createStatusMessage(
							serviceManager.getContext().getString(R.string.status_group_new_photo),
							messageReceiver);
						ShortcutUtil.updatePinnedShortcut(messageReceiver);
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}

			@Override
			public void onRemove(GroupModel groupModel) {
				new Thread(() -> {
					try {
						final MessageReceiver receiver = serviceManager.getGroupService().createReceiver(groupModel);
						serviceManager.getBallotService().remove(receiver);
						serviceManager.getConversationService().removed(groupModel);
						serviceManager.getNotificationService().cancel(new GroupMessageReceiver(groupModel, null, null, null, null));
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}

			@Override
			public void onNewMember(GroupModel group, String newIdentity, int previousMemberCount) {
				String memberName = newIdentity;
				ContactModel contactModel;
				try {
					if ((contactModel = serviceManager.getContactService().getByIdentity(newIdentity)) != null) {
						memberName = NameUtil.getDisplayNameOrNickname(contactModel, true);
					}
				} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
					logger.error("Exception", e);
				}


				try {
					final MessageReceiver receiver = serviceManager.getGroupService().createReceiver(group);
					final String myIdentity = serviceManager.getUserService().getIdentity();

					if (receiver != null && !TestUtil.empty(myIdentity)) {
						serviceManager.getMessageService().createStatusMessage(
								serviceManager.getContext().getString(R.string.status_group_new_member, memberName),
								receiver);

						if ((!myIdentity.equals(group.getCreatorIdentity())) || previousMemberCount > 1) {
							//send all open ballots to the new group member
							BallotService ballotService = serviceManager.getBallotService();
							if (ballotService != null) {
								List<BallotModel> openBallots = ballotService.getBallots(new BallotService.BallotFilter() {
									@Override
									public MessageReceiver getReceiver() {
										return receiver;
									}

									@Override
									public BallotModel.State[] getStates() {
										return new BallotModel.State[]{BallotModel.State.OPEN};
									}

									@Override
									public boolean filter(BallotModel ballotModel) {
										//only my ballots please
										return ballotModel.getCreatorIdentity().equals(myIdentity);
									}
								});

								for (BallotModel ballotModel : openBallots) {
									ballotService.publish(receiver, ballotModel, null, newIdentity);
								}
							}
						}
					}

				} catch (ThreemaException x) {
					logger.error("Exception", x);
				}

				//reset avatar to recreate it!
				try {
					serviceManager.getAvatarCacheService()
							.reset(group);
				} catch (FileSystemNotPresentException e) {
					logger.error("Exception", e);
				}
			}

			@Override
			public void onMemberLeave(GroupModel group, String identity, int previousMemberCount) {
				String memberName = identity;
				ContactModel contactModel;
				try {
					if ((contactModel = serviceManager.getContactService().getByIdentity(identity)) != null) {
						memberName = NameUtil.getDisplayNameOrNickname(contactModel, true);
					}
				} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
					logger.error("Exception", e);
				}
				try {
					final MessageReceiver receiver = serviceManager.getGroupService().createReceiver(group);

					serviceManager.getMessageService().createStatusMessage(
							serviceManager.getContext().getString(R.string.status_group_member_left, memberName),
							receiver);

					BallotService ballotService = serviceManager.getBallotService();
					ballotService.removeVotes(receiver, identity);
				} catch (ThreemaException e) {
					logger.error("Exception", e);
				}
			}

			@Override
			public void onMemberKicked(GroupModel group, String identity, int previousMemberCount) {
				final String myIdentity = serviceManager.getUserService().getIdentity();

				if (myIdentity != null && myIdentity.equals(identity)) {
					// my own member status has changed
					try {
						serviceManager.getConversationService().refresh(group);
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}

				String memberName = identity;
				ContactModel contactModel;
				try {
					if ((contactModel = serviceManager.getContactService().getByIdentity(identity)) != null) {
						memberName = NameUtil.getDisplayNameOrNickname(contactModel, true);
					}
				} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
					logger.error("Exception", e);
				}
				try {
					final MessageReceiver receiver = serviceManager.getGroupService().createReceiver(group);

					serviceManager.getMessageService().createStatusMessage(
							serviceManager.getContext().getString(R.string.status_group_member_kicked, memberName),
							receiver);


					BallotService ballotService = serviceManager.getBallotService();
					ballotService.removeVotes(receiver, identity);
				} catch (ThreemaException e) {
					logger.error("Exception", e);
				}
			}

			@Override
			public void onUpdate(GroupModel groupModel) {
				try {
					serviceManager.getConversationService().refresh(groupModel);
				} catch (ThreemaException e) {
					logger.error("Exception", e);
				}
			}

			@Override
			public void onLeave(GroupModel groupModel) {
				new Thread(() -> {
					try {
						serviceManager.getConversationService().refresh(groupModel);
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}

			@Override
			public void onGroupStateChanged(GroupModel groupModel, @GroupService.GroupState int oldState, @GroupService.GroupState int newState) {
				logger.debug("&&& onGroupStateChanged: {} -> {}", oldState, newState);

				showNotesGroupNotice(groupModel, oldState, newState);
			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		ListenerManager.distributionListListeners.add(new DistributionListListener() {
			@Override
			public void onCreate(DistributionListModel distributionListModel) {
				try {
					serviceManager.getConversationService().refresh(distributionListModel);
				} catch (ThreemaException e) {
					logger.error("Exception", e);
				}
			}

			@Override
			public void onModify(DistributionListModel distributionListModel) {
				new Thread(() -> {
					try {
						serviceManager.getConversationService().refresh(distributionListModel);
						ShortcutUtil.updatePinnedShortcut(serviceManager.getDistributionListService().createReceiver(distributionListModel));
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}


			@Override
			public void onRemove(DistributionListModel distributionListModel) {
				new Thread(() -> {
					try {
						serviceManager.getConversationService().removed(distributionListModel);
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		ListenerManager.messageListeners.add(new ch.threema.app.listeners.MessageListener() {
			@Override
			public void onNew(AbstractMessageModel newMessage) {
				logger.debug("MessageListener.onNewMessage");
				if (!newMessage.isStatusMessage()) {
					showConversationNotification(newMessage, false);
				}
			}

			@Override
			public void onModified(List<AbstractMessageModel> modifiedMessageModels) {
				logger.debug("MessageListener.onModified");
				for (final AbstractMessageModel modifiedMessageModel : modifiedMessageModels) {

					if (!modifiedMessageModel.isStatusMessage()) {
						try {
							serviceManager.getConversationService().refresh(modifiedMessageModel);
						} catch (ThreemaException e) {
							logger.error("Exception", e);
						}
					}

					if (!modifiedMessageModel.isStatusMessage() && modifiedMessageModel.getType() == MessageType.IMAGE) {
						// update notification with image preview
						showConversationNotification(modifiedMessageModel, true);
					}
				}
			}

			@Override
			public void onRemoved(AbstractMessageModel removedMessageModel) {
				logger.debug("MessageListener.onRemoved");
				if (!removedMessageModel.isStatusMessage()) {
					try {
						serviceManager.getConversationService().refreshWithDeletedMessage(removedMessageModel);
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}
			}

			@Override
			public void onRemoved(List<AbstractMessageModel> removedMessageModels) {
				logger.debug("MessageListener.onRemoved multi");
				for (final AbstractMessageModel removedMessageModel : removedMessageModels) {
					if (!removedMessageModel.isStatusMessage()) {
						try {
							serviceManager.getConversationService().refreshWithDeletedMessage(removedMessageModel);
						} catch (ThreemaException e) {
							logger.error("Exception", e);
						}
					}
				}
			}

			@Override
			public void onProgressChanged(AbstractMessageModel messageModel, int newProgress) {
				//ingore
			}

			private void showConversationNotification(AbstractMessageModel newMessage, boolean updateExisting) {
				try {
					ConversationService conversationService = serviceManager.getConversationService();
					ConversationModel conversationModel = conversationService.refresh(newMessage);

					if (conversationModel != null
							&& !newMessage.isOutbox()
							&& !newMessage.isStatusMessage()
							&& !newMessage.isRead()) {

						NotificationService notificationService = serviceManager.getNotificationService();
						ContactService contactService = serviceManager.getContactService();
						GroupService groupService = serviceManager.getGroupService();
						DeadlineListService hiddenChatsListService = serviceManager.getHiddenChatsListService();

						if (TestUtil.required(notificationService, contactService, groupService)) {

							notificationService.addConversationNotification(ConversationNotificationUtil.convert(
									getAppContext(),
									newMessage,
									contactService,
									groupService,
									hiddenChatsListService),
									updateExisting);

							// update widget on incoming message
							WidgetUtil.updateWidgets(serviceManager.getContext());
						}
					}
				} catch (ThreemaException e) {
					logger.error("Exception", e);
				}
			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		ListenerManager.groupJoinResponseListener.add((outgoingGroupJoinRequestModel, status) -> {
			NotificationService n = serviceManager.getNotificationService();
			if (n != null) {
				n.showGroupJoinResponseNotification(outgoingGroupJoinRequestModel, status, serviceManager.getDatabaseServiceNew());
			}
		});


		ListenerManager.incomingGroupJoinRequestListener.add(new IncomingGroupJoinRequestListener() {
			@Override
			public void onReceived(IncomingGroupJoinRequestModel incomingGroupJoinRequestModel, GroupModel groupModel) {
				NotificationService n = serviceManager.getNotificationService();
				if (n != null) {
					n.showGroupJoinRequestNotification(incomingGroupJoinRequestModel, groupModel);
				}
			}

			@Override
			public void onRespond() {
				// don't bother here
			}
		});

		ListenerManager.serverMessageListeners.add(new ServerMessageListener() {
			@Override
			public void onAlert(ServerMessageModel serverMessage) {
				NotificationService n = serviceManager.getNotificationService();
				if (n != null) {
					n.showServerMessage(serverMessage);
				}
			}

			@Override
			public void onError(ServerMessageModel serverMessage) {
				NotificationService n = serviceManager.getNotificationService();
				if (n != null) {
					n.showServerMessage(serverMessage);
				}
			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		ListenerManager.contactListeners.add(new ContactListener() {
			@Override
			public void onModified(ContactModel modifiedContactModel) {
				new Thread(() -> {
					try {
						serviceManager.getConversationService().refresh(modifiedContactModel);
						ShortcutUtil.updatePinnedShortcut(serviceManager.getContactService().createReceiver(modifiedContactModel));
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}

			@Override
			public void onAvatarChanged(ContactModel contactModel) {
				new Thread(() -> {
					try {
						ShortcutUtil.updatePinnedShortcut(serviceManager.getContactService().createReceiver(contactModel));
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}

			@Override
			public void onNew(ContactModel createdContactModel) { }

			@Override
			public void onRemoved(ContactModel removedContactModel) {
				new Thread(() -> {
					try {
						serviceManager.getConversationService().removed(removedContactModel);
						serviceManager.getNotificationService().cancel(new ContactMessageReceiver(
							removedContactModel, serviceManager.getContactService(), null, null, null, null)
						);

						//remove custom avatar (ANDR-353)
						FileService f = serviceManager.getFileService();
						if (f != null) {
							f.removeContactAvatar(removedContactModel);
							f.removeContactPhoto(removedContactModel);
						}
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
				}).start();
			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		ListenerManager.contactSettingsListeners.add(new ContactSettingsListener() {
			@Override
			public void onSortingChanged() {
				//do nothing!
			}

			@Override
			public void onNameFormatChanged() {
				//do nothing
			}

			@Override
			public void onAvatarSettingChanged() {
				//reset the avatar cache!
				if (serviceManager != null) {
					try {
						AvatarCacheService s = null;
						s = serviceManager.getAvatarCacheService();
						if (s != null) {
							s.clear();
						}
					} catch (FileSystemNotPresentException e) {
						logger.error("Exception", e);
					}
				}
			}

			@Override
			public void onInactiveContactsSettingChanged() {

			}

			@Override
			public void onNotificationSettingChanged(String uid) {

			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		ListenerManager.conversationListeners.add(new ConversationListener() {
			@Override
			public void onNew(ConversationModel conversationModel) {}

			@Override
			public void onModified(ConversationModel modifiedConversationModel, Integer oldPosition) {}

			@Override
			public void onRemoved(ConversationModel conversationModel) {
				//remove notification!
				NotificationService notificationService = serviceManager.getNotificationService();
				if (notificationService != null) {
					notificationService.cancel(conversationModel);
				}
			}

			@Override
			public void onModifiedAll() {}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		ListenerManager.ballotVoteListeners.add(new BallotVoteListener() {
			@Override
			public void onSelfVote(BallotModel ballotModel) { }

			@Override
			public void onVoteChanged(BallotModel ballotModel, String votingIdentity, boolean isFirstVote) {
				//add group state

				//DISABLED
				ServiceManager s = ThreemaApplication.getServiceManager();
				if(s != null) {
					try {
						BallotService ballotService = s.getBallotService();
						ContactService contactService = s.getContactService();
						GroupService groupService = s.getGroupService();
						MessageService messageService = s.getMessageService();
						UserService userService = s.getUserService();

						if(TestUtil.required(ballotModel, contactService, groupService, messageService, userService)
								//disabled, show status message at every participant
								/*&& BallotUtil.isMine(ballotModel, userService)*/) {
							LinkBallotModel b = ballotService.getLinkedBallotModel(ballotModel);
							if(b != null) {
								String message = null;
								MessageReceiver receiver = null;
								if (b instanceof GroupBallotModel) {
									GroupModel groupModel = groupService.getById(((GroupBallotModel) b).getGroupId());

									//its a group ballot,write status
									receiver = groupService.createReceiver(groupModel);
									// reset archived status
									groupService.setIsArchived(groupModel, false);

								} else if (b instanceof IdentityBallotModel) {
									String identity = ((IdentityBallotModel) b).getIdentity();

									//not implemented
									receiver = contactService.createReceiver(contactService.getByIdentity(identity));
									// reset archived status
									contactService.setIsArchived(identity, false);
								}

								if (ballotModel.getType() == BallotModel.Type.RESULT_ON_CLOSE) {
									//on private voting, only show default update msg!
									message = serviceManager
												.getContext().getString(R.string.status_ballot_voting_changed, ballotModel.getName());
								} else {

									if (receiver != null) {
										ContactModel votingContactModel = contactService.getByIdentity(votingIdentity);

										if (isFirstVote) {
											message = serviceManager
													.getContext().getString(R.string.status_ballot_user_first_vote,
															NameUtil.getDisplayName(votingContactModel),
															ballotModel.getName());
										} else {
											message = serviceManager
													.getContext().getString(R.string.status_ballot_user_modified_vote,
															NameUtil.getDisplayName(votingContactModel),
															ballotModel.getName());
										}
									}
								}

								if(TestUtil.required(message, receiver)) {
									messageService.createStatusMessage(message, receiver);
								}

								//now check if every participant has voted
								if(ballotService.getPendingParticipants(ballotModel.getId()).size() == 0) {
									String ballotAllVotesMessage = serviceManager
													.getContext().getString(R.string.status_ballot_all_votes,
															ballotModel.getName());

									messageService.createStatusMessage(ballotAllVotesMessage, receiver);
								}
							}
						}
					} catch (ThreemaException x) {
						logger.error("Exception", x);
					}
				}
			}

			@Override
			public void onVoteRemoved(BallotModel ballotModel, String votingIdentity) {
				//ignore
			}

			@Override
			public boolean handle(BallotModel ballotModel) {
				//handle all
				return true;
			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		final ContentObserver contentObserverChangeContactNames = new ContentObserver(null) {
			private boolean isRunning = false;

			@Override
			public boolean deliverSelfNotifications() {
				return super.deliverSelfNotifications();
			}

			@Override
			public void onChange(boolean selfChange) {
				super.onChange(selfChange);

				if (!selfChange && serviceManager != null && !isRunning) {
					this.isRunning = true;

					boolean cont;
					//check if a sync is in progress.. wait!
					try {
						SynchronizeContactsService synchronizeContactService = serviceManager.getSynchronizeContactsService();
						cont = synchronizeContactService != null && !synchronizeContactService.isSynchronizationInProgress();
					} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
						logger.error("Exception", e);
						//do nothing
						cont = false;
					}

					if (cont) {
						PreferenceService preferencesService = serviceManager.getPreferenceService();
						if (preferencesService != null && preferencesService.isSyncContacts()) {
							try {
								ContactService c = serviceManager.getContactService();
								if (c != null) {
									//update contact names if changed!
									c.updateAllContactNamesFromAndroidContacts();
								}
							} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
								logger.error("Exception", e);
							}
						}
					}
					this.isRunning = false;
				}
			}
		};

		ListenerManager.synchronizeContactsListeners.add(new SynchronizeContactsListener() {
			@Override
			public void onStarted(SynchronizeContactsRoutine startedRoutine) {
				//disable contact observer
				serviceManager.getContext().getContentResolver().unregisterContentObserver(contentObserverChangeContactNames);
			}

			@Override
			public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
				//enable contact observer
				serviceManager.getContext().getContentResolver().registerContentObserver(
						ContactsContract.Contacts.CONTENT_URI,
						false,
						contentObserverChangeContactNames);
			}

			@Override
			public void onError(SynchronizeContactsRoutine finishedRoutine) {
				//enable contact observer
				serviceManager.getContext().getContentResolver().registerContentObserver(
						ContactsContract.Contacts.CONTENT_URI,
						false,
						contentObserverChangeContactNames);
			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		ListenerManager.contactTypingListeners.add(new ContactTypingListener() {
			@Override
			public void onContactIsTyping(ContactModel fromContact, boolean isTyping) {
				//update the conversations
				try {
					serviceManager.getConversationService()
							.setIsTyping(fromContact, isTyping);
				} catch (ThreemaException e) {
					logger.error("Exception", e);
				}
			}
		});

		ListenerManager.newSyncedContactListener.add(new NewSyncedContactsListener() {
			@Override
			public void onNew(List<ContactModel> contactModels) {
				NotificationService notificationService = serviceManager.getNotificationService();
				notificationService.showNewSyncedContactsNotification(contactModels);
			}
		});

		WebClientListenerManager.serviceListener.add(new WebClientServiceListener() {
			@Override
			public void onEnabled() {
				SessionWakeUpServiceImpl.getInstance()
					.processPendingWakeupsAsync();
			}

			@Override
			public void onStarted(
				@NonNull final WebClientSessionModel model,
				@NonNull final byte[] permanentKey,
				@NonNull final String browser
			) {
				logger.info( "WebClientListenerManager: onStarted", true);

				RuntimeUtil.runOnUiThread(() -> {
					String toastText = getAppContext().getString(R.string.webclient_new_connection_toast);
					if(model.getLabel() != null) {
						toastText += " (" + model.getLabel() +")";
					}
					Toast.makeText(getAppContext(), toastText, Toast.LENGTH_LONG).show();

					final Intent intent = new Intent(context, SessionAndroidService.class);

					if (SessionAndroidService.isRunning()) {
						intent.setAction(SessionAndroidService.ACTION_UPDATE);
						logger.info( "sending ACTION_UPDATE to SessionAndroidService");
						context.startService(intent);
					} else {
						logger.info( "SessionAndroidService not running...starting");
						intent.setAction(SessionAndroidService.ACTION_START);
						logger.info( "sending ACTION_START to SessionAndroidService");
						ContextCompat.startForegroundService(context, intent);
					}
				});
			}

			@Override
			public void onStateChanged(
				@NonNull final WebClientSessionModel model,
				@NonNull final WebClientSessionState oldState,
				@NonNull final WebClientSessionState newState
			) {
				logger.info( "WebClientListenerManager: onStateChanged", true);

				if (newState == WebClientSessionState.DISCONNECTED) {
					RuntimeUtil.runOnUiThread(() -> {
						logger.info("updating SessionAndroidService", true);
						if (SessionAndroidService.isRunning()) {
							final Intent intent = new Intent(context, SessionAndroidService.class);
							intent.setAction(SessionAndroidService.ACTION_UPDATE);
							logger.info("sending ACTION_UPDATE to SessionAndroidService");
							context.startService(intent);
						} else {
							logger.info("SessionAndroidService not running...not updating");
						}
					});
				}
			}

			@Override
			public void onStopped(@NonNull final WebClientSessionModel model, @NonNull final DisconnectContext reason) {
				logger.info( "WebClientListenerManager: onStopped", true);

				RuntimeUtil.runOnUiThread(() -> {
					if (SessionAndroidService.isRunning()) {
						final Intent intent = new Intent(context, SessionAndroidService.class);
						intent.setAction(SessionAndroidService.ACTION_STOP);
						logger.info( "sending ACTION_STOP to SessionAndroidService");
						context.startService(intent);
					} else {
						logger.info( "SessionAndroidService not running...not stopping");
					}
				});
			}
		});

		//called if a fcm message with a newer session received
		WebClientListenerManager.wakeUpListener.add(new WebClientWakeUpListener() {
			@Override
			public void onProtocolError() {
				RuntimeUtil.runOnUiThread(
					() -> Toast.makeText(getAppContext(), R.string.webclient_protocol_version_to_old, Toast.LENGTH_LONG).show()
				);
			}
		});

		VoipListenerManager.callEventListener.add(new VoipCallEventListener() {
			private final Logger logger = LoggingUtil.getThreemaLogger("VoipCallEventListener");

			@Override
			public void onRinging(String peerIdentity) {
				this.logger.debug("onRinging {}", peerIdentity);
			}

			@Override
			public void onStarted(String peerIdentity, boolean outgoing) {
				final String direction = outgoing ? "to" : "from";
				this.logger.info("Call {} {} started", direction, peerIdentity);
			}

			@Override
			public void onFinished(long callId, @NonNull String peerIdentity, boolean outgoing, int duration) {
				final String direction = outgoing ? "to" : "from";
				this.logger.info("Call {} {} finished", direction, peerIdentity);
				this.saveStatus(peerIdentity,
						outgoing,
						VoipStatusDataModel.createFinished(callId, duration),
						true);
			}

			@Override
			public void onRejected(long callId, String peerIdentity, boolean outgoing, byte reason) {
				final String direction = outgoing ? "to" : "from";
				this.logger.info("Call {} {} rejected (reason {})", direction, peerIdentity, reason);
				this.saveStatus(peerIdentity,
						// on rejected incoming, the outgoing was rejected!
						!outgoing,
						VoipStatusDataModel.createRejected(callId, reason),
						true);
			}

			@Override
			public void onMissed(long callId, String peerIdentity, boolean accepted, @Nullable Date date) {
				this.logger.info("Call from {} missed", peerIdentity);
				this.saveStatus(peerIdentity,
						false,
						VoipStatusDataModel.createMissed(callId, date),
						accepted);
			}

			@Override
			public void onAborted(long callId, String peerIdentity) {
				this.logger.info("Call to {} aborted", peerIdentity);
				this.saveStatus(peerIdentity,
						true,
						VoipStatusDataModel.createAborted(callId),
						true);
			}

			private void saveStatus(
				@NonNull String identity,
				boolean isOutbox,
				@NonNull VoipStatusDataModel status,
				boolean isRead
			) {
				try {
					if (serviceManager == null || serviceManager.getMessageService() == null) {
						this.logger.error("Could not save voip status, servicemanager or messageservice are null");
						return;
					}

					// If an incoming status message is not targeted at our own identity, something's wrong
					final String appIdentity = serviceManager.getIdentityStore().getIdentity();
					if (TestUtil.compare(identity, appIdentity) && !isOutbox) {
						this.logger.error("Could not save voip status (identity={}, appIdentity={}, outbox={})", identity, appIdentity, isOutbox);
						return;
					}

					final ContactModel contactModel = serviceManager.getContactService().getByIdentity(identity);
					final ContactMessageReceiver receiver = serviceManager.getContactService().createReceiver(contactModel);
					serviceManager.getMessageService().createVoipStatus(status, receiver, isOutbox, isRead);
				} catch (ThreemaException e) {
					logger.error("Exception", e);
				}
			}
		}, THREEMA_APPLICATION_LISTENER_TAG);

		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(serviceManager.getContext(), android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
			serviceManager.getContext().getContentResolver()
					.registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
							false,
							contentObserverChangeContactNames);
		}
	}

	public static boolean activityResumed(Activity currentActivity) {
		logger.debug("*** App ActivityResumed");
		if (serviceManager != null) {
			serviceManager.getActivityService().resume(currentActivity);
			return true;
		}
		return false;
	}

	public static void activityPaused(Activity pausedActivity) {
		logger.debug("*** App ActivityPaused");
		if (serviceManager != null) {
			serviceManager.getActivityService().pause(pausedActivity);
		}
	}

	public static void activityDestroyed(Activity destroyedActivity) {
		logger.debug("*** App ActivityDestroyed");
		if (serviceManager != null) {
			serviceManager.getActivityService().destroy(destroyedActivity);
		}
	}

	public static boolean activityUserInteract(Activity interactedActivity) {
		if (serviceManager != null) {
			serviceManager.getActivityService().userInteract(interactedActivity);
		}
		return true;
	}

	public static Date getLastLoggedIn() {
		return lastLoggedIn;
	}

	public static boolean isIsDeviceIdle() {
		return isDeviceIdle;
	}

	public static AppVersion getAppVersion() {
		return appVersion;
	}

	public static int getFeatureLevel() {
		return 3;
	}

	public static Context getAppContext() {
		return ThreemaApplication.context;
	}

	public static boolean getIPv6() {
		return ipv6;
	}
}
