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

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.os.Environment
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import ch.threema.android.setSound
import ch.threema.app.R
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNELGROUP_CHAT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNELGROUP_GROUP_CHAT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_ALERT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_CHAT_UPDATE
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_FORWARD_SECURITY
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_IN_CALL
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_NOTICE
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_PASSPHRASE
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_THREEMA_PUSH
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_WEBCLIENT
import ch.threema.app.notifications.NotificationChannels.NOTIFICATION_CHANNEL_WORK_SYNC
import ch.threema.app.notifications.migrations.NotificationChannelMigration
import ch.threema.app.notifications.migrations.Version1Migration
import ch.threema.app.notifications.migrations.Version2Migration
import ch.threema.app.notifications.migrations.Version3Migration
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.RingtoneUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("NotificationChannelSetup")

class NotificationChannelSetup(
    private val appContext: Context,
    private val notificationManager: NotificationManagerCompat = NotificationManagerCompat.from(appContext),
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext),
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.default,
) {
    fun createOrUpdateNotificationChannels() {
        when (val version = getNotificationChannelsVersion()) {
            NO_VERSION -> {
                if (notificationManager.notificationChannelsCompat.isEmpty()) {
                    // app is a fresh installation, or notification channels were reset
                    createNotificationChannelsFromScratch()
                } else {
                    // app was updated from a version that already used notification channels but didn't yet version them,
                    // or the version number was lost because the app was reset
                    migrate(fromVersion = 0)
                }
                installThreemaRingtone()
                storeNotificationChannelsVersion(NOTIFICATION_CHANNELS_VERSION)
            }
            in 0 until NOTIFICATION_CHANNELS_VERSION -> {
                migrate(fromVersion = version)
                storeNotificationChannelsVersion(NOTIFICATION_CHANNELS_VERSION)
            }
            NOTIFICATION_CHANNELS_VERSION -> {
                // notification channels likely already exist, but might have been reset (via the Android system settings),
                // thus we recreate them if needed
                createNotificationChannelsFromScratch()
            }
            else -> logger.error("Unexpected notification channel version {}", version)
        }
    }

    private fun getNotificationChannelsVersion(): Int =
        sharedPreferences.getInt(appContext.getString(R.string.preferences__notification_channels_version), NO_VERSION)

    private fun storeNotificationChannelsVersion(version: Int) = sharedPreferences.edit {
        putInt(appContext.getString(R.string.preferences__notification_channels_version), version)
    }

    /**
     * Ensures that all required notification channels and notification channel groups exist by creating them if needed.
     * If you need to make changes here, make sure to also create an appropriate migration to account for existing app installations.
     */
    private fun createNotificationChannelsFromScratch() = with(notificationManager) {
        createGroup(NOTIFICATION_CHANNELGROUP_CHAT, getString(R.string.chats))
        createGroup(NOTIFICATION_CHANNELGROUP_GROUP_CHAT, getString(R.string.group_chats))

        createChannel(
            channelId = NOTIFICATION_CHANNEL_CHATS_DEFAULT,
            channelName = getString(R.string.new_messages),
            channelImportance = NotificationManagerCompat.IMPORTANCE_MAX,
            groupId = NOTIFICATION_CHANNELGROUP_CHAT,
        ) {
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setVibrationPattern(NotificationChannels.VIBRATE_PATTERN_REGULAR)
            setShowBadge(true)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.USAGE_NOTIFICATION)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT,
            channelName = getString(R.string.new_group_messages),
            channelImportance = NotificationManagerCompat.IMPORTANCE_MAX,
            groupId = NOTIFICATION_CHANNELGROUP_GROUP_CHAT,
        ) {
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setVibrationPattern(NotificationChannels.VIBRATE_PATTERN_REGULAR)
            setShowBadge(true)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.USAGE_NOTIFICATION)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_INCOMING_CALLS,
            channelName = getString(R.string.incoming_calls),
            channelImportance = NotificationManagerCompat.IMPORTANCE_HIGH,
        ) {
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setVibrationPattern(NotificationChannels.VIBRATE_PATTERN_INCOMING_CALL)
            setShowBadge(false)
            setSound(RingtoneUtil.THREEMA_CALL_RINGTONE_URI, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_IN_CALL,
            channelName = getString(R.string.call_ongoing),
            channelImportance = NotificationManagerCompat.IMPORTANCE_LOW,
        ) {
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS,
            channelName = getString(R.string.group_calls),
            channelImportance = NotificationManagerCompat.IMPORTANCE_HIGH,
        ) {
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setVibrationPattern(NotificationChannels.VIBRATE_PATTERN_GROUP_CALL)
            setShowBadge(false)
            setSound(Settings.System.DEFAULT_RINGTONE_URI, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_PASSPHRASE,
            channelName = getString(R.string.passphrase_service_name),
            channelImportance = NotificationManagerCompat.IMPORTANCE_LOW,
        ) {
            setDescription(getString(R.string.passphrase_service_description))
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_WEBCLIENT,
            channelName = getString(R.string.webclient),
            channelImportance = NotificationManagerCompat.IMPORTANCE_LOW,
        ) {
            setDescription(getString(R.string.webclient_service_description))
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_ALERT,
            channelName = getString(R.string.notification_channel_alerts),
            channelImportance = NotificationManagerCompat.IMPORTANCE_HIGH,
        ) {
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setShowBadge(false)
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), AudioAttributes.USAGE_NOTIFICATION_EVENT)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_NOTICE,
            channelName = getString(R.string.notification_channel_notices),
            channelImportance = NotificationManagerCompat.IMPORTANCE_LOW,
        ) {
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS,
            channelName = getString(R.string.backup_or_restore_progress),
            channelImportance = NotificationManagerCompat.IMPORTANCE_LOW,
        ) {
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS,
            channelName = getString(R.string.notification_channel_new_contact),
            channelImportance = NotificationManagerCompat.IMPORTANCE_HIGH,
        ) {
            setDescription(getString(R.string.notification_channel_new_contact_desc))
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setShowBadge(false)
            setSound(null, null)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_FORWARD_SECURITY,
            channelName = getString(R.string.forward_security_notification_channel_name),
            channelImportance = NotificationManagerCompat.IMPORTANCE_HIGH,
        ) {
            setDescription(getString(R.string.notification_channel_new_contact_desc))
            setLightsEnabled(true)
            setVibrationEnabled(true)
            setShowBadge(false)
            setSound(null, null)
        }

        createChannel(
            channelId = NOTIFICATION_CHANNEL_CHAT_UPDATE,
            channelName = getString(R.string.chat_updates),
            channelImportance = NotificationManagerCompat.IMPORTANCE_LOW,
        ) {
            setLightsEnabled(false)
            setVibrationEnabled(false)
            setShowBadge(false)
            setSound(null, null)
        }

        // work sync notification
        if (ConfigUtils.isWorkBuild()) {
            createChannel(
                channelId = NOTIFICATION_CHANNEL_WORK_SYNC,
                channelName = getString(R.string.work_data_sync),
                channelImportance = NotificationManagerCompat.IMPORTANCE_LOW,
            ) {
                setDescription(getString(R.string.work_data_sync_desc))
                setLightsEnabled(false)
                setVibrationEnabled(false)
                setShowBadge(false)
                setSound(null, null)
            }
        }
    }

    private fun migrate(fromVersion: Int) {
        logger.info("Upgrading notification channels from version {}", fromVersion)
        val migrationFactories = getMigrationFactories()
        for (toVersion in (fromVersion + 1)..NOTIFICATION_CHANNELS_VERSION) {
            logger.info("Upgrading notification channels to version {}", toVersion)
            migrationFactories[toVersion - 1]().migrate(appContext, sharedPreferences, notificationManager)
        }
    }

    private fun getMigrationFactories() = arrayOf<() -> NotificationChannelMigration>(
        ::Version1Migration,
        ::Version2Migration,
        ::Version3Migration,
    )

    private fun getString(@StringRes resId: Int): String =
        appContext.getString(resId)

    /**
     * Copies the default Threema ringtone into the public directories, to allow users to switch back to the default ringtone manually.
     */
    private fun installThreemaRingtone() {
        CoroutineScope(dispatcherProvider.io).launch {
            try {
                installThreemaRingtoneIntoDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES))

                // On some devices (in particular Samsung) the notification channel settings only allow picking notification sounds but not ringtones.
                // To account for that, we also install the ringtone into the Notifications directory.
                installThreemaRingtoneIntoDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_NOTIFICATIONS))
            } catch (e: Exception) {
                logger.error("Failed to copy Threema ringtone", e)
            }
        }
    }

    private fun installThreemaRingtoneIntoDirectory(targetDirectory: File) {
        val targetFile = File(targetDirectory, "Threema Call.ogg")
        if (targetFile.exists()) {
            return
        }
        try {
            targetDirectory.mkdirs()
            appContext.resources.openRawResource(R.raw.threema_call).use { inStream ->
                FileOutputStream(targetFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(targetFile.absolutePath),
                arrayOf("audio/ogg"),
                null,
            )
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        }
    }

    fun deleteAll() {
        with(notificationManager) {
            safelyDeleteChannel(NOTIFICATION_CHANNEL_INCOMING_CALLS)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_IN_CALL)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_ALERT)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_NOTICE)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_FORWARD_SECURITY)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_CHAT_UPDATE)

            // get rid of foreground service channels
            safelyDeleteChannel(NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS)
            if (ConfigUtils.isWorkBuild()) {
                safelyDeleteChannel(NOTIFICATION_CHANNEL_WORK_SYNC)
            }
            safelyDeleteChannel(NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_PASSPHRASE)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_WEBCLIENT)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_THREEMA_PUSH)

            // delete all groups and all the channels that belong to them, including custom per-conversation channels
            safelyDeleteChannelGroup(NOTIFICATION_CHANNELGROUP_CHAT)
            safelyDeleteChannelGroup(NOTIFICATION_CHANNELGROUP_GROUP_CHAT)

            safelyDeleteChannel(NOTIFICATION_CHANNEL_CHATS_DEFAULT)
            safelyDeleteChannel(NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT)
        }
        storeNotificationChannelsVersion(version = 0)
    }

    companion object {
        private const val NOTIFICATION_CHANNELS_VERSION = 3

        private const val NO_VERSION = 0
    }
}
