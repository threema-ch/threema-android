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

package ch.threema.app.notifications;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import ch.threema.app.R;

public class NotificationUtil {
    public static final String TAG = "NotificationUtil";

    /**
     * Get Android N notification importance
     *
     * @param context Context
     * @return Notification importance
     */
    @TargetApi(Build.VERSION_CODES.N)
    public static int getNotificationImportance(Context context) {
        switch (getNotificationPriority(context)) {
            case NotificationCompat.PRIORITY_MAX:
                return NotificationManagerCompat.IMPORTANCE_MAX;
            case NotificationCompat.PRIORITY_HIGH:
                return NotificationManagerCompat.IMPORTANCE_HIGH;
            case NotificationCompat.PRIORITY_LOW:
                return NotificationManagerCompat.IMPORTANCE_LOW;
            default:
                return NotificationManagerCompat.IMPORTANCE_DEFAULT;
        }
    }

    /**
     * Get Android pre-N notification priority
     *
     * @param context Context
     * @return Notification priority
     */
    public static int getNotificationPriority(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        try {
            return Integer.parseInt(sharedPreferences.getString(context.getString(R.string.preferences__notification_priority), "1"));
        } catch (NumberFormatException | NullPointerException e) {
            return NotificationCompat.PRIORITY_HIGH;
        }
    }

    public static boolean isVoiceCallVibrate(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        return sharedPreferences.getBoolean(context.getString(R.string.preferences__voip_vibration), true);
    }
}
