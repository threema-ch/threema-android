/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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
import ch.threema.app.services.DeadlineListService
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.LoggingUtil
import kotlin.random.Random

private val logger = LoggingUtil.getThreemaLogger("ForwardSecurityNotificationManager")

class ForwardSecurityNotificationManager(
    private val context: Context,
    private val hiddenChatListService: DeadlineListService,
) {
    private val notificationIdMap = HashMap<String, Int>()

    @SuppressLint("MissingPermission")
    fun showForwardSecurityNotification(messageReceiver: MessageReceiver<*>) {
        val contentText = getNotificationContextText(messageReceiver)

        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            context,
            NotificationChannels.NOTIFICATION_CHANNEL_FORWARD_SECURITY
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
                Manifest.permission.POST_NOTIFICATIONS
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
            PendingIntent.FLAG_UPDATE_CURRENT or IntentDataUtil.PENDING_INTENT_FLAG_IMMUTABLE
        )
    }

    private fun getNotificationContextText(messageReceiver: MessageReceiver<*>): String {
        // Do not include name in case of a hidden contact. The intent remains the same, so clicking
        // the notification will still result in opening the correct chat.
        return if (hiddenChatListService.has(messageReceiver.uniqueIdString)) {
            context.getString(R.string.forward_security_notification_rejected_text_generic)
        } else {
            when (messageReceiver) {
                is ContactMessageReceiver -> context.getString(
                    R.string.forward_security_notification_rejected_text_contact,
                    messageReceiver.displayName
                )

                is GroupMessageReceiver -> context.getString(
                    R.string.forward_security_notification_rejected_text_group,
                    messageReceiver.displayName
                )

                // Note that messages in distribution lists are rejected in the corresponding 1:1
                // chat and therefore handled via contact message receiver.
                else -> throw IllegalArgumentException("Cannot show notification for unexpected receiver type: " + messageReceiver.type)
            }
        }
    }
}
