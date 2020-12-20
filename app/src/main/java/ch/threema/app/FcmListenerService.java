/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.text.format.DateUtils;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;

import ch.threema.app.jobs.ReConnectJobService;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.receivers.AlarmManagerBroadcastReceiver;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.NotificationServiceImpl;
import ch.threema.app.services.PollingHelper;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.PreferenceServiceImpl;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl;

public class FcmListenerService extends FirebaseMessagingService {
	private static final Logger logger = LoggerFactory.getLogger(FcmListenerService.class);

	private static final int RECONNECT_JOB = 89;

	private static final String WEBCLIENT_SESSION = "wcs";
	private static final String WEBCLIENT_TIMESTAMP = "wct";
	private static final String WEBCLIENT_VERSION = "wcv";
	private static final String WEBCLIENT_AFFILIATION_ID = "wca";

	private static final int NOTIFICATION_TYPE_LOCKED = 3;

	private PollingHelper pollingHelper = null;

	@Override
	public void onNewToken(String token) {
		logger.info("New FCM token received");

		// Fetch updated Instance ID token and notify our app's server of any changes (if applicable).
		PushUtil.clearPushTokenSentDate(this);
		PushUtil.scheduleSendPushTokenToServer(this);
	}

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		logger.info("Handling incoming FCM intent.");

		RuntimeUtil.runInWakelock(getApplicationContext(), DateUtils.MINUTE_IN_MILLIS * 10, "FcmListenerService", () -> processFcmMessage(remoteMessage));
	}

	private void processFcmMessage(RemoteMessage remoteMessage) {
		logger.info("Received FCM message: {}", remoteMessage.getMessageId());

		// from should be equal to R.string.gcm_sender_id
		String from = remoteMessage.getFrom();
		Map<String, String> data = remoteMessage.getData();

		if (pollingHelper == null) {
			pollingHelper = new PollingHelper(this, "FCM");
		}

		// Log message sent time
		try {
			Date sentDate = new Date(remoteMessage.getSentTime());

			logger.info("*** Message sent     : " + sentDate.toString(), true);
			logger.info("*** Message received : " + new Date().toString(), true);
			logger.info("*** Original priority: " + remoteMessage.getOriginalPriority());
			logger.info("*** Current priority: " + remoteMessage.getPriority());
		} catch (Exception ignore) {
		}

		// Message notifications
		if (remoteMessage.getCollapseKey() != null && remoteMessage.getCollapseKey().equals("new_message")) {
			// Post notification of received message.
			sendNotification();
		}

		// Webclient notifications
		if (data != null && data.containsKey(WEBCLIENT_SESSION) && data.containsKey(WEBCLIENT_TIMESTAMP)) {
			final String session = data.get(WEBCLIENT_SESSION);
			final String timestamp = data.get(WEBCLIENT_TIMESTAMP);
			final String version = data.get(WEBCLIENT_VERSION);
			final String affiliationId = data.get(WEBCLIENT_AFFILIATION_ID);
			if (session != null && !session.isEmpty() && timestamp != null && !timestamp.isEmpty()) {
				logger.debug("Received FCM webclient wakeup for session {}", session);

				final Thread t = new Thread(() -> {
					logger.info("Trying to wake up webclient session {}", session);

					// Parse version number
					Integer versionNumber = null;
					if (version != null) { // Can be null during beta, if an old client doesn't yet send the version field
						try {
							versionNumber = Integer.parseInt(version);
						} catch (NumberFormatException e) {
							// Version number was sent but is not a valid u16.
							// We should probably throw the entire wakeup notification away.
							logger.error("Could not parse webclient protocol version number: " + e);
							return;
						}
					}

					// Try to wake up session
					SessionWakeUpServiceImpl.getInstance()
							.resume(session, versionNumber == null ? 0 : versionNumber, affiliationId);
				});
				t.setName("webclient-gcm-wakeup");
				t.start();
			}
		}
	}

	private void displayAdHocNotification(int type) {
		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		NotificationService notificationService;
		if (serviceManager != null) {
			notificationService = serviceManager.getNotificationService();
		} else {
			// Because the master key is locked, there is no preference service object.
			// We need to create one for ourselves so that we can read the user's notification prefs
			// (which are unencrypted).

			//create a temporary service class (with some implementations) to use the showMasterKeyLockedNewMessageNotification

			PreferenceStore ps = new PreferenceStore(this.getApplicationContext(), ThreemaApplication.getMasterKey());
			PreferenceService p = new PreferenceServiceImpl(this.getApplicationContext(), ps);

			Context c = this.getApplicationContext();

			notificationService = new NotificationServiceImpl(
				c,
				new LockAppService() {
					@Override
					public boolean isLockingEnabled() {
						return false;
					}

					@Override
					public boolean unlock(String pin) {
						return false;
					}

					@Override
					public void lock() {
					}

					@Override
					public boolean checkLock() {
						return false;
					}

					@Override
					public boolean isLocked() {
						return false;
					}

					@Override
					public LockAppService resetLockTimer(boolean restartAfterReset) {
						return null;
					}

					@Override
					public void addOnLockAppStateChanged(OnLockAppStateChanged c) {
					}

					@Override
					public void removeOnLockAppStateChanged(OnLockAppStateChanged c) {
					}
				},
				new DeadlineListService() {
					@Override
					public void add(String uid, long timeout) {
					}

					@Override
					public void init() {
					}

					@Override
					public boolean has(String uid) {
						return false;
					}

					@Override
					public void remove(String uid) {
					}

					@Override
					public long getDeadline(String uid) {
						return 0;
					}

					@Override
					public int getSize() {
						return 0;
					}

					@Override
					public void clear() {
					}
				},
				new DeadlineListService() {
					@Override
					public void add(String uid, long timeout) {
					}

					@Override
					public void init() {
					}

					@Override
					public boolean has(String uid) {
						return false;
					}

					@Override
					public void remove(String uid) {
					}

					@Override
					public long getDeadline(String uid) {
						return 0;
					}

					@Override
					public int getSize() {
						return 0;
					}

					@Override
					public void clear() {
					}
				},
				new DeadlineListService() {
					@Override
					public void add(String uid, long timeout) {}

					@Override
					public void init() {}

					@Override
					public boolean has(String uid) { return false; }

					@Override
					public void remove(String uid) {}

					@Override
					public long getDeadline(String uid) { return 0; }

					@Override
					public int getSize() { return 0; }

					@Override
					public void clear() {}
				},
				p,
				new RingtoneService() {
					@Override
					public void init() {
					}

					@Override
					public void setRingtone(String uniqueId, Uri ringtoneUri) {
					}

					@Override
					public Uri getRingtoneFromUniqueId(String uniqueId) {
						return null;
					}

					@Override
					public boolean hasCustomRingtone(String uniqueId) {
						return false;
					}

					@Override
					public void removeCustomRingtone(String uniqueId) {
					}

					@Override
					public void resetRingtones(Context context) {
					}

					@Override
					public Uri getContactRingtone(String uniqueId) {
						return null;
					}

					@Override
					public Uri getGroupRingtone(String uniqueId) {
						return null;
					}

					@Override
					public Uri getVoiceCallRingtone(String uniqueId) {
						return null;
					}

					@Override
					public Uri getDefaultContactRingtone() {
						return null;
					}

					@Override
					public Uri getDefaultGroupRingtone() {
						return null;
					}

					@Override
					public boolean isSilent(String uniqueId, boolean isGroup) {
						return false;
					}
				});
		}

		if (notificationService != null) {
			notificationService.showMasterKeyLockedNewMessageNotification();
		}
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		logger.debug("*** Service task removed");
		super.onTaskRemoved(rootIntent);
	}

	private void sendNotification() {
		// check if background data is disabled before attempting to connect
		ConnectivityManager mgr = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = mgr.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.getDetailedState() == NetworkInfo.DetailedState.BLOCKED) {
			logger.warn("Network blocked (background data disabled?)");
			// The same GCM message may arrive twice (due to a network change). so we simply ignore messages that we were unable to fetch due to a blocked network
			// Simply schedule a poll when the device is back online
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				JobScheduler js = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);

				js.cancel(RECONNECT_JOB);

				JobInfo job = new JobInfo.Builder(RECONNECT_JOB,
						new ComponentName(this, ReConnectJobService.class))
						.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
						.setRequiresCharging(false)
						.build();

				if (js.schedule(job) != JobScheduler.RESULT_SUCCESS) {
					logger.error("Job scheduling failed");
				}
			}
			return;
		}

		if (networkInfo == null) {
			logger.warn("No network info available");
		}

		//recheck after one minute
		AlarmManagerBroadcastReceiver.requireLoggedInConnection(
				this,
				(int) DateUtils.MINUTE_IN_MILLIS
		);

		PreferenceStore preferenceStore = new PreferenceStore(this, null);
		PreferenceServiceImpl preferenceService = new PreferenceServiceImpl(this, preferenceStore);

		if (ThreemaApplication.getMasterKey() != null &&
			ThreemaApplication.getMasterKey().isLocked() &&
			preferenceService.isMasterKeyNewMessageNotifications()) {

			displayAdHocNotification(NOTIFICATION_TYPE_LOCKED);
		}

		if (!pollingHelper.poll(true)) {
			logger.warn("Unable to establish connection");
		}
	}
}
