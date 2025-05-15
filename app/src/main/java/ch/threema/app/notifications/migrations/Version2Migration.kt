/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.notifications.migrations

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import androidx.core.app.NotificationManagerCompat
import ch.threema.app.R
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS
import ch.threema.app.notifications.NotificationChannels.VIBRATE_PATTERN_INCOMING_CALL
import ch.threema.app.notifications.getRingtoneUri
import ch.threema.app.notifications.migrateOrCreateChannel
import ch.threema.app.notifications.setSound
import ch.threema.app.utils.RingtoneUtil

/**
 * This migration deletes the (v1) notification channel for incoming calls and replaces it with a new one (v2) with a different id,
 * in order to set the sound and vibration to match the custom settings that were previously stored in shared preferences.
 */
class Version2Migration : NotificationChannelMigration {
    override fun migrate(
        context: Context,
        sharedPreferences: SharedPreferences,
        notificationManager: NotificationManagerCompat,
    ) = with(notificationManager) {
        val ringtone = sharedPreferences.getRingtoneUri(context.getString(R.string.preferences__voip_ringtone))
            ?: RingtoneUtil.THREEMA_CALL_RINGTONE_URI
        val vibrate = sharedPreferences.getBoolean(context.getString(R.string.preferences__voip_vibration), true)

        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS_V1,
            newId = NOTIFICATION_CHANNEL_INCOMING_CALLS,
            modify = {
                setVibrationEnabled(vibrate)
                setSound(ringtone, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            },
            create = {
                setName(context.getString(R.string.incoming_calls))
                setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                setLightsEnabled(true)
                setVibrationPattern(VIBRATE_PATTERN_INCOMING_CALL)
                setShowBadge(false)
                setVibrationEnabled(vibrate)
                setSound(ringtone, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            },
        )
    }
}
