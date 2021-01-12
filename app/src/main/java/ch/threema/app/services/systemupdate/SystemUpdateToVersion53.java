/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

package ch.threema.app.services.systemupdate;

import android.app.NotificationManager;
import android.content.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LogUtil;

/**
 * remove old pre-API26 notification channels
 */
public class SystemUpdateToVersion53 extends UpdateToVersion implements UpdateSystemService.SystemUpdate {
	private static final Logger logger = LoggerFactory.getLogger(SystemUpdateToVersion53.class);

	public SystemUpdateToVersion53() { }

	@Override
	public boolean runDirectly() {
		if (ConfigUtils.supportsNotificationChannels()) {
			NotificationManager notificationManager = (NotificationManager) ThreemaApplication.getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				try {
					notificationManager.deleteNotificationChannel("passphrase_service");
					notificationManager.deleteNotificationChannel("webclient");
					notificationManager.deleteNotificationChannel(BuildConfig.APPLICATION_ID + "passphrase_service");
					notificationManager.deleteNotificationChannel(BuildConfig.APPLICATION_ID + "webclient");
				} catch (Exception e) {
					logger.error("Exception", e);
				}
			}
		}
		return true;
	}

	@Override
	public boolean runASync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 53";
	}
}
