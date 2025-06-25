/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.systemupdates.updates;

import android.content.Context;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import ch.threema.app.BuildConfig;
import ch.threema.base.utils.LoggingUtil;

/**
 * remove old pre-API26 notification channels
 */
public class SystemUpdateToVersion53 implements SystemUpdate {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SystemUpdateToVersion53");

    @NonNull
    private final Context context;

    public SystemUpdateToVersion53(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        try {
            notificationManagerCompat.deleteNotificationChannel("passphrase_service");
            notificationManagerCompat.deleteNotificationChannel("webclient");
            notificationManagerCompat.deleteNotificationChannel(BuildConfig.APPLICATION_ID + "passphrase_service");
            notificationManagerCompat.deleteNotificationChannel(BuildConfig.APPLICATION_ID + "webclient");
        } catch (Exception e) {
            logger.error("Exception", e);
        }
    }

    @Override
    public int getVersion() {
        return 53;
    }
}
