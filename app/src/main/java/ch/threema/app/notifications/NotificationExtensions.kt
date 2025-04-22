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

package ch.threema.app.notifications

import android.content.SharedPreferences
import android.net.Uri
import android.os.DeadObjectException
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import ch.threema.app.services.ServicesConstants
import ch.threema.app.utils.SoundUtil
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("NotificationExtensions")

/**
 * Retrieves the URI for a ringtone (aka. notification sound).
 * [Uri.EMPTY] is used to represent "silent".
 */
fun SharedPreferences.getRingtoneUri(key: String): Uri? =
    getString(key, null)
        .takeUnless { it.isNullOrBlank() }
        ?.takeUnless { it == ServicesConstants.PREFERENCES_NULL }
        ?.toUri()

fun NotificationManagerCompat.exists(channelId: String): Boolean =
    getNotificationChannelCompat(channelId) != null

/**
 * Create a notification channel group with the provided ID and name
 * If a group with [groupId] already exists and the name has changed, rename it to the provided group name
 */
fun NotificationManagerCompat.createGroup(groupId: String, name: String) {
    val existingGroup = findNotificationChannelGroup(groupId)
    if (existingGroup != null && name == existingGroup.name) {
        return
    }
    val notificationChannelGroup = NotificationChannelGroupCompat.Builder(groupId)
        .setName(name)
        .build()
    createNotificationChannelGroup(notificationChannelGroup)
}

private fun NotificationManagerCompat.findNotificationChannelGroup(groupId: String): NotificationChannelGroupCompat? =
    try {
        getNotificationChannelGroupCompat(groupId)
    } catch (e: DeadObjectException) {
        notificationChannelGroupsCompat.firstOrNull { group -> group.id == groupId }
    }

fun NotificationManagerCompat.createChannel(
    channelId: String,
    channelName: String? = channelId,
    channelImportance: Int,
    parentChannelId: String? = null,
    groupId: String? = null,
    builder: NotificationChannelCompat.Builder.() -> Unit,
) {
    val notificationChannel = NotificationChannelCompat.Builder(channelId, channelImportance).apply {
        setName(channelName)
        if (parentChannelId != null) {
            setConversationId(parentChannelId, channelId)
        }
        if (groupId != null) {
            setGroup(groupId)
        }
        builder()
    }
        .build()
    createNotificationChannel(notificationChannel)
}

fun NotificationManagerCompat.safelyDeleteChannel(channelId: String) {
    try {
        deleteNotificationChannel(channelId)
    } catch (e: SecurityException) {
        logger.error("Unable to delete notification channel {}", channelId, e)
    }
}

fun NotificationChannelCompat.toBuilder(channelId: String): NotificationChannelCompat.Builder =
    NotificationChannelCompat.Builder(channelId, importance).apply {
        setName(name)
        setDescription(description)
        setGroup(group)
        setShowBadge(canShowBadge())
        setSound(sound, audioAttributes)
        setLightsEnabled(shouldShowLights())
        setLightColor(lightColor)
        setVibrationEnabled(shouldVibrate())
        setVibrationPattern(vibrationPattern)
        if (parentChannelId != null && conversationId != null) {
            setConversationId(parentChannelId!!, conversationId!!)
        }
    }

/**
 * Migrate an existing channel by copying all of its properties to a new channel with a new ID, applying [modify] to it,
 * and deleting the old channel.
 * If the channel does not exist, nothing happens.
 */
fun NotificationManagerCompat.migrateChannel(
    oldId: String,
    newId: String,
    modify: (NotificationChannelCompat.Builder.() -> Unit) = {},
) {
    migrateOrCreateChannel(
        oldId = oldId,
        newId = newId,
        modify = modify,
        create = null,
    )
}

/**
 * Migrate an existing channel by copying all of its properties to a new channel with a new ID, applying [modify] to it,
 * and deleting the old channel.
 * If the channel does not exist, it is created using the provided [create].
 */
fun NotificationManagerCompat.migrateOrCreateChannel(
    oldId: String,
    newId: String,
    modify: (NotificationChannelCompat.Builder.() -> Unit) = {},
    create: (NotificationChannelCompat.Builder.() -> Unit)?,
) {
    val oldNotificationChannel = getNotificationChannelCompat(oldId)
    if (oldNotificationChannel != null) {
        val newNotificationChannel = oldNotificationChannel.toBuilder(newId)
            .apply(modify)
            .build()

        createNotificationChannel(newNotificationChannel)
        safelyDeleteChannel(oldId)
    } else if (create != null) {
        val notificationChannel = NotificationChannelCompat.Builder(newId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .apply(create)
            .build()
        createNotificationChannel(notificationChannel)
    }
}

/**
 * Attempt to delete a channel group. Continue if deleting the channel group fails.
 */
fun NotificationManagerCompat.safelyDeleteChannelGroup(groupId: String) {
    try {
        deleteNotificationChannelGroup(groupId)
    } catch (e: SecurityException) {
        logger.error("Unable to delete notification channel group {}", groupId, e)
    }
}

fun NotificationChannelCompat.Builder.setSound(sound: Uri?, usage: Int) {
    setSound(sound, SoundUtil.getAudioAttributesForUsage(usage))
}
