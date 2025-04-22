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

/**
 * Contains all the names of notification channels and notification channel groups which are no longer used by the app today,
 * except for the purpose of migrating old channels and groups.
 */
object ObsoleteNotificationChannels {
    const val NOTIFICATION_CHANNEL_CHATS_DEFAULT_V1 = "chats_default"
    const val NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT_V1 = "group_chats_default"
    const val NOTIFICATION_CHANNEL_INCOMING_CALLS_V1 = "incoming_call"
    const val NOTIFICATION_CHANNEL_PASSPHRASE_PRE = "ps"
    const val NOTIFICATION_CHANNEL_WEBCLIENT_PRE = "wc"

    /** channel formerly used for calls */
    const val NOTIFICATION_CHANNEL_CALL_PRE = "ca"
    const val NOTIFICATION_CHANNEL_IN_CALL_PRE = "ic"
    const val NOTIFICATION_CHANNEL_ALERT_PRE = "al"
    const val NOTIFICATION_CHANNEL_NOTICE_PRE = "no"
    const val NOTIFICATION_CHANNEL_WORK_SYNC_PRE = "ws"
    const val NOTIFICATION_CHANNEL_IDENTITY_SYNC_PRE = "is"
    const val NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS_PRE = "bk"

    /** channel formerly used for quiet chat notification updates (e.g. when changing from regular to PIN locked) */
    const val NOTIFICATION_CHANNEL_CHAT_UPDATE_PRE = "cu"
    const val NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS_PRE = "nc"
    const val NOTIFICATION_CHANNEL_GROUP_JOIN_RESPONSE_PRE = "jres"
    const val NOTIFICATION_CHANNEL_GROUP_JOIN_REQUEST_PRE = "jreq"
    const val NOTIFICATION_CHANNEL_THREEMA_PUSH_PRE = "tpush"

    /** channel formerly used for group calls */
    const val NOTIFICATION_CHANNEL_GROUP_CALL_PRE = "gcall"
    const val NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER_PRE = "vmp"
    const val NOTIFICATION_CHANNEL_FORWARD_SECURITY_PRE = "fs"

    const val NOTIFICATION_CHANNELGROUP_CHAT_PRE = "group"
    const val NOTIFICATION_CHANNELGROUP_VOIP_PRE = "vgroup"

    /** formerly used for silent updates */
    const val NOTIFICATION_CHANNELGROUP_CHAT_UPDATE_PRE = "ugroup"
    const val NOTIFICATION_CHANNELGROUP_GROUP_CALLS_PRE = "group_calls"
}
