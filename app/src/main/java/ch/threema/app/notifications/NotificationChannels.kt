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

package ch.threema.app.notifications

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import ch.threema.base.utils.LoggingUtil
import org.slf4j.Logger

object NotificationChannels {
    val logger: Logger = LoggingUtil.getThreemaLogger("NotificationChannels")

    /*
     * static notification channels
     */

    /** channel for default chat notifications */
    const val NOTIFICATION_CHANNEL_CHATS_DEFAULT = "chats_default_v2"

    /** channel for default chat notifications */
    const val NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT = "group_chats_default_v2"

    /** channel for incoming 1:1 calls */
    const val NOTIFICATION_CHANNEL_INCOMING_CALLS = "incoming_call_v2"
    const val NOTIFICATION_CHANNEL_IN_CALL = "ongoing_call"

    /** channel for incoming group calls */
    const val NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS = "incoming_group_call"
    const val NOTIFICATION_CHANNEL_PASSPHRASE = "passphrase"
    const val NOTIFICATION_CHANNEL_WEBCLIENT = "webclient"
    const val NOTIFICATION_CHANNEL_ALERT = "alert"
    const val NOTIFICATION_CHANNEL_NOTICE = "notice"
    const val NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS = "data_backup"
    const val NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS = "new_contact_found"
    const val NOTIFICATION_CHANNEL_FORWARD_SECURITY = "forward_security_alert"

    /** channel for quiet chat notification updates (e.g. when changing from regular to PIN locked) */
    const val NOTIFICATION_CHANNEL_CHAT_UPDATE = "silent_chat_update"
    const val NOTIFICATION_CHANNEL_WORK_SYNC = "work_sync"

    /*
     * these channels are created on demand only
     */

    /** channel will be created on-the-fly by ThreemaPushService */
    const val NOTIFICATION_CHANNEL_THREEMA_PUSH = "threema_push"

    /** channel will be created on-the-fly by VoiceMessagePlayerService */
    const val NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER = "voicemessage_player"

    /*
     * notification channel groups
     */

    const val NOTIFICATION_CHANNELGROUP_CHAT = "chats"
    const val NOTIFICATION_CHANNELGROUP_GROUP_CHAT = "group_chats"

    /*
     * Default vibration patterns
     */

    @JvmField
    val VIBRATE_PATTERN_REGULAR = longArrayOf(0, 250, 250, 250)

    @JvmField
    val VIBRATE_PATTERN_INCOMING_CALL = longArrayOf(0, 1000, 1000, 0)

    @JvmField
    val VIBRATE_PATTERN_GROUP_CALL = longArrayOf(0, 2000)

    /**
     * Ensure notification channels and groups are created. Upgrade if necessary
     */
    fun createOrMigrateNotificationChannels(context: Context) {
        NotificationChannelSetup(context).createOrUpdateNotificationChannels()
    }

    fun doesPerConversationChannelExist(context: Context, uid: String): Boolean =
        NotificationManagerCompat.from(context).exists(uid)

    fun deletePerConversationChannel(context: Context, uid: String) {
        NotificationManagerCompat.from(context).safelyDeleteChannel(uid)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getPerConversationChannelSettingsIntent(
        context: Context,
        chatName: String?,
        uid: String,
        isGroupChat: Boolean,
    ): Intent {
        val notificationManager = NotificationManagerCompat.from(context)

        // check if a channel for specified ID exists - if not, create it before creating the intent
        if (!notificationManager.exists(uid)) {
            notificationManager.createChannel(
                channelId = uid,
                channelName = chatName,
                channelImportance = NotificationManagerCompat.IMPORTANCE_MAX,
                parentChannelId = if (isGroupChat) NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT else NOTIFICATION_CHANNEL_CHATS_DEFAULT,
                groupId = if (isGroupChat) NOTIFICATION_CHANNELGROUP_GROUP_CHAT else NOTIFICATION_CHANNELGROUP_CHAT,
            ) {
                setLightsEnabled(true)
                setVibrationEnabled(true)
                setVibrationPattern(VIBRATE_PATTERN_REGULAR)
                setShowBadge(true)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.USAGE_NOTIFICATION)
            }
        }

        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, uid)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intent.putExtra(Settings.EXTRA_CONVERSATION_ID, uid)
        }
        return intent
    }

    fun recreateNotificationChannels(context: Context) {
        with(NotificationChannelSetup(context)) {
            deleteAll()
            createOrUpdateNotificationChannels()
        }
    }
}
