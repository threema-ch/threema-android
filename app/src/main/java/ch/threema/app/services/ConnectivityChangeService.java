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

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.core.app.FixedJobIntentService;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.base.utils.LoggingUtil;

public class ConnectivityChangeService extends FixedJobIntentService {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ConnectivityChangeService");

	private static final int MESSAGE_SEND_TIME = 30 * 1000;
	private static final int JOB_ID = 2001;

	public static void enqueueWork(Context context, Intent work) {
		if (work != null) {
			try {
				enqueueWork(context, ConnectivityChangeService.class, JOB_ID, work);
			} catch (IllegalArgumentException e) {
				logger.error("Unable to enqueue work", e);
			}
		}
	}

	@Override
	protected void onHandleWork(@NonNull Intent intent) {
		boolean wasOnline = false;

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();

		if (serviceManager != null) {
			PreferenceService preferenceService = serviceManager.getPreferenceService();
			boolean online = serviceManager.getDeviceService().isOnline();

			if (preferenceService != null) {
				wasOnline = preferenceService.getLastOnlineStatus();
				preferenceService.setLastOnlineStatus(online);
			}

			Bundle extras = intent.getExtras();

			if (extras != null) {
				NetworkInfo networkInfo = (NetworkInfo) extras.get(ConnectivityManager.EXTRA_NETWORK_INFO);
				if (networkInfo != null) {
					logger.info(networkInfo.toString());
				}
			}

			if (online != wasOnline) {
				logger.info("Device is now {}", online ? "ONLINE" : "OFFLINE");

				/* if there are pending messages in the queue, go online for a moment to send them */
				try {
					if (serviceManager.getMessageQueue().getQueueSize() > 0) {
						logger.info("Messages in queue; acquiring connection");
						serviceManager.getLifetimeService().acquireConnection("connectivityChange");
						serviceManager.getLifetimeService().releaseConnectionLinger("connectivityChange", MESSAGE_SEND_TIME);
					}

					/* if no backup was created in due time, do it now. The JobScheduler will handle connectivity changes in Lollipop+  */
				} catch (Exception e) {
					logger.error("Error", e);
				}
			}
		}
	}
}
