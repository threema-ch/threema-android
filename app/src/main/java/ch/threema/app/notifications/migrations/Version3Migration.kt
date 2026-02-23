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
