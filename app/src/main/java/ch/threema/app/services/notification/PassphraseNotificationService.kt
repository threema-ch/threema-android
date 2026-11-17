package ch.threema.app.services.notification

import android.content.Context
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ch.threema.android.buildNotification
import ch.threema.app.R
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.services.NotificationPreferenceService

class PassphraseNotificationService(
    appContext: Context,
    notificationManagerCompat: NotificationManagerCompat,
    private val notificationPreferenceService: NotificationPreferenceService,
) : BaseNotificationService(appContext, notificationManagerCompat) {
    fun showNewMessagesPassphraseNotification() {
        val notification = buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT) {
            setSmallIcon(R.drawable.ic_notification_small)
            setContentTitle(appContext.getString(R.string.new_messages_locked))
            setContentText(appContext.getString(R.string.new_messages_locked_description))
            setTicker(appContext.getString(R.string.new_messages_locked))
            setCategory(NotificationCompat.CATEGORY_MESSAGE)
            setOnlyAlertOnce(false)
            setContentIntent(createPendingIntentForHomeActivity())
            setSound(notificationPreferenceService.getLegacyNotificationSound(), AudioManager.STREAM_NOTIFICATION)

            if (notificationPreferenceService.isLegacyNotificationVibrate()) {
                setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR)
            }
        }

        notify(
            notificationId = NotificationIDs.NEW_MESSAGE_LOCKED_NOTIFICATION_ID,
            notification = notification,
            defaultChannelId = NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT,
        )
    }

    fun cancelNewMessagesPassphraseNotification() {
        cancel(NotificationIDs.NEW_MESSAGE_LOCKED_NOTIFICATION_ID)
    }
}
