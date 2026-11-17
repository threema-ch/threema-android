package ch.threema.app.services.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import ch.threema.app.home.HomeActivity
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("BaseNotificationService")

abstract class BaseNotificationService(
    protected val appContext: Context,
    protected val notificationManagerCompat: NotificationManagerCompat,
) {
    protected fun notify(
        notificationId: Int,
        notification: Notification,
        schema: NotificationSchema? = null,
        defaultChannelId: String? = null,
        tag: String? = null,
    ) {
        try {
            notificationManagerCompat.notify(tag, notificationId, notification)
        } catch (e: SecurityException) {
            // some phones revoke access to selected sound files for notifications after an OS upgrade
            logger.error("Can't show notification (notificationId={}). Falling back to default ringtone", notificationId, e)

            if (defaultChannelId == NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT ||
                defaultChannelId == NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT ||
                defaultChannelId == NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS ||
                defaultChannelId == NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS
            ) {
                val soundUri = schema?.soundUri
                if (soundUri != null && soundUri != Settings.System.DEFAULT_NOTIFICATION_URI && soundUri != Settings.System.DEFAULT_RINGTONE_URI) {
                    val newNotificationBuilder = NotificationCompat.Builder(appContext, notification)

                    // post notification to a silent channel
                    newNotificationBuilder.setChannelId(NotificationChannels.NOTIFICATION_CHANNEL_CHAT_UPDATE)
                    try {
                        notificationManagerCompat.notify(tag, notificationId, newNotificationBuilder.build())
                    } catch (e2: Exception) {
                        logger.error("Failed to show fallback notification", e2)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to notify (notificationId={})", notificationId, e)
        }
    }

    protected fun createPendingIntentForHomeActivity(): PendingIntent =
        createPendingIntentWithTaskStack(
            intent = HomeActivity.createIntent(appContext)
                .setFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                ),
            requestCode = NotificationRequestCodes.HOME_ACTIVITY,
        )

    protected fun createPendingIntentWithTaskStack(intent: Intent, requestCode: Int): PendingIntent =
        TaskStackBuilder.create(appContext)
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(requestCode, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE or FLAG_ONE_SHOT)!!

    protected fun cancel(notificationId: Int, tag: String? = null) {
        try {
            notificationManagerCompat.cancel(tag, notificationId)
        } catch (e: Exception) {
            logger.warn("Failed to cancel notification {}", notificationId, e)
        }
    }

    protected fun hasNotificationPermission(): Boolean =
        notificationManagerCompat.areNotificationsEnabled()
}
