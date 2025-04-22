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
import androidx.core.app.NotificationManagerCompat
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.migrateChannel

/**
 * This migration ensures that the channels for new messages and new group messages use the notification light.
 */
class Version3Migration : NotificationChannelMigration {
    override fun migrate(
        context: Context,
        sharedPreferences: SharedPreferences,
        notificationManager: NotificationManagerCompat,
    ) = with(notificationManager) {
        migrateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT_V1,
            newId = NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT,
            modify = {
                setLightsEnabled(true)
            },
        )
        migrateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT_V1,
            newId = NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT,
            modify = {
                setLightsEnabled(true)
            },
        )
    }
}
