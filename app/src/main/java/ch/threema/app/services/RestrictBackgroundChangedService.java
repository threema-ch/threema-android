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

package ch.threema.app.services;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.FixedJobIntentService;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;

@TargetApi(Build.VERSION_CODES.N)
public class RestrictBackgroundChangedService extends FixedJobIntentService {

	private static final int JOB_ID = 2003;

	public static void enqueueWork(Context context, Intent work) {
		enqueueWork(context, RestrictBackgroundChangedService.class, JOB_ID, work);
	}

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connMgr != null) {

			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
				NotificationService notificationService = serviceManager.getNotificationService();

				if (notificationService != null) {
					switch (connMgr.getRestrictBackgroundStatus()) {
						case android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED:
							// Background data usage is blocked for this app. Wherever possible,
							// the app should also use less data in the foreground.
							notificationService.showNetworkBlockedNotification(false);
							break;
						case android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED:
							// Data Saver is disabled. Since the device is connected to a
							// metered network, the app should use less data wherever possible.
						case android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED:
							// The app is whitelisted. Wherever possible,
							// the app should use less data in the foreground and background.
							notificationService.cancelNetworkBlockedNotification();
							break;
					}
				}
			}
		}
	}
}
