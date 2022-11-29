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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Build;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.core.app.FixedJobIntentService;
import androidx.core.app.NotificationCompat;
import ch.threema.app.activities.HomeActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.notifications.NotificationBuilderWrapper;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.localcrypto.MasterKey;

import static ch.threema.app.services.NotificationService.NOTIFICATION_CHANNEL_NOTICE;
import static ch.threema.app.utils.IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE;

public class AutostartService extends FixedJobIntentService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("AutostartService");
	private static final int JOB_ID = 2000;

	public static void enqueueWork(Context context, Intent work) {
		enqueueWork(context, AutostartService.class, JOB_ID, work);
	}

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		logger.info("Processing AutoStart - start");

		MasterKey masterKey = ThreemaApplication.getMasterKey();
		if (masterKey == null) {
			logger.error("Unable to launch app");
			stopSelf();
			return;
		}

		// check if masterkey needs a password and issue a notification if necessary
		if (masterKey.isLocked()) {
			NotificationCompat.Builder notificationCompat =
				new NotificationBuilderWrapper(this, NOTIFICATION_CHANNEL_NOTICE, null)
					.setSmallIcon(R.drawable.ic_notification_small)
					.setContentTitle(getString(R.string.master_key_locked))
					.setContentText(getString(R.string.master_key_locked_notify_description))
					.setTicker(getString(R.string.master_key_locked))
					.setCategory(NotificationCompat.CATEGORY_SERVICE);

			Intent notificationIntent = IntentDataUtil.createActionIntentHideAfterUnlock(new Intent(this, HomeActivity.class));
			notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PENDING_INTENT_FLAG_IMMUTABLE);
			notificationCompat.setContentIntent(pendingIntent);
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(ThreemaApplication.MASTER_KEY_LOCKED_NOTIFICATION_ID, notificationCompat.build());
		}

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			logger.error("Service manager not available");
			stopSelf();
			return;
		}

		// check if background data is disabled and issue a warning
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			if (connMgr != null && connMgr.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
				NotificationService notificationService = serviceManager.getNotificationService();
				if (notificationService != null) {
					notificationService.showNetworkBlockedNotification(false);
				}
			}
		}

		// fixes https://issuetracker.google.com/issues/36951052
		PreferenceService preferenceService = serviceManager.getPreferenceService();
		if (preferenceService != null) {
			// reset feature level
			preferenceService.setTransmittedFeatureLevel(0);

			//auto fix failed sync account
			if (preferenceService.isSyncContacts()) {
				UserService userService = serviceManager.getUserService();
				if (userService != null && !userService.checkAccount()) {
					//create account
					userService.getAccount(true);
					userService.enableAccountAutoSync(true);
				}
			}
		}

		logger.info("Processing AutoStart - end");
	}
}
