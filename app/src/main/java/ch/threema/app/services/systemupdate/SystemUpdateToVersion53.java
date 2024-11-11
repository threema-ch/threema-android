/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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

import androidx.core.app.NotificationManagerCompat;

import org.slf4j.Logger;

import ch.threema.app.BuildConfig;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.UpdateSystemService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;

/**
 * remove old pre-API26 notification channels
 */
public class SystemUpdateToVersion53 implements UpdateSystemService.SystemUpdate {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion53");

	public SystemUpdateToVersion53() { }

	@Override
	public boolean runDirectly() {
		NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(ThreemaApplication.getAppContext());
		try {
			notificationManagerCompat.deleteNotificationChannel("passphrase_service");
			notificationManagerCompat.deleteNotificationChannel("webclient");
			notificationManagerCompat.deleteNotificationChannel(BuildConfig.APPLICATION_ID + "passphrase_service");
			notificationManagerCompat.deleteNotificationChannel(BuildConfig.APPLICATION_ID + "webclient");
		} catch (Exception e) {
			logger.error("Exception", e);
		}
		return true;
	}

	@Override
	public boolean runAsync() {
		return true;
	}

	@Override
	public String getText() {
		return "version 53";
	}
}
