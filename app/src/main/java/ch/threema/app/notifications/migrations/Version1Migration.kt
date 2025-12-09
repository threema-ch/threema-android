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
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ch.threema.android.setSound
import ch.threema.app.R
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNELGROUP_CHAT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNELGROUP_GROUP_CHAT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_ALERT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_CHAT_UPDATE
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_FORWARD_SECURITY
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_IN_CALL
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_NOTICE
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_PASSPHRASE
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_THREEMA_PUSH
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_WEBCLIENT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_WORK_SYNC
import ch.threema.app.notifications.NotificationChannels.VIBRATE_PATTERN_REGULAR
import ch.threema.app.notifications.createChannel
import ch.threema.app.notifications.createGroup
import ch.threema.app.notifications.getRingtoneUri
import ch.threema.app.notifications.migrateChannel
import ch.threema.app.notifications.migrateOrCreateChannel
import ch.threema.app.notifications.safelyDeleteChannel
import ch.threema.app.notifications.safelyDeleteChannelGroup
import ch.threema.app.utils.ConfigUtils

/**
 * This migration introduces the notification channel groups "Chats" and "Group Chats".
 * It also migrates all channels that may have existed prior to our notification channel versioning and ensures that
 * all of the required channels exist, deriving properties from shared preferences where needed to reflect the user's choices.
 */
class Version1Migration : NotificationChannelMigration {
    override fun migrate(
        context: Context,
        sharedPreferences: SharedPreferences,
        notificationManager: NotificationManagerCompat,
    ) = with(notificationManager) {
        // destination groups must be created *before* we can migrate
        createGroup(NOTIFICATION_CHANNELGROUP_CHAT, context.getString(R.string.chats))
        createGroup(NOTIFICATION_CHANNELGROUP_GROUP_CHAT, context.getString(R.string.group_chats))

        // migrate all channels that are part of a group
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_CHAT_UPDATE_PRE,
            newId = NOTIFICATION_CHANNEL_CHAT_UPDATE,
            modify = {
                setGroup(NOTIFICATION_CHANNELGROUP_CHAT)
            },
            create = {
                setName(context.getString(R.string.chat_updates))
                setImportance(NotificationManagerCompat.IMPORTANCE_LOW)
                setLightsEnabled(false)
                setVibrationEnabled(false)
                setShowBadge(false)
                setSound(null, null)
            },
        )

        // migrate all channels that are not part of a group (they will just get a new ID), or create them from scratch
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_PASSPHRASE_PRE,
            newId = NOTIFICATION_CHANNEL_PASSPHRASE,
        ) {
            setName(context.getString(R.string.passphrase_service_name))
            setImportance(NotificationManagerCompat.IMPORTANCE_LOW)
            setDescription(context.getString(R.string.passphrase_service_description))
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_WEBCLIENT_PRE,
            newId = NOTIFICATION_CHANNEL_WEBCLIENT,
        ) {
            setName(context.getString(R.string.webclient))
            setImportance(NotificationManagerCompat.IMPORTANCE_LOW)
            setDescription(context.getString(R.string.webclient_service_description))
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_ALERT_PRE,
            newId = NOTIFICATION_CHANNEL_ALERT,
        ) {
            setName(context.getString(R.string.notification_channel_alerts))
            setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setShowBadge(false)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.USAGE_NOTIFICATION_EVENT)
        }
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_NOTICE_PRE,
            newId = NOTIFICATION_CHANNEL_NOTICE,
        ) {
            setName(context.getString(R.string.notification_channel_notices))
            setImportance(NotificationManagerCompat.IMPORTANCE_LOW)
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }
        if (ConfigUtils.isWorkBuild()) {
            migrateOrCreateChannel(
                oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_WORK_SYNC_PRE,
                newId = NOTIFICATION_CHANNEL_WORK_SYNC,
            ) {
                setName(context.getString(R.string.work_data_sync))
                setImportance(NotificationManagerCompat.IMPORTANCE_LOW)
                setDescription(context.getString(R.string.work_data_sync_desc))
                setLightsEnabled(false)
                setVibrationEnabled(false)
                setShowBadge(false)
                setSound(null, null)
            }
        }
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS_PRE,
            newId = NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS,
        ) {
            setName(context.getString(R.string.backup_or_restore_progress))
            setImportance(NotificationManagerCompat.IMPORTANCE_LOW)
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS_PRE,
            newId = NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS,
        ) {
            setName(context.getString(R.string.notification_channel_new_contact))
            setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            setDescription(context.getString(R.string.notification_channel_new_contact_desc))
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setShowBadge(false)
            setSound(null, null)
        }
        migrateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_THREEMA_PUSH_PRE,
            newId = NOTIFICATION_CHANNEL_THREEMA_PUSH,
        )
        migrateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER_PRE,
            newId = NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER,
        )
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_FORWARD_SECURITY_PRE,
            newId = NOTIFICATION_CHANNEL_FORWARD_SECURITY,
        ) {
            setName(context.getString(R.string.forward_security_notification_channel_name))
            setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            setDescription(context.getString(R.string.notification_channel_new_contact_desc))
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setShowBadge(false)
            setSound(null, null)
        }
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_CALL_PRE,
            newId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS_V1,
        ) {
            setName(context.getString(R.string.incoming_calls))
            setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setVibrationPattern(NotificationChannels.VIBRATE_PATTERN_INCOMING_CALL)
            setShowBadge(false)
            setSound(Settings.System.DEFAULT_RINGTONE_URI, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        }
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_IN_CALL_PRE,
            newId = NOTIFICATION_CHANNEL_IN_CALL,
        ) {
            setName(context.getString(R.string.call_ongoing))
            setImportance(NotificationManagerCompat.IMPORTANCE_LOW)
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }
        migrateOrCreateChannel(
            oldId = ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_GROUP_CALL_PRE,
            newId = NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS,
        ) {
            setName(context.getString(R.string.group_calls))
            setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setVibrationPattern(NotificationChannels.VIBRATE_PATTERN_GROUP_CALL)
            setShowBadge(false)
            setSound(Settings.System.DEFAULT_RINGTONE_URI, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        }

        // delete obsolete channels
        safelyDeleteChannel(ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_IDENTITY_SYNC_PRE)
        safelyDeleteChannel(ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_GROUP_JOIN_RESPONSE_PRE)
        safelyDeleteChannel(ObsoleteNotificationChannels.NOTIFICATION_CHANNEL_GROUP_JOIN_REQUEST_PRE)

        // delete obsolete channel groups. this also deletes all remaining channels in these groups
        safelyDeleteChannelGroup(ObsoleteNotificationChannels.NOTIFICATION_CHANNELGROUP_CHAT_PRE)
        safelyDeleteChannelGroup(ObsoleteNotificationChannels.NOTIFICATION_CHANNELGROUP_CHAT_UPDATE_PRE)
        safelyDeleteChannelGroup(ObsoleteNotificationChannels.NOTIFICATION_CHANNELGROUP_VOIP_PRE)
        safelyDeleteChannelGroup(ObsoleteNotificationChannels.NOTIFICATION_CHANNELGROUP_GROUP_CALLS_PRE)

        // create default chat channels based on current settings
        val notificationPriority = sharedPreferences.getString(context.getString(R.string.preferences__notification_priority), "1")
            ?.toIntOrNull()
            ?: NotificationCompat.PRIORITY_HIGH
        val channelImportance = if (notificationPriority >= NotificationCompat.PRIORITY_HIGH) {
            NotificationManagerCompat.IMPORTANCE_MAX
        } else {
            NotificationManagerCompat.IMPORTANCE_HIGH
        }
        createChannel(
            channelId = NOTIFICATION_CHANNEL_CHATS_DEFAULT,
            channelName = context.getString(R.string.new_messages),
            channelImportance = channelImportance,
            groupId = NOTIFICATION_CHANNELGROUP_CHAT,
        ) {
            setLightsEnabled(true)
            setVibrationEnabled(sharedPreferences.getBoolean(context.getString(R.string.preferences__vibrate), true))
            setVibrationPattern(VIBRATE_PATTERN_REGULAR)
            setShowBadge(true)
            setSound(
                sound = sharedPreferences.getRingtoneUri(context.getString(R.string.preferences__notification_sound))
                    ?: Settings.System.DEFAULT_NOTIFICATION_URI,
                usage = AudioAttributes.USAGE_NOTIFICATION,
            )
        }
        createChannel(
            channelId = NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT,
            channelName = context.getString(R.string.new_group_messages),
            channelImportance = channelImportance,
            groupId = NOTIFICATION_CHANNELGROUP_GROUP_CHAT,
        ) {
            setLightsEnabled(true)
            setVibrationEnabled(sharedPreferences.getBoolean(context.getString(R.string.preferences__group_vibrate), true))
            setVibrationPattern(VIBRATE_PATTERN_REGULAR)
            setShowBadge(true)
            setSound(
                sound = sharedPreferences.getRingtoneUri(context.getString(R.string.preferences__group_notification_sound))
                    ?: Settings.System.DEFAULT_NOTIFICATION_URI,
                usage = AudioAttributes.USAGE_NOTIFICATION,
            )
        }
    }
}
