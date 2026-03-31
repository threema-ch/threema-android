package ch.threema.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ch.threema.app.R
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.base.utils.getThreemaLogger
import kotlin.random.Random

private val logger = getThreemaLogger("ForwardSecurityNotificationManager")

class ForwardSecurityNotificationManager(
    private val context: Context,
    private val conversationCategoryService: ConversationCategoryService,
    private val preferenceService: PreferenceService,
) {
    private val notificationIdMap = HashMap<String, Int>()

    @SuppressLint("MissingPermission")
    fun showForwardSecurityNotification(messageReceiver: MessageReceiver<*>) {
        val contentText = getNotificationContextText(messageReceiver)

        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            NotificationChannels.NOTIFICATION_CHANNEL_FORWARD_SECURITY,
        )
            .setContentTitle(context.getString(R.string.forward_security_notification_rejected_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_baseline_key_off_24)
            .setLocalOnly(true)
            .setContentIntent(getIntent(messageReceiver))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setOngoing(false)

        val notificationId = getNotificationId(messageReceiver)

        NotificationManagerCompat.from(context).apply {
            if (hasNotificationPermission()) {
                logger.info("Displaying fs reject notification with id {}", notificationId)
                notify(notificationId, builder.build())
            } else {
                logger.warn("Cannot show forward security notification due to missing permission")
            }
        }
    }

    private fun hasNotificationPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun getNotificationId(messageReceiver: MessageReceiver<*>): Int {
        return notificationIdMap[messageReceiver.uniqueIdString] ?: run {
            val newId = Random.nextInt()
            notificationIdMap[messageReceiver.uniqueIdString] = newId
            newId
        }
    }

    private fun getIntent(messageReceiver: MessageReceiver<*>): PendingIntent {
        val intent = Intent(context, ComposeMessageActivity::class.java)
        messageReceiver.prepareIntent(intent)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        return PendingIntent.getActivity(
            context,
            // Use unique request code to prevent that pending intent extras are overridden
            messageReceiver.uniqueIdString.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getNotificationContextText(messageReceiver: MessageReceiver<*>): String {
        // Do not include name in case of a hidden contact. The intent remains the same, so clicking
        // the notification will still result in opening the correct chat.
        return if (conversationCategoryService.isPrivateChat(messageReceiver.uniqueIdString)) {
            context.getString(R.string.forward_security_notification_rejected_text_generic)
        } else {
            when (messageReceiver) {
                is ContactMessageReceiver -> context.getString(
                    R.string.forward_security_notification_rejected_text_contact,
                    messageReceiver.getDisplayName(preferenceService.getContactNameFormat()),
                )

                is GroupMessageReceiver -> context.getString(
                    R.string.forward_security_notification_rejected_text_group,
                    messageReceiver.getDisplayName(preferenceService.getContactNameFormat()),
                )

                // Note that messages in distribution lists are rejected in the corresponding 1:1
                // chat and therefore handled via contact message receiver.
                else -> throw IllegalArgumentException("Cannot show notification for unexpected receiver type: " + messageReceiver.type)
            }
        }
    }
}
