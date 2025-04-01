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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.DeadObjectException
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.ServicesConstants
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.SoundUtil
import ch.threema.base.utils.LoggingUtil
import org.slf4j.Logger

object NotificationChannels {

    val logger: Logger = LoggingUtil.getThreemaLogger("NotificationChannels")

    /**
     * Notification channel version
     */

    private const val CHANNEL_SETUP_VERSION = 1

    /*
     * static notification channels
     */
    const val NOTIFICATION_CHANNEL_CHATS_DEFAULT: String =
        "chats_default" // channel for default chat notifications
    const val NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT: String =
        "group_chats_default" // channel for default chat notifications
    const val NOTIFICATION_CHANNEL_INCOMING_CALLS: String = "incoming_call"
    const val NOTIFICATION_CHANNEL_IN_CALL: String = "ongoing_call"
    const val NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS: String =
        "incoming_group_call" // channel for group calls
    const val NOTIFICATION_CHANNEL_PASSPHRASE: String = "passphrase"
    const val NOTIFICATION_CHANNEL_WEBCLIENT: String = "webclient"
    const val NOTIFICATION_CHANNEL_ALERT: String = "alert"
    const val NOTIFICATION_CHANNEL_NOTICE: String = "notice"
    const val NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS: String = "data_backup"
    const val NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS: String = "new_contact_found"
    const val NOTIFICATION_CHANNEL_FORWARD_SECURITY: String = "forward_security_alert"
    const val NOTIFICATION_CHANNEL_CHAT_UPDATE: String =
        "silent_chat_update" // channel for quiet chat notification updates (e.g. when changing from regular to PIN locked)
    const val NOTIFICATION_CHANNEL_WORK_SYNC: String = "work_sync"

    // these channels are created on demand only
    const val NOTIFICATION_CHANNEL_THREEMA_PUSH: String =
        "threema_push" // channel will be created on-the-fly by ThreemaPushService
    const val NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER: String =
        "voicemessage_player" // channel will be created on-the-fly by VoiceMessagePlayerService

    /*
     * notification channel groups
     */
    private const val NOTIFICATION_CHANNELGROUP_CHAT: String = "chats"
    private const val NOTIFICATION_CHANNELGROUP_GROUP_CHAT: String = "group_chats"

    /**
     * deprecated notification channels (pre versioning)
     */
    private const val NOTIFICATION_CHANNEL_PASSPHRASE_PRE: String = "ps"
    private const val NOTIFICATION_CHANNEL_WEBCLIENT_PRE: String = "wc"
    private const val NOTIFICATION_CHANNEL_CHATS_DEFAULT_PRE: String =
        "cc" // channel for default chat notifications
    private const val NOTIFICATION_CHANNEL_CALL_PRE: String = "ca" // channel used for calls
    private const val NOTIFICATION_CHANNEL_IN_CALL_PRE: String = "ic"
    private const val NOTIFICATION_CHANNEL_ALERT_PRE: String = "al"
    private const val NOTIFICATION_CHANNEL_NOTICE_PRE: String = "no"
    private const val NOTIFICATION_CHANNEL_WORK_SYNC_PRE: String = "ws"
    private const val NOTIFICATION_CHANNEL_IDENTITY_SYNC_PRE: String = "is"
    private const val NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS_PRE: String = "bk"
    private const val NOTIFICATION_CHANNEL_CHAT_UPDATE_PRE: String =
        "cu" // channel for quiet chat notification updates (e.g. when changing from regular to PIN locked)
    private const val NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS_PRE: String = "nc"
    private const val NOTIFICATION_CHANNEL_GROUP_JOIN_RESPONSE_PRE: String =
        "jres" // currently not used
    private const val NOTIFICATION_CHANNEL_GROUP_JOIN_REQUEST_PRE: String =
        "jreq" // currently not use
    private const val NOTIFICATION_CHANNEL_THREEMA_PUSH_PRE: String = "tpush"
    private const val NOTIFICATION_CHANNEL_GROUP_CALL_PRE: String =
        "gcall" // channel for group calls
    private const val NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER_PRE: String = "vmp"
    private const val NOTIFICATION_CHANNEL_FORWARD_SECURITY_PRE: String = "fs"

    /*
     * deprecated notification channel groups (pre versioning)
     */
    private const val NOTIFICATION_CHANNELGROUP_CHAT_PRE: String = "group"
    private const val NOTIFICATION_CHANNELGROUP_VOIP_PRE: String = "vgroup"
    private const val NOTIFICATION_CHANNELGROUP_CHAT_UPDATE_PRE: String = "ugroup" // silent updates
    private const val NOTIFICATION_CHANNELGROUP_GROUP_CALLS_PRE: String = "group_calls"

    /**
     * Default vibration patterns
     */
    @JvmField
    val VIBRATE_PATTERN_REGULAR: LongArray = longArrayOf(0, 250, 250, 250)
    @JvmField
    val VIBRATE_PATTERN_INCOMING_CALL: LongArray = longArrayOf(0, 1000, 1000, 0)
    @JvmField
    val VIBRATE_PATTERN_GROUP_CALL: LongArray = longArrayOf(0, 2000)

    /**
     * Ensure notification channels and groups are created. Upgrade if necessary
     */
    fun ensureNotificationChannelsAndGroups() {
        val appContext = ThreemaApplication.getAppContext()
        val notificationManagerCompat = NotificationManagerCompat.from(appContext)

        // PreferenceService may not yet be available at that time
        val sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext())

        val previousVersion = sharedPreferences.getInt(
            appContext.getString(R.string.preferences__notification_channels_version),
            0
        )
        if (previousVersion < CHANNEL_SETUP_VERSION) {
            if (previousVersion == 0) {
                logger.info(
                    "Upgrading notification channels and groups from version {} to {}",
                    previousVersion,
                    CHANNEL_SETUP_VERSION
                )
                upgradeGroupsAndChannelsToVersion1(appContext, notificationManagerCompat)
            }
            sharedPreferences.edit {
                putInt(
                    appContext.getString(R.string.preferences__notification_channels_version),
                    CHANNEL_SETUP_VERSION
                )
            }
        }

        createOrRefreshChannelsAndGroups(appContext, notificationManagerCompat)
    }

    /**
     * create all channels for a new installation
     */
    private fun createOrRefreshChannelsAndGroups(
        context: Context,
        notificationManagerCompat: NotificationManagerCompat
    ) {
        createGroup(
            context,
            notificationManagerCompat,
            NOTIFICATION_CHANNELGROUP_CHAT,
            R.string.chats
        )
        createGroup(
            context,
            notificationManagerCompat,
            NOTIFICATION_CHANNELGROUP_GROUP_CHAT,
            R.string.group_chats
        )

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_CHATS_DEFAULT,
            context.getString(R.string.new_messages),
            NotificationManagerCompat.IMPORTANCE_MAX,
            null,
            NOTIFICATION_CHANNELGROUP_CHAT
        )?.let {
            it.setLightsEnabled(true)
            it.setVibrationEnabled(true)
            it.setVibrationPattern(VIBRATE_PATTERN_REGULAR)
            it.setShowBadge(true)
            it.setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                SoundUtil.getAudioAttributesForUsage(AudioAttributes.USAGE_NOTIFICATION)
            )
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT,
            context.getString(R.string.new_group_messages),
            NotificationManagerCompat.IMPORTANCE_MAX,
            null,
            NOTIFICATION_CHANNELGROUP_GROUP_CHAT
        )?.let {
            it.setLightsEnabled(true)
            it.setVibrationEnabled(true)
            it.setVibrationPattern(VIBRATE_PATTERN_REGULAR)
            it.setShowBadge(true)
            it.setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                SoundUtil.getAudioAttributesForUsage(AudioAttributes.USAGE_NOTIFICATION)
            )
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_INCOMING_CALLS,
            context.getString(R.string.incoming_calls),
            NotificationManagerCompat.IMPORTANCE_HIGH
        )?.let {
            it.setLightsEnabled(true)
            it.setVibrationEnabled(true)
            it.setVibrationPattern(VIBRATE_PATTERN_INCOMING_CALL)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_IN_CALL,
            context.getString(R.string.call_ongoing),
            NotificationManagerCompat.IMPORTANCE_LOW
        )?.let {
            it.setLightsEnabled(false)
            it.setVibrationEnabled(false)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS,
            context.getString(R.string.group_calls),
            NotificationManagerCompat.IMPORTANCE_HIGH
        )?.let {
            it.setLightsEnabled(true)
            it.setVibrationEnabled(true)
            it.setVibrationPattern(VIBRATE_PATTERN_GROUP_CALL)
            it.setShowBadge(false)
            it.setSound(
                Settings.System.DEFAULT_RINGTONE_URI,
                SoundUtil.getAudioAttributesForUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            )
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_PASSPHRASE,
            context.getString(R.string.passphrase_service_name),
            NotificationManagerCompat.IMPORTANCE_LOW
        )?.let {
            it.setDescription(context.getString(R.string.passphrase_service_description))
            it.setLightsEnabled(false)
            it.setVibrationEnabled(false)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_WEBCLIENT,
            context.getString(R.string.webclient),
            NotificationManagerCompat.IMPORTANCE_LOW
        )?.let {
            it.setDescription(context.getString(R.string.webclient_service_description))
            it.setLightsEnabled(false)
            it.setVibrationEnabled(false)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_ALERT,
            context.getString(R.string.notification_channel_alerts),
            NotificationManagerCompat.IMPORTANCE_HIGH
        )?.let {
            it.setLightsEnabled(true)
            it.setVibrationEnabled(true)
            it.setShowBadge(false)
            it.setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                SoundUtil.getAudioAttributesForUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            )
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_NOTICE,
            context.getString(R.string.notification_channel_notices),
            NotificationManagerCompat.IMPORTANCE_LOW
        )?.let {
            it.setLightsEnabled(false)
            it.setVibrationEnabled(false)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS,
            context.getString(R.string.backup_or_restore_progress),
            NotificationManagerCompat.IMPORTANCE_LOW
        )?.let {
            it.setLightsEnabled(false)
            it.setVibrationEnabled(false)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS,
            context.getString(R.string.notification_channel_new_contact),
            NotificationManagerCompat.IMPORTANCE_HIGH
        )?.let {
            it.setDescription(context.getString(R.string.notification_channel_new_contact_desc))
            it.setLightsEnabled(true)
            it.setVibrationEnabled(true)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_FORWARD_SECURITY,
            context.getString(R.string.forward_security_notification_channel_name),
            NotificationManagerCompat.IMPORTANCE_HIGH
        )?.let {
            it.setDescription(context.getString(R.string.notification_channel_new_contact_desc))
            it.setLightsEnabled(true)
            it.setVibrationEnabled(true)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_CHAT_UPDATE,
            context.getString(R.string.chat_updates),
            NotificationManagerCompat.IMPORTANCE_LOW
        )?.let {
            it.setLightsEnabled(false)
            it.setVibrationEnabled(false)
            it.setShowBadge(false)
            it.setSound(null, null)
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        // work sync notification
        if (ConfigUtils.isWorkBuild()) {
            getChannelBuilder(
                notificationManagerCompat,
                NOTIFICATION_CHANNEL_WORK_SYNC,
                context.getString(R.string.work_data_sync),
                NotificationManagerCompat.IMPORTANCE_LOW
            )?.let {
                it.setDescription(context.getString(R.string.work_data_sync_desc))
                it.setLightsEnabled(false)
                it.setVibrationEnabled(false)
                it.setShowBadge(false)
                it.setSound(null, null)
                notificationManagerCompat.createNotificationChannel(it.build())
            }
        }
    }

    private fun upgradeGroupsAndChannelsToVersion1(
        context: Context,
        notificationManagerCompat: NotificationManagerCompat
    ) {
        // destination groups must be created *before* we can migrate
        createGroup(
            context,
            notificationManagerCompat,
            NOTIFICATION_CHANNELGROUP_CHAT,
            R.string.chats
        )
        createGroup(
            context,
            notificationManagerCompat,
            NOTIFICATION_CHANNELGROUP_GROUP_CHAT,
            R.string.group_chats
        )

        // migrate all channels that are part of a group
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_CHAT_UPDATE_PRE,
            NOTIFICATION_CHANNEL_CHAT_UPDATE,
            NOTIFICATION_CHANNELGROUP_CHAT
        )

        // migrate all channels that are not part of a group (they will just get a new ID)
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_PASSPHRASE_PRE,
            NOTIFICATION_CHANNEL_PASSPHRASE
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_WEBCLIENT_PRE,
            NOTIFICATION_CHANNEL_WEBCLIENT
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_ALERT_PRE,
            NOTIFICATION_CHANNEL_ALERT
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_NOTICE_PRE,
            NOTIFICATION_CHANNEL_NOTICE
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_WORK_SYNC_PRE,
            NOTIFICATION_CHANNEL_WORK_SYNC
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS_PRE,
            NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS_PRE,
            NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_THREEMA_PUSH_PRE,
            NOTIFICATION_CHANNEL_THREEMA_PUSH
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER_PRE,
            NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_FORWARD_SECURITY_PRE,
            NOTIFICATION_CHANNEL_FORWARD_SECURITY
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_CALL_PRE,
            NOTIFICATION_CHANNEL_INCOMING_CALLS
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_IN_CALL_PRE,
            NOTIFICATION_CHANNEL_IN_CALL
        )
        migrateChannel(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_GROUP_CALL_PRE,
            NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS
        )

        // delete obsolete channels
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_IDENTITY_SYNC_PRE)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_GROUP_JOIN_RESPONSE_PRE)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_GROUP_JOIN_REQUEST_PRE)

        // delete obsolete channel groups. this also deletes all remaining channels in these groups
        deleteChannelGroup(notificationManagerCompat, NOTIFICATION_CHANNELGROUP_CHAT_PRE)
        deleteChannelGroup(notificationManagerCompat, NOTIFICATION_CHANNELGROUP_CHAT_UPDATE_PRE)
        deleteChannelGroup(notificationManagerCompat, NOTIFICATION_CHANNELGROUP_VOIP_PRE)
        deleteChannelGroup(notificationManagerCompat, NOTIFICATION_CHANNELGROUP_GROUP_CALLS_PRE)

        // create default chat channels based on current settings
        val sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(ThreemaApplication.getAppContext())
        val channelImportance =
            if (NotificationUtil.getNotificationPriority(context) >= NotificationCompat.PRIORITY_HIGH) NotificationManagerCompat.IMPORTANCE_MAX else NotificationManagerCompat.IMPORTANCE_HIGH

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_CHATS_DEFAULT,
            context.getString(R.string.new_messages),
            channelImportance,
            null,
            NOTIFICATION_CHANNELGROUP_CHAT
        )?.let {
            it.setVibrationEnabled(
                sharedPreferences.getBoolean(
                    context.getString(R.string.preferences__vibrate),
                    true
                )
            )
            it.setVibrationPattern(VIBRATE_PATTERN_REGULAR)
            it.setShowBadge(true)
            it.setSound(
                getRingtoneUriFromPrefsKey(
                    context,
                    sharedPreferences,
                    R.string.preferences__notification_sound
                ),
                SoundUtil.getAudioAttributesForUsage(AudioAttributes.USAGE_NOTIFICATION)
            )
            notificationManagerCompat.createNotificationChannel(it.build())
        }

        getChannelBuilder(
            notificationManagerCompat,
            NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT,
            context.getString(R.string.new_group_messages),
            channelImportance,
            null,
            NOTIFICATION_CHANNELGROUP_GROUP_CHAT
        )?.let {
            it.setVibrationEnabled(
                sharedPreferences.getBoolean(
                    context.getString(R.string.preferences__group_vibrate),
                    true
                )
            )
            it.setVibrationPattern(VIBRATE_PATTERN_REGULAR)
            it.setShowBadge(true)
            it.setSound(
                getRingtoneUriFromPrefsKey(
                    context,
                    sharedPreferences,
                    R.string.preferences__group_notification_sound
                ),
                SoundUtil.getAudioAttributesForUsage(AudioAttributes.USAGE_NOTIFICATION)
            )
            notificationManagerCompat.createNotificationChannel(it.build())
        }
    }

    private fun getRingtoneUriFromPrefsKey(
        context: Context,
        sharedPreferences: SharedPreferences,
        key: Int
    ): Uri {
        val ringtone = sharedPreferences.getString(context.getString(key), null)
        return if (!ringtone.isNullOrEmpty() && ringtone != ServicesConstants.PREFERENCES_NULL) Uri.parse(
            ringtone
        ) else Settings.System.DEFAULT_NOTIFICATION_URI
    }

    /**
     * Migrate existing channel to a new channel while dropping the group if a groupId is not specified
     * It is safe to call this method if a channel with oldId does not exist - no new channel will be created.
     * The old channel will be deleted after migration
     */
    private fun migrateChannel(
        notificationManagerCompat: NotificationManagerCompat,
        oldId: String,
        newId: String,
        groupId: String? = null
    ) {
        val oldNotificationChannel = notificationManagerCompat.getNotificationChannelCompat(oldId)

        if (oldNotificationChannel != null) {
            val newNotificationChannelBuilder =
                NotificationChannelCompat.Builder(newId, oldNotificationChannel.importance)
                    .setName(oldNotificationChannel.name)
                    .setDescription(oldNotificationChannel.description)
                    .setLightColor(oldNotificationChannel.lightColor)
                    .setLightsEnabled(oldNotificationChannel.shouldShowLights())
                    .setShowBadge(oldNotificationChannel.canShowBadge())
                    .setVibrationEnabled(oldNotificationChannel.shouldVibrate())
                    .setVibrationPattern(oldNotificationChannel.vibrationPattern)
                    .setSound(oldNotificationChannel.sound, oldNotificationChannel.audioAttributes)

            if (groupId != null) {
                newNotificationChannelBuilder.setGroup(groupId)
            }

            notificationManagerCompat.createNotificationChannel(newNotificationChannelBuilder.build())
            deleteChannel(notificationManagerCompat, oldId)
        }
    }

    /**
     * Attempt to delete a channel. Continue if deleting the channel fails.
     */
    fun deleteChannel(notificationManagerCompat: NotificationManagerCompat, channelId: String) {
        try {
            notificationManagerCompat.deleteNotificationChannel(channelId)
        } catch (e: SecurityException) {
            logger.error("Unable to delete notification channel {}", channelId, e)
        }
    }

    /**
     * Attempt to delete a channel group. Continue if deleting the channel group fails.
     */
    private fun deleteChannelGroup(
        notificationManagerCompat: NotificationManagerCompat,
        groupId: String
    ) {
        try {
            notificationManagerCompat.deleteNotificationChannelGroup(groupId)
        } catch (e: SecurityException) {
            logger.error("Unable to delete notification channel group {}", groupId, e)
        }
    }

    /**
     * Create a notification channel group with the provided ID and name
     * If a group already exists and the name has changed, rename it to the provided group name
     */
    private fun createGroup(
        context: Context,
        notificationManagerCompat: NotificationManagerCompat,
        groupId: String,
        groupNameRes: Int
    ) {
        val newName = context.getString(groupNameRes)
        val existingGroup = getNotificationChannelGroup(notificationManagerCompat, groupId)

        if (existingGroup != null && newName == existingGroup.name) {
            return
        }

        val builder = NotificationChannelGroupCompat.Builder(
            groupId
        ).setName(newName)

        notificationManagerCompat.createNotificationChannelGroup(builder.build())
    }

    /**
     * Get builder for a notification channel if a channel with the same ID doesn't already exist
     * If the channel exists and has a different name, the channel will be renamed
     * If a conversationId is specified, this is assumed to be a conversation-centric channel that will be linked to the specified channel in absence of a specific conversation id.
     * Otherwise, null is returned
     */
    private fun getChannelBuilder(
        notificationManagerCompat: NotificationManagerCompat,
        channelId: String,
        channelName: String?,
        channelImportance: Int,
        parentChannelId: String? = null,
        groupId: String? = null
    ): NotificationChannelCompat.Builder? {
        val newName = channelName ?: channelId
        val existingChannel = notificationManagerCompat.getNotificationChannelCompat(channelId)

        if (existingChannel != null && newName == existingChannel.name) {
            return null
        }

        val builder = NotificationChannelCompat.Builder(
            channelId,
            channelImportance
        ).setName(newName)

        if (parentChannelId != null) {
            builder.setConversationId(parentChannelId, channelId)
        }

        if (groupId != null) {
            builder.setGroup(groupId)
        }

        return builder
    }

    /**
     * Check if a notification group with the given groupId exists
     * @param notificationManagerCompat The notification manager
     * @param groupId The id of the group
     * @return NotificationChannelGroup object of matching group if exists, null otherwise
     */
    private fun getNotificationChannelGroup(
        notificationManagerCompat: NotificationManagerCompat,
        groupId: String
    ): NotificationChannelGroupCompat? {
        try {
            notificationManagerCompat.getNotificationChannelGroupCompat(groupId)
        } catch (e: DeadObjectException) {
            val groups = notificationManagerCompat.notificationChannelGroupsCompat
            for (group in groups) {
                if (groupId == group.id) {
                    return group
                }
            }
        }
        return null
    }

    public fun exists(context: Context, channelId: String): Boolean {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        return notificationManagerCompat.getNotificationChannelCompat(channelId) != null;
    }

    /**
     * launch notification channel settings screen for the channel specified by the channelId
     */
    fun launchChannelSettings(
        activityContext: Activity,
        channelName: String?,
        channelId: String,
        isGroupChat: Boolean,
        launcher: ActivityResultLauncher<Intent>? = null
    ) {
        if (!ConfigUtils.supportsNotificationChannels()) {
            return
        }

        val notificationManagerCompat = NotificationManagerCompat.from(activityContext)

        // check if a channel for specified ID exists - if not, create it before opening the intent
        if (!exists(activityContext, channelId)) {
            getChannelBuilder(
                notificationManagerCompat,
                channelId,
                channelName,
                NotificationManagerCompat.IMPORTANCE_MAX,
                if (isGroupChat) NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT else NOTIFICATION_CHANNEL_CHATS_DEFAULT,
                if (isGroupChat) NOTIFICATION_CHANNELGROUP_GROUP_CHAT else NOTIFICATION_CHANNELGROUP_CHAT
            )?.let {
                it.setLightsEnabled(true)
                it.setVibrationEnabled(true)
                it.setVibrationPattern(VIBRATE_PATTERN_REGULAR)
                it.setShowBadge(true)
                it.setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    SoundUtil.getAudioAttributesForUsage(AudioAttributes.USAGE_NOTIFICATION)
                )
                notificationManagerCompat.createNotificationChannel(it.build())
            }
        }

        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, ThreemaApplication.getAppContext().packageName)
        if (Build.VERSION.SDK_INT >= 30) {
            intent.putExtra(Settings.EXTRA_CONVERSATION_ID, channelId)
        }
        try {
            if (launcher != null) {
                launcher.launch(intent)
            } else {
                activityContext.startActivity(intent)
            }
        } catch (e: ActivityNotFoundException) {
            logger.debug("No settings activity found")
        }
    }

    fun deleteAll() {
        val notificationManagerCompat =
            NotificationManagerCompat.from(ThreemaApplication.getAppContext())

        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_INCOMING_CALLS)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_IN_CALL)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_ALERT)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_NOTICE)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_FORWARD_SECURITY)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_CHAT_UPDATE)

        // get rid of foreground service channels
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_BACKUP_RESTORE_IN_PROGRESS)
        if (ConfigUtils.isWorkBuild()) {
            deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_WORK_SYNC)
        }
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_VOICE_MSG_PLAYER)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_PASSPHRASE)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_WEBCLIENT)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_THREEMA_PUSH)

        deleteChannelGroup(
            notificationManagerCompat,
            NOTIFICATION_CHANNELGROUP_CHAT
        ) // this deletes all channels contained in this group
        deleteChannelGroup(
            notificationManagerCompat,
            NOTIFICATION_CHANNELGROUP_GROUP_CHAT
        ) // this deletes all channels contained in this group

        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_CHATS_DEFAULT)
        deleteChannel(notificationManagerCompat, NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT)
    }
}
