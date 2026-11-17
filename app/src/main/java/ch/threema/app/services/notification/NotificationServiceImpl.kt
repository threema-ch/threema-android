package ch.threema.app.services.notification

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_ONE_SHOT
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.drawable.IconCompat
import ch.threema.android.DeviceInfo
import ch.threema.android.buildNotification
import ch.threema.android.buildNotificationAction
import ch.threema.android.buildPerson
import ch.threema.android.cancelPendingIntentForActivity
import ch.threema.android.getPendingIntentForActivity
import ch.threema.android.getPendingIntentForBroadcast
import ch.threema.android.getPendingIntentForService
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.activities.BackupAdminActivity
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.home.HomeActivity
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.notifications.ForwardSecurityNotificationManager
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.notifications.NotificationGroups
import ch.threema.app.notifications.NotificationIDs
import ch.threema.app.notifications.NotificationIDs.getNotificationIdForConversation
import ch.threema.app.notifications.NotificationRequestCodes
import ch.threema.app.notifications.NotificationRequestCodes.ConversationNotificationAction
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.receivers.CancelResendMessagesBroadcastReceiver
import ch.threema.app.receivers.ReSendMessagesBroadcastReceiver
import ch.threema.app.servermessage.ServerMessageActivity
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.notification.ConversationNotificationStore.StoreAccessor
import ch.threema.app.ui.muteAppliesAt
import ch.threema.app.usecases.conversation.GetConversationCategoryUseCase
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DoNotDisturbUtil
import ch.threema.app.utils.NameUtil
import ch.threema.app.voip.activities.CallActivity
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.app.voip.services.VoipCallService
import ch.threema.app.widget.WidgetUpdater
import ch.threema.base.SessionScoped
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import ch.threema.common.truncate
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.ConversationIdObfuscated
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.data.datatypes.LocalGroupId
import ch.threema.data.datatypes.localGroupId
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.Identity
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.conversationId
import ch.threema.storage.models.group.GroupModelOld
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("NotificationServiceImpl")

@SessionScoped
class NotificationServiceImpl(
    appContext: Context,
    notificationManagerCompat: NotificationManagerCompat,
    private val getConversationCategoryUseCase: GetConversationCategoryUseCase,
    private val lockAppService: LockAppService,
    private val notificationPreferenceService: NotificationPreferenceService,
    private val ringtoneService: RingtoneService,
    private val preferenceService: PreferenceService,
    private val identityProvider: IdentityProvider,
    private val badgeUpdater: BadgeUpdater,
    private val doNotDisturbUtil: DoNotDisturbUtil,
    private val timeProvider: TimeProvider,
    private val widgetUpdater: WidgetUpdater,
) : BaseNotificationService(appContext, notificationManagerCompat), NotificationService, KoinComponent {

    private val conversationNotificationStore = ConversationNotificationStore()
    private var visibleConversationId: ConversationId? = null

    private val fsNotificationManager by lazy {
        ForwardSecurityNotificationManager(
            appContext,
            preferenceService,
        )
    }

    private val contactService: ContactService? by injectNullableNonBinding()
    private val contactModelRepository: ContactModelRepository? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()

    init {
        NotificationChannels.createOrMigrateNotificationChannels(appContext)
    }

    override fun recreateNotificationChannels() {
        NotificationChannels.recreateNotificationChannels(appContext)
    }

    override fun setVisibleConversation(conversationId: ConversationId?) {
        if (conversationId != null && conversationId != visibleConversationId) {
            cancelAllConversationNotifications(conversationId)
        }
        visibleConversationId = conversationId
    }

    override fun showGroupCallNotification(group: GroupModelOld, contactModelData: ContactModelData) {
        val groupService = groupService
        if (groupService == null) {
            logger.error("Group service is null; cannot show notification")
            return
        }

        if (shouldBlockGroupCallNotification(group)) {
            return
        }

        val conversationId = group.conversationId

        val publicNotification = buildNotification(appContext, channelId = NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS) {
            setContentTitle(appContext.getString(R.string.group_call))
            setContentText(appContext.getString(R.string.voip_gc_notification_new_call_public))
            setSmallIcon(R.drawable.ic_phone_locked_outline)
            setGroup(NotificationGroups.CALLS)
            setGroupSummary(false)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setChannelId(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS)
        }

        val privateNotification = buildNotification(appContext, channelId = NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS) {
            val contentText = appContext.getString(
                R.string.voip_gc_notification_call_started,
                contactModelData.getShortName(),
                group.name,
            )
            setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            setContentTitle(appContext.getString(R.string.group_call))
            setContentText(contentText)
            setSmallIcon(R.drawable.ic_phone_locked_outline)
            setLargeIcon(groupService.getAvatar(group, false))
            setLocalOnly(true)
            setGroup(NotificationGroups.CALLS)
            setGroupSummary(false)
            setCategory(NotificationCompat.CATEGORY_CALL)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            setPublicVersion(publicNotification)
            setTimeoutAfter(TimeUnit.SECONDS.toMillis(30))
            setContentIntent(
                createPendingIntentForConversation(
                    conversationId = conversationId,
                    requestCode = NotificationRequestCodes.getRequestCodeForConversationNotification(
                        conversationId = conversationId,
                        action = ConversationNotificationAction.OPEN,
                    ),
                ),
            )

            addAction(
                buildNotificationAction(
                    icon = R.drawable.ic_phone_locked_outline,
                    title = appContext.getString(R.string.voip_gc_join_call),
                    intent = getGroupCallJoinPendingIntent(conversationId),
                ),
            )

            if (!DeviceInfo.supportsNotificationChannels()) {
                setSound(
                    notificationPreferenceService.getLegacyGroupCallRingtone(),
                    AudioManager.STREAM_RING,
                )
                if (notificationPreferenceService.isLegacyGroupCallVibrate()) {
                    setVibrate(NotificationChannels.VIBRATE_PATTERN_GROUP_CALL)
                }
            }
        }

        notify(
            notificationId = NotificationIDs.INCOMING_GROUP_CALL_NOTIFICATION_ID,
            notification = privateNotification,
            defaultChannelId = NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS,
            tag = getNotificationTagForGroup(group.localGroupId),
        )
    }

    private fun shouldBlockGroupCallNotification(group: GroupModelOld): Boolean {
        // If the notificationTriggerPolicyOverride setting is set to mention only, we will show the call notification.
        // Otherwise, we check if the mute setting would apply at the current system time.
        val notificationTriggerPolicyOverride = group.notificationTriggerPolicyOverride
        val isMuted = notificationTriggerPolicyOverride != null &&
            notificationTriggerPolicyOverride.policy != GroupNotificationTriggerPolicyOverridePolicy.MENTIONED &&
            notificationTriggerPolicyOverride.muteAppliesAt(timeProvider.get())
        return isMuted || doNotDisturbUtil.isDoNotDisturbActive()
    }

    private fun getGroupCallJoinPendingIntent(groupConversationId: GroupConversationId): PendingIntent =
        getPendingIntentForActivity(
            context = appContext,
            requestCode = NotificationRequestCodes.getRequestCodeForConversationNotification(
                conversationId = groupConversationId,
                action = ConversationNotificationAction.GROUP_CALL_JOIN,
            ),
            intent = GroupCallActivity.createJoinCallIntent(
                context = appContext,
                groupId = groupConversationId.groupDatabaseId.toInt(),
            ),
            // update the current pending intent (if it exists), it can be immutable, and it is sufficient if it is one shot.
            flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE or FLAG_ONE_SHOT,
        )

    private fun cancelGroupCallJoinPendingIntent(groupConversationId: GroupConversationId) {
        cancelPendingIntentForActivity(
            context = appContext,
            requestCode = NotificationRequestCodes.getRequestCodeForConversationNotification(
                conversationId = groupConversationId,
                action = ConversationNotificationAction.GROUP_CALL_JOIN,
            ),
            intent = GroupCallActivity.createJoinCallIntent(
                context = appContext,
                groupId = groupConversationId.groupDatabaseId.toInt(),
            ),
        )
    }

    override fun showConversationNotification(messageNotification: ConversationMessageNotification, updateExisting: Boolean) {
        if (ConfigUtils.hasInvalidCredentials()) {
            logger.debug("Credentials are not (or no longer) valid. Suppressing notification.")
            return
        }

        if (notificationPreferenceService.getWizardRunning()) {
            logger.debug("Wizard in progress. Notification suppressed.")
            return
        }

        val currentConversationHandle = messageNotification.conversationHandle
        val conversationId = currentConversationHandle.conversationId

        if (conversationId == visibleConversationId) {
            logger.info("No notification - conversation visible")
            return
        }

        accessExclusively { storeAccessor ->
            val messageNotificationForMessageExists = storeAccessor.messageNotificationForMessageExists(messageNotification.messageUid)

            val conversationIdObfuscated: ConversationIdObfuscated? = if (!messageNotificationForMessageExists || updateExisting) {
                conversationId.obfuscated
            } else {
                null
            }

            if (!messageNotificationForMessageExists && !updateExisting) {
                if (!doNotDisturbUtil.isMessageMuted(
                        notificationTriggerPolicyOverride = currentConversationHandle.notificationTriggerPolicyOverride,
                        rawMessageText = messageNotification.rawMessage,
                    )
                ) {
                    storeAccessor.addMessageNotification(messageNotification)
                }
            } else if (messageNotificationForMessageExists && updateExisting) {
                if (messageNotification.isMessageDeleted) {
                    storeAccessor.removeMessageNotification(messageNotification.messageUid)
                } else if (messageNotification.isMessageEdited) {
                    storeAccessor.replaceMessageNotification(messageNotification)
                }
            }

            if (!storeAccessor.messageNotificationsForConversationExist(conversationId)) {
                cancelConversationMessageNotifications(conversationId)
                return@accessExclusively
            }

            val latestFullName = currentConversationHandle.name.get(appContext)
            val channelId = determineChannelId(currentConversationHandle)
            val defaultChannelId = determineDefaultChannelId(currentConversationHandle)
            if (
                doNotDisturbUtil.isMessageMuted(
                    notificationTriggerPolicyOverride = currentConversationHandle.notificationTriggerPolicyOverride,
                    rawMessageText = messageNotification.rawMessage,
                )
            ) {
                return@accessExclusively
            }

            val notificationSchema = createNotificationSchema(conversationId)

            if (lockAppService.isLocked) {
                if (!updateExisting) {
                    showAppLockedNewMessageNotification(
                        notificationSchema = notificationSchema,
                        messageUid = messageNotification.messageUid,
                        channelId = defaultChannelId,
                    )
                }
                return@accessExclusively
            }

            cancelAppLockedNewMessagesNotification()

            var tickerText: CharSequence?
            var singleMessageText: CharSequence?
            val newMessagesText = getUnreadMessagesCountText(
                unreadConversationsCount = storeAccessor.getConversationsCount(),
                unreadMessagesCount = storeAccessor.getTotalMessageNotificationCount(),
            )

            val isPrivateChat = getConversationCategoryUseCase.call(conversationId) == ConversationCategory.PROTECTED

            if (isPrivateChat) {
                tickerText = newMessagesText
                singleMessageText = newMessagesText
            } else {
                val tickerMessage: CharSequence?
                if (notificationPreferenceService.isShowMessagePreview()) {
                    tickerMessage = messageNotification.message.truncate(MAX_TICKER_TEXT_LENGTH)
                    singleMessageText = messageNotification.message
                } else {
                    tickerMessage = newMessagesText
                    singleMessageText = newMessagesText
                }
                tickerText = appContext.getString(
                    R.string.notification_preview_pattern,
                    latestFullName,
                    tickerMessage,
                )
            }

            val now = timeProvider.get()
            val onlyAlertOnce = messageNotification.isMessageEdited ||
                messageNotification.isMessageDeleted ||
                (now - currentConversationHandle.lastNotificationTime) < NOTIFY_AGAIN_TIMEOUT
            currentConversationHandle.lastNotificationTime = now

            val numberOfMessageNotificationsForCurrentConversation = storeAccessor.getMessageNotificationCountForConversation(conversationId)
            val summaryText = appContext.resources.getQuantityString(
                R.plurals.new_messages,
                numberOfMessageNotificationsForCurrentConversation,
                numberOfMessageNotificationsForCurrentConversation,
            )

            if (!notificationPreferenceService.isShowMessagePreview() || isPrivateChat) {
                singleMessageText = summaryText
                tickerText = summaryText
            }

            val publicNotification = buildNotification(appContext, channelId) {
                setContentTitle(summaryText)
                setContentText(appContext.getString(R.string.notification_hidden_text))
                setSmallIcon(R.drawable.ic_notification_small)
                setOnlyAlertOnce(onlyAlertOnce)
            }

            val privateNotification = buildNotification(appContext, channelId) {
                setContentTitle(latestFullName)
                setContentText(singleMessageText)
                setTicker(tickerText)
                setSmallIcon(R.drawable.ic_notification_small)
                setLargeIcon(currentConversationHandle.loadAvatar())
                setGroup(NotificationGroups.CONVERSATIONS)
                setGroupSummary(false)
                setOnlyAlertOnce(onlyAlertOnce)
                setPriority(notificationPreferenceService.getLegacyNotificationPriority())
                setCategory(NotificationCompat.CATEGORY_MESSAGE)
                setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                setPublicVersion(publicNotification)

                if (!DeviceInfo.supportsNotificationChannels()) {
                    if (notificationSchema.soundUri != null) {
                        setSound(notificationSchema.soundUri, AudioManager.STREAM_NOTIFICATION)
                    }
                    if (notificationSchema.shouldVibrate) {
                        setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR)
                    }
                    if (notificationSchema.shouldUseLight) {
                        setLights(ContextCompat.getColor(appContext, R.color.md_theme_light_primary), 2500, 2500)
                    }
                }

                // Add identity to notification for system DND priority override
                if (messageNotification.shouldApplySenderPerson) {
                    addPerson(messageNotification.senderPerson)
                }

                if (notificationPreferenceService.isShowMessagePreview() && !isPrivateChat) {
                    setStyle(
                        getMessagingStyle(
                            conversationHandle = currentConversationHandle,
                            messageNotifications = storeAccessor.getMessageNotificationsForConversation(conversationId),
                        ),
                    )
                    if (conversationIdObfuscated != null) {
                        setShortcutId(conversationIdObfuscated.value)
                        setLocusId(LocusIdCompat(conversationIdObfuscated.value))
                    }

                    val replyPendingIntent = getPendingIntentForService(
                        context = appContext,
                        requestCode = currentConversationHandle.getRequestCode(ConversationNotificationAction.REPLY),
                        intent = NotificationActionService.createReplyIntent(
                            appContext,
                            conversationId,
                        ),
                        // Note that this pending intent needs to be mutable because the reply text needs to be added to it.
                        flags = FLAG_UPDATE_CURRENT or FLAG_MUTABLE,
                    )

                    val markReadPendingIntent = getPendingIntentForService(
                        context = appContext,
                        requestCode = currentConversationHandle.getRequestCode(ConversationNotificationAction.MARK_AS_READ),
                        intent = NotificationActionService.createMarkAsReadIntent(
                            appContext,
                            conversationId,
                        ),
                        flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE or FLAG_ONE_SHOT,
                    )

                    val ackPendingIntent = getPendingIntentForService(
                        context = appContext,
                        requestCode = currentConversationHandle.getRequestCode(ConversationNotificationAction.ACK),
                        intent = NotificationActionService.createAckIntent(
                            appContext,
                            conversationId,
                            messageNotification.messageId,
                        ),
                        flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE or FLAG_ONE_SHOT,
                    )

                    val decPendingIntent = getPendingIntentForService(
                        context = appContext,
                        requestCode = currentConversationHandle.getRequestCode(ConversationNotificationAction.DEC),
                        intent = NotificationActionService.createDecIntent(
                            appContext,
                            conversationId,
                            messageNotification.messageId,
                        ),
                        flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE or FLAG_ONE_SHOT,
                    )

                    addConversationNotificationActions(
                        replyPendingIntent = replyPendingIntent,
                        ackPendingIntent = ackPendingIntent,
                        markReadPendingIntent = markReadPendingIntent,
                        conversationMessageNotification = messageNotification,
                        unreadMessagesCount = numberOfMessageNotificationsForCurrentConversation,
                        newestConversationHandle = currentConversationHandle,
                    )
                    addWearableExtender(
                        newestConversationHandle = currentConversationHandle,
                        ackPendingIntent = ackPendingIntent,
                        decPendingIntent = decPendingIntent,
                        replyPendingIntent = replyPendingIntent,
                        markReadPendingIntent = markReadPendingIntent,
                        numberOfUnreadMessagesForThisConversation = numberOfMessageNotificationsForCurrentConversation,
                    )
                }

                setContentIntent(
                    createPendingIntentForConversation(
                        conversationId = conversationId,
                        requestCode = currentConversationHandle.getRequestCode(ConversationNotificationAction.OPEN),
                    ),
                )
                setDeleteIntent(
                    getPendingIntentForBroadcast(
                        context = appContext,
                        requestCode = NotificationRequestCodes.DISMISS_NOTIFICATION,
                        intent = NotificationDismissedBroadcastReceiver.createIntent(appContext),
                        flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE,
                    ),
                )
            }

            if (!updateExisting || notificationExists(currentConversationHandle.notificationId)) {
                logger.info("Showing notification, uid={}", messageNotification.messageUid)
                notify(
                    notificationId = currentConversationHandle.notificationId,
                    notification = privateNotification,
                    schema = notificationSchema,
                    defaultChannelId = defaultChannelId,
                )
                notifyConversationsSummary(defaultChannelId)
                badgeUpdater.showIconBadge(storeAccessor.getTotalMessageNotificationCount())
            }
        }
    }

    private fun notifyConversationsSummary(channelId: String) {
        notify(
            NotificationIDs.CONVERSATIONS_SUMMARY_NOTIFICATION_ID,
            buildNotification(appContext, channelId) {
                setGroupSummary(true)
                setGroup(NotificationGroups.CONVERSATIONS)
                setSmallIcon(R.drawable.ic_notification_small)
                setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            },
        )
    }

    private fun createNotificationSchema(conversationId: ConversationId): NotificationSchema =
        when (conversationId) {
            is GroupConversationId -> {
                NotificationSchema(
                    shouldVibrate = notificationPreferenceService.isLegacyGroupVibrate(),
                    soundUri = ringtoneService.getGroupRingtone(conversationId),
                    shouldUseLight = notificationPreferenceService.isLegacyGroupNotificationLightEnabled(),
                )
            }
            is ContactConversationId -> {
                NotificationSchema(
                    shouldVibrate = notificationPreferenceService.isLegacyNotificationVibrate(),
                    soundUri = ringtoneService.getContactRingtone(conversationId),
                    shouldUseLight = notificationPreferenceService.isLegacyNotificationLightEnabled(),
                )
            }
            else -> throw IllegalStateException("Unsupported messageReceiver")
        }

    private fun getUnreadMessagesCountText(unreadConversationsCount: Int, unreadMessagesCount: Int): String =
        if (unreadConversationsCount > 1) {
            appContext.resources.getQuantityString(
                R.plurals.new_messages_in_chats,
                unreadMessagesCount,
                unreadMessagesCount,
                unreadConversationsCount,
            )
        } else {
            appContext.resources.getQuantityString(
                R.plurals.new_messages,
                unreadMessagesCount,
                unreadMessagesCount,
            )
        }

    private fun buildUserPerson(
        userIdentity: Identity,
        contactService: ContactService,
    ): Person = buildPerson {
        setName(appContext.getString(R.string.me_myself_and_i))
        setKey(ContactConversationId(userIdentity.value).obfuscated.value)
        contactService.getAvatar(userIdentity.value, false)
            ?.let { avatar ->
                setIcon(IconCompat.createWithBitmap(avatar))
            }
    }

    private fun getMessagingStyle(
        conversationHandle: NotificationConversationHandle,
        messageNotifications: List<ConversationMessageNotification>,
    ): NotificationCompat.MessagingStyle? {
        val contactService = contactService
            ?: run {
                logger.warn("Contact service is null")
                return null
            }

        val userIdentity = identityProvider.getIdentity()
            ?: return null
        val userPerson = buildUserPerson(
            userIdentity = userIdentity,
            contactService = contactService,
        )

        val conversationName = conversationHandle.name.get(appContext)
        val isGroupConversation = conversationHandle.conversationId is GroupConversationId

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle(if (isGroupConversation) conversationName else null)
            .setGroupConversation(isGroupConversation)

        messageNotifications.take(NotificationCompat.MessagingStyle.MAXIMUM_RETAINED_MESSAGES)
            .map { notification ->
                val messageText = notification.message
                val created = notification.createdAt.toEpochMilli()
                val person =
                    if (notification.shouldApplySenderPerson) {
                        if (isGroupConversation) {
                            notification.senderPerson
                        } else {
                            (notification.senderPerson?.toBuilder() ?: Person.Builder())
                                .setName(conversationName)
                                .build()
                        }
                    } else {
                        null
                    }
                val message = NotificationCompat.MessagingStyle.Message(messageText, created, person)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && notification.thumbnailMimeType != null) {
                    notification.getOrCreateThumbnail()
                        ?.let { thumbnailUri ->
                            message.setData(notification.thumbnailMimeType, thumbnailUri)
                        }
                }
                message
            }
            .reversed()
            .forEach { message ->
                messagingStyle.addMessage(message)
            }
        return messagingStyle
    }

    /**
     * This [NotificationCompat.MessagingStyle] bundles multiple reaction notifications for a single conversation.
     *
     * Note that the amount of [NotificationCompat.MessagingStyle.Message]s added to the style can be greater than the count of
     * [reactionNotifications] because we have to expand a message that has a thumbnail into 2 separate messages. Otherwise, a notification that has
     * both a text and a thumbnail uri will only render the thumbnail and not the text.
     */
    private fun getEmojiReactionStyle(
        conversationHandle: NotificationConversationHandle,
        reactionNotifications: List<ConversationReactionNotification>,
    ): NotificationCompat.MessagingStyle? {
        val contactService = contactService
            ?: run {
                logger.warn("Contact service is null")
                return null
            }

        val contactModelRepository = contactModelRepository
            ?: run {
                logger.warn("Contact model repository is null")
                return null
            }

        val userIdentity = identityProvider.getIdentity()
            ?: return null
        val userPerson = buildUserPerson(
            userIdentity = userIdentity,
            contactService = contactService,
        )

        val conversationName = conversationHandle.name.get(appContext)
        val isGroupConversation = conversationHandle.conversationId is GroupConversationId

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle(if (isGroupConversation) conversationName else null)
            // We explicitly don't want the group styling even if this messageReceiver is of type group
            .setGroupConversation(false)

        val contactNameFormat = preferenceService.getContactNameFormat()

        reactionNotifications
            .take(NotificationCompat.MessagingStyle.MAXIMUM_RETAINED_MESSAGES)
            .flatMap { reactionNotification ->
                val messageText = reactionNotification.resolveNotificationContentPrivate(
                    context = appContext,
                    contactModelRepository = contactModelRepository,
                    contactNameFormat = contactNameFormat,
                ) ?: return@flatMap emptyList()
                val messageTimestamp = reactionNotification.createdAt.toEpochMilli()
                val messagePerson =
                    if (reactionNotification.shouldApplySenderPerson) {
                        (reactionNotification.senderPerson?.toBuilder() ?: Person.Builder())
                            .setName(conversationName)
                            .build()
                    } else {
                        null
                    }
                val messages = mutableListOf<NotificationCompat.MessagingStyle.Message>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && reactionNotification.thumbnailMimeType != null) {
                    reactionNotification
                        .getOrCreateThumbnail()
                        ?.let { thumbnailUri ->
                            // Add 1 to have this thumbnail notification rendered below the actual message (caption) notification
                            val timestamp = messageTimestamp + 1L
                            messages.add(
                                NotificationCompat.MessagingStyle.Message(
                                    /* text = */
                                    "",
                                    /* timestamp = */
                                    timestamp,
                                    /* person = */
                                    messagePerson,
                                ).apply {
                                    setData(reactionNotification.thumbnailMimeType, thumbnailUri)
                                },
                            )
                        }
                }
                messages.add(
                    NotificationCompat.MessagingStyle.Message(
                        /* text = */
                        messageText,
                        /* timestamp = */
                        messageTimestamp,
                        /* person = */
                        messagePerson,
                    ),
                )
                messages
            }
            .reversed()
            .forEach(messagingStyle::addMessage)
        return messagingStyle
    }

    private fun NotificationCompat.Builder.addConversationNotificationActions(
        replyPendingIntent: PendingIntent,
        ackPendingIntent: PendingIntent,
        markReadPendingIntent: PendingIntent,
        conversationMessageNotification: ConversationMessageNotification,
        unreadMessagesCount: Int,
        newestConversationHandle: NotificationConversationHandle,
    ) {
        var showMarkAsReadAction = false
        if (notificationPreferenceService.isShowMessagePreview() &&
            getConversationCategoryUseCase.call(newestConversationHandle.conversationId) == ConversationCategory.DEFAULT
        ) {
            val replyAction = buildNotificationAction(
                icon = R.drawable.ic_reply_black_18dp,
                title = appContext.getString(R.string.wearable_reply),
                intent = replyPendingIntent,
            ) {
                addRemoteInput(
                    RemoteInput.Builder(AppConstants.EXTRA_VOICE_REPLY)
                        .setLabel(appContext.getString(R.string.compose_message_and_enter))
                        .build(),
                )
                setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                setShowsUserInterface(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowGeneratedReplies(!notificationPreferenceService.getDisableSmartReplies())
                }
            }
            addAction(replyAction)

            when (val conversationId = newestConversationHandle.conversationId) {
                is ContactConversationId -> {
                    if (conversationMessageNotification.messageType == MessageType.VOIP_STATUS) {
                        val identity = conversationId.identity
                        val callActivityIntent = Intent(appContext, CallActivity::class.java)
                            .putExtra(
                                VoipCallService.EXTRA_ACTIVITY_MODE,
                                CallActivity.MODE_OUTGOING_CALL,
                            )
                            .putExtra(VoipCallService.EXTRA_CONTACT_IDENTITY, identity)
                            .putExtra(VoipCallService.EXTRA_IS_INITIATOR, true)
                            .putExtra(VoipCallService.EXTRA_CALL_ID, -1L)
                        val callPendingIntent = getPendingIntentForActivity(
                            context = appContext,
                            requestCode = NotificationRequestCodes.getRequestCodeForConversationNotification(
                                conversationId = ContactConversationId(identity),
                                action = ConversationNotificationAction.CALL,
                            ),
                            intent = callActivityIntent,
                            // A call pending intent can be immutable and one shot.
                            flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE or FLAG_ONE_SHOT,
                        )
                        addAction(
                            buildNotificationAction(
                                R.drawable.ic_call_white_24dp,
                                appContext.getString(R.string.voip_return_call),
                                callPendingIntent,
                            ) {
                                setShowsUserInterface(true)
                                setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL)
                            },
                        )
                    } else {
                        if (unreadMessagesCount == 1) {
                            addAction(getThumbsUpAction(ackPendingIntent))
                        }
                        showMarkAsReadAction = true
                    }
                }
                is GroupConversationId -> {
                    if (unreadMessagesCount == 1) {
                        addAction(getThumbsUpAction(ackPendingIntent))
                    }
                    showMarkAsReadAction = true
                }
                is DistributionListConversationId -> {
                    // no dedicated actions for distribution list messages
                    showMarkAsReadAction = false
                }
            }
        }

        val markAsReadAction = getMarkAsReadAction(markReadPendingIntent)
        if (showMarkAsReadAction) {
            addAction(markAsReadAction)
        } else {
            addInvisibleAction(markAsReadAction)
        }
    }

    private fun getMarkAsReadAction(markReadPendingIntent: PendingIntent): NotificationCompat.Action =
        buildNotificationAction(
            icon = R.drawable.ic_mark_read_bitmap,
            title = appContext.getString(
                R.string.mark_read_short,
            ),
            intent = markReadPendingIntent,
        ) {
            setShowsUserInterface(false)
            setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
        }

    private fun getThumbsUpAction(ackPendingIntent: PendingIntent): NotificationCompat.Action =
        buildNotificationAction(
            icon = R.drawable.emoji_thumbs_up,
            title = appContext.getString(R.string.acknowledge),
            intent = ackPendingIntent,
        ) {
            setShowsUserInterface(false)
            setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
        }

    private fun NotificationCompat.Builder.addWearableExtender(
        newestConversationHandle: NotificationConversationHandle,
        ackPendingIntent: PendingIntent?,
        decPendingIntent: PendingIntent?,
        replyPendingIntent: PendingIntent?,
        markReadPendingIntent: PendingIntent?,
        numberOfUnreadMessagesForThisConversation: Int,
    ) {
        val replyAction = buildNotificationAction(
            icon = R.drawable.ic_wear_full_reply,
            title = appContext.getString(R.string.wearable_reply),
            intent = replyPendingIntent,
        ) {
            addRemoteInput(
                RemoteInput.Builder(AppConstants.EXTRA_VOICE_REPLY)
                    .setLabel(appContext.getString(R.string.wearable_reply_label, newestConversationHandle.name))
                    .setChoices(appContext.resources.getStringArray(R.array.wearable_reply_choices))
                    .build(),
            )
            setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            setShowsUserInterface(false)
            extend(
                NotificationCompat.Action.WearableExtender()
                    .setHintDisplayActionInline(true),
            )
        }

        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(replyAction)

        if (notificationPreferenceService.isShowMessagePreview() &&
            getConversationCategoryUseCase.call(newestConversationHandle.conversationId) == ConversationCategory.DEFAULT
        ) {
            if (numberOfUnreadMessagesForThisConversation == 1 && newestConversationHandle.conversationId is ContactConversationId) {
                wearableExtender.addAction(
                    buildNotificationAction(
                        icon = R.drawable.emoji_thumbs_up,
                        title = appContext.getString(R.string.acknowledge),
                        intent = ackPendingIntent,
                    ) {
                        setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
                    },
                )

                wearableExtender.addAction(
                    buildNotificationAction(
                        icon = R.drawable.emoji_thumbs_down,
                        title = appContext.getString(R.string.decline),
                        intent = decPendingIntent,
                    ) {
                        setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_DOWN)
                    },
                )
            }

            wearableExtender.addAction(
                buildNotificationAction(
                    icon = R.drawable.ic_mark_read,
                    title = appContext.getString(R.string.mark_read),
                    intent = markReadPendingIntent,
                ) {
                    setShowsUserInterface(false)
                    setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                },
            )
        }
        extend(wearableExtender)
        extend(
            NotificationCompat.CarExtender().setLargeIcon(newestConversationHandle.loadAvatar()),
        )
    }

    private fun notificationExists(notificationId: Int): Boolean =
        try {
            notificationManagerCompat.activeNotifications.any { notification ->
                notification.id == notificationId
            }
        } catch (e: Exception) {
            logger.warn("Failed to check if notification exists", e)
            false
        }

    private fun cancelConversationSummaryNotificationIfNeeded() {
        accessExclusively {
            try {
                val activeNotifications = notificationManagerCompat.activeNotifications
                val hasConversationNotifications = activeNotifications.any { notification ->
                    notification.id != NotificationIDs.CONVERSATIONS_SUMMARY_NOTIFICATION_ID &&
                        notification.notification?.group == NotificationGroups.CONVERSATIONS
                }
                if (!hasConversationNotifications) {
                    cancel(NotificationIDs.CONVERSATIONS_SUMMARY_NOTIFICATION_ID)
                }
            } catch (e: Exception) {
                logger.error("Failed to cancel conversation summary notification", e)
            }
        }
    }

    private fun showAppLockedNewMessageNotification(
        notificationSchema: NotificationSchema? = null,
        messageUid: MessageUid? = null,
        channelId: String,
    ) {
        val notification = buildNotification(appContext, channelId) {
            setSmallIcon(R.drawable.ic_notification_small)
            setContentTitle(appContext.getString(R.string.new_messages_locked))
            setContentText(appContext.getString(R.string.new_messages_locked_description))
            setTicker(appContext.getString(R.string.new_messages_locked))
            setCategory(NotificationCompat.CATEGORY_MESSAGE)
            setPriority(notificationPreferenceService.getLegacyNotificationPriority())
            setOnlyAlertOnce(false)
            setContentIntent(createPendingIntentForHomeActivity())
            setSound(notificationSchema?.soundUri, AudioManager.STREAM_NOTIFICATION)

            if (notificationSchema?.shouldVibrate == true) {
                setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR)
            }
        }

        notify(
            notificationId = NotificationIDs.NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID,
            notification = notification,
            defaultChannelId = channelId,
        )

        badgeUpdater.showIconBadge(0)

        logger.info(
            "Showing generic notification (app locked), uid = {}, channelId = {} ",
            messageUid,
            channelId,
        )
    }

    private fun createPendingIntentForConversation(conversationId: ConversationId, requestCode: Int): PendingIntent {
        val intent = ComposeMessageActivity.createIntent(appContext, conversationId)
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION,
            )
        return createPendingIntentWithTaskStack(intent, requestCode)
    }

    override fun showServerMessageNotification() {
        if (doNotDisturbUtil.isDoNotDisturbActive()) {
            return
        }

        val notification = buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_NOTICE) {
            setSmallIcon(R.drawable.ic_error_red_24dp)
            setTicker(appContext.getString(R.string.server_message_title))
            setContentTitle(appContext.getString(R.string.server_message_title))
            setContentText(appContext.getString(R.string.tap_here_for_more))
            setContentIntent(
                getPendingIntentForActivity(
                    context = appContext,
                    requestCode = NotificationRequestCodes.SERVER_MESSAGE,
                    intent = ServerMessageActivity.createIntent(appContext),
                    flags = FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT,
                ),
            )
            setLocalOnly(true)
            setPriority(NotificationCompat.PRIORITY_MAX)
            setAutoCancel(true)
        }
        notify(
            notificationId = NotificationIDs.SERVER_MESSAGE_NOTIFICATION_ID,
            notification = notification,
            defaultChannelId = NotificationChannels.NOTIFICATION_CHANNEL_NOTICE,
        )
    }

    override fun showUnsentMessageNotification(failedMessages: List<MessageModel>) {
        val numberOfFailedMessages = failedMessages.size

        if (numberOfFailedMessages == 0) {
            cancel(NotificationIDs.UNSENT_MESSAGE_NOTIFICATION_ID)
            return
        }

        val sendPendingIntent = getPendingIntentForBroadcast(
            context = appContext,
            requestCode = NotificationRequestCodes.UNSENT_NOTIFICATIONS_SEND,
            intent = ReSendMessagesBroadcastReceiver.createIntent(appContext, failedMessages),
            flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE or FLAG_ONE_SHOT,
        )

        val tryAgainAction = buildNotificationAction(
            icon = R.drawable.ic_wear_full_retry,
            title = appContext.getString(R.string.try_again),
            intent = sendPendingIntent,
        )
        val wearableExtender = NotificationCompat.WearableExtender()
            .addAction(tryAgainAction)

        val cancelIntent = CancelResendMessagesBroadcastReceiver.createIntent(appContext, failedMessages)

        val content = appContext.getResources()
            .getQuantityString(R.plurals.sending_message_failed, numberOfFailedMessages, numberOfFailedMessages)
        val notification = buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_ALERT) {
            setSmallIcon(R.drawable.ic_error_red_24dp)
            setTicker(content)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setCategory(NotificationCompat.CATEGORY_ERROR)
            setContentIntent(createPendingIntentForHomeActivity())
            extend(wearableExtender)
            setContentTitle(appContext.getString(R.string.app_name))
            setContentText(content)
            setStyle(NotificationCompat.BigTextStyle().bigText(content))
            setDeleteIntent(
                getPendingIntentForBroadcast(
                    context = appContext,
                    requestCode = NotificationRequestCodes.UNSENT_NOTIFICATIONS_CANCEL,
                    intent = cancelIntent,
                    flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE or FLAG_ONE_SHOT,
                ),
            )
            addAction(
                R.drawable.ic_refresh_white_24dp,
                appContext.getString(R.string.try_again),
                sendPendingIntent,
            )
        }

        notify(
            notificationId = NotificationIDs.UNSENT_MESSAGE_NOTIFICATION_ID,
            notification = notification,
            defaultChannelId = NotificationChannels.NOTIFICATION_CHANNEL_ALERT,
        )
    }

    override fun showForwardSecurityMessageRejectedNotification(messageReceiver: MessageReceiver<*>) {
        fsNotificationManager.showForwardSecurityNotification(messageReceiver)
    }

    override fun showSafeBackupFailed(fullDaysSinceLastBackup: Int) {
        val notification = buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_ALERT) {
            val content = appContext.getString(R.string.safe_failed_notification, fullDaysSinceLastBackup)

            setSmallIcon(R.drawable.ic_error_red_24dp)
            setTicker(content)
            setLocalOnly(true)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setCategory(NotificationCompat.CATEGORY_ERROR)
            setContentTitle(appContext.getString(R.string.app_name))
            setContentText(content)
            setStyle(NotificationCompat.BigTextStyle().bigText(content))

            setContentIntent(
                getPendingIntentForActivity(
                    context = appContext,
                    requestCode = NotificationRequestCodes.SAFE_BACKUP_FAILED,
                    intent = BackupAdminActivity.createIntent(appContext),
                    flags = FLAG_IMMUTABLE,
                ),
            )
        }
        notify(
            notificationId = NotificationIDs.SAFE_FAILED_NOTIFICATION_ID,
            notification = notification,
            defaultChannelId = NotificationChannels.NOTIFICATION_CHANNEL_ALERT,
        )
    }

    override fun showNewSyncedContactsNotification(contactModels: List<ContactModel>) {
        if (contactModels.isEmpty()) {
            return
        }

        val message: String
        val openPendingIntent: PendingIntent

        val contactModel = contactModels.singleOrNull()
        val contactNameFormat = preferenceService.getContactNameFormat()
        if (contactModel == null) {
            val contactListString = contactModels.joinToString(separator = ", ") { contactModel ->
                NameUtil.getContactDisplayName(contactModel, contactNameFormat)
            }
            message = appContext.getString(
                R.string.notification_contact_has_joined_multiple,
                contactModels.size,
                contactListString,
            )
            openPendingIntent = createPendingIntentWithTaskStack(
                intent = HomeActivity.createIntent(appContext, showContacts = true)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                requestCode = NotificationRequestCodes.NEW_CONTACTS_SYNCED,
            )
        } else {
            val name = NameUtil.getContactDisplayName(contactModel, contactNameFormat)
            message = appContext.getString(R.string.notification_contact_has_joined, name)
            openPendingIntent = createPendingIntentForConversation(
                conversationId = ContactConversationId(contactModel.identity),
                requestCode = NotificationRequestCodes.NEW_CONTACTS_SYNCED,
            )
        }

        val notification = buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS) {
            setSmallIcon(R.drawable.ic_notification_small)
            setContentTitle(appContext.getString(R.string.notification_channel_new_contact))
            setContentText(message)
            setContentIntent(openPendingIntent)
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setAutoCancel(true)

            if (notificationPreferenceService.isLegacyNotificationVibrate()) {
                setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR)
            }
        }
        notify(
            notificationId = NotificationIDs.NEW_SYNCED_CONTACTS_NOTIFICATION_ID,
            notification = notification,
            defaultChannelId = NotificationChannels.NOTIFICATION_CHANNEL_NEW_SYNCED_CONTACTS,
        )
    }

    override fun showWebclientResumeFailed(message: String) {
        val notification = buildNotification(appContext, NotificationChannels.NOTIFICATION_CHANNEL_NOTICE) {
            setSmallIcon(R.drawable.ic_web_notification)
            setTicker(message)
            setLocalOnly(true)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setCategory(NotificationCompat.CATEGORY_ERROR)
            setContentTitle(appContext.getString(R.string.app_name))
            setContentText(message)
            setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }
        notify(
            notificationId = NotificationIDs.WEB_RESUME_FAILED_NOTIFICATION_ID,
            notification = notification,
            defaultChannelId = NotificationChannels.NOTIFICATION_CHANNEL_NOTICE,
        )
    }

    override fun showEmojiReactionNotification(reactionNotification: ConversationReactionNotification) {
        if (ConfigUtils.hasInvalidCredentials()) {
            logger.debug("Credentials are not (or no longer) valid. Suppressing reaction notification.")
            return
        }

        if (notificationPreferenceService.getWizardRunning()) {
            logger.debug("Wizard in progress. Reaction notification suppressed.")
            return
        }

        if (!reactionNotification.anyEmojiReactionExists) {
            logger.error("Cannot show/update an emoji reaction notification without any reaction")
            return
        }

        if (lockAppService.isLocked) {
            logger.debug("App is locked. Reaction notification suppressed.")
            return
        }

        val conversationHandle = reactionNotification.conversationHandle
        val conversationId = conversationHandle.conversationId
        if (conversationId == visibleConversationId) {
            logger.info("No reaction notification - conversation visible")
            return
        }

        val conversationIsMutedNow = reactionNotification
            .conversationHandle
            .notificationTriggerPolicyOverride
            ?.muteAppliesAt(timeProvider.get()) == true

        if (conversationIsMutedNow || doNotDisturbUtil.isDoNotDisturbActive()) {
            return
        }

        accessExclusively { accessor ->
            val reactionNotificationExists = accessor.reactionNotificationForMessageExists(reactionNotification.messageUid)
            if (reactionNotificationExists) {
                accessor.replaceReactionNotification(reactionNotification)
            } else {
                accessor.addReactionNotification(reactionNotification)
            }

            val now = timeProvider.get()
            val onlyAlertOnce = (now - conversationHandle.lastNotificationTime) < NOTIFY_AGAIN_TIMEOUT
            conversationHandle.lastNotificationTime = now

            postEmojiReactionNotification(
                conversationHandle = conversationHandle,
                headerReaction = reactionNotification,
                reactionNotifications = accessor.getReactionNotificationsForConversation(conversationId),
                onlyAlertOnce = onlyAlertOnce,
            )

            badgeUpdater.showIconBadge(accessor.getTotalMessageNotificationCount())
        }
    }

    override fun cancelEmojiReactionNotification(conversationId: ConversationId, messageUid: MessageUid) {
        accessExclusively { accessor ->
            accessor.removeReactionNotification(messageUid)
            // It is important here that we cancel the notification even if the conversationNotificationStore does not know about it,
            //  as the store gets reset every time the app is stopped but notifications might persist longer
            cancel(
                notificationId = getNotificationIdForConversation(
                    conversationId = conversationId,
                    offset = NotificationIDs.Offset.EMOJI_REACTION_NOTIFICATION,
                ),
            )
            val remainingReactionNotifications = accessor.getReactionNotificationsForConversation(conversationId)
            if (remainingReactionNotifications.isNotEmpty()) {
                // More reaction notifications for this conversation exist, so update the group of notifications
                val newestRemainingReactionNotification = remainingReactionNotifications.maxBy { conversationReactionNotification ->
                    conversationReactionNotification.createdAt.toEpochMilli()
                }
                postEmojiReactionNotification(
                    conversationHandle = newestRemainingReactionNotification.conversationHandle,
                    headerReaction = newestRemainingReactionNotification,
                    reactionNotifications = remainingReactionNotifications,
                    onlyAlertOnce = true,
                )
            }
            cancelConversationSummaryNotificationIfNeeded()
            badgeUpdater.showIconBadge(accessor.getTotalMessageNotificationCount())
        }
    }

    /**
     * Post *all* emoji reaction notifications for *one* conversation in a grouped way.
     *
     * @param headerReaction The most recent reaction notification for this conversation
     * @param reactionNotifications All (including [headerReaction]) reactions for this conversation
     * @param onlyAlertOnce See [NotificationCompat.Builder.setOnlyAlertOnce]
     */
    private fun postEmojiReactionNotification(
        conversationHandle: NotificationConversationHandle,
        headerReaction: ConversationReactionNotification,
        reactionNotifications: List<ConversationReactionNotification>,
        onlyAlertOnce: Boolean,
    ) {
        val conversationId = conversationHandle.conversationId
        val conversationIdObfuscated = conversationId.obfuscated
        val channelId = determineChannelId(conversationHandle)
        val defaultChannelId = determineDefaultChannelId(conversationHandle)
        val notificationSchema = createNotificationSchema(conversationId)
        val isPrivateConversation = getConversationCategoryUseCase.call(conversationId) == ConversationCategory.PROTECTED
        val showMessagePreviewsBySetting = notificationPreferenceService.isShowMessagePreview()

        val publicNotification = buildNotification(appContext, channelId) {
            setContentTitle(headerReaction.resolveNotificationTitlePublic(appContext))
            setContentText(headerReaction.resolveNotificationContentPublic(appContext))
            setSmallIcon(R.drawable.ic_notification_small)
            setOnlyAlertOnce(onlyAlertOnce)
            setGroup(NotificationGroups.CONVERSATIONS)
            setGroupSummary(false)
        }

        val contentTitle = if (isPrivateConversation) {
            headerReaction.resolveNotificationTitlePublic(appContext)
        } else {
            headerReaction.resolveNotificationTitlePrivate(appContext)
        }

        val contentText: String? = if (!isPrivateConversation && showMessagePreviewsBySetting) {
            contactModelRepository?.let { repo ->
                headerReaction.resolveNotificationContentPrivate(
                    context = appContext,
                    contactModelRepository = repo,
                    contactNameFormat = preferenceService.getContactNameFormat(),
                )
            }
        } else {
            headerReaction.resolveNotificationContentPublic(appContext)
        }

        if (contentText == null) {
            logger.error("Failed to resolve emoji reaction notification content text")
            return
        }

        val tickerText = contentText.truncate(MAX_TICKER_TEXT_LENGTH)

        val privateNotification = buildNotification(appContext, channelId) {
            setContentTitle(contentTitle)
            setContentText(contentText)
            setTicker(tickerText)
            setSmallIcon(R.drawable.ic_notification_small)
            setLargeIcon(conversationHandle.loadAvatar())
            setGroup(NotificationGroups.CONVERSATIONS)
            setGroupSummary(false)
            setOnlyAlertOnce(onlyAlertOnce)
            setPriority(notificationPreferenceService.getLegacyNotificationPriority())
            setCategory(NotificationCompat.CATEGORY_MESSAGE)
            setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            setPublicVersion(publicNotification)

            if (!DeviceInfo.supportsNotificationChannels()) {
                if (notificationSchema.soundUri != null) {
                    setSound(notificationSchema.soundUri, AudioManager.STREAM_NOTIFICATION)
                }
                if (notificationSchema.shouldVibrate) {
                    setVibrate(NotificationChannels.VIBRATE_PATTERN_REGULAR)
                }
                if (notificationSchema.shouldUseLight) {
                    setLights(ContextCompat.getColor(appContext, R.color.md_theme_light_primary), 2500, 2500)
                }
            }

            if (headerReaction.shouldApplySenderPerson) {
                addPerson(headerReaction.senderPerson)
            }

            if (showMessagePreviewsBySetting && !isPrivateConversation) {
                setStyle(
                    getEmojiReactionStyle(
                        conversationHandle = conversationHandle,
                        reactionNotifications = reactionNotifications,
                    ),
                )
                setShortcutId(conversationIdObfuscated.value)
                setLocusId(LocusIdCompat(conversationIdObfuscated.value))
            }

            setContentIntent(
                createPendingIntentForConversationJumpToMessage(
                    message = headerReaction.targetMessage,
                    requestCode = conversationHandle.getRequestCode(ConversationNotificationAction.OPEN),
                ),
            )
            setDeleteIntent(
                getPendingIntentForBroadcast(
                    context = appContext,
                    requestCode = NotificationRequestCodes.DISMISS_NOTIFICATION,
                    intent = NotificationDismissedBroadcastReceiver.createIntent(appContext),
                    flags = FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE,
                ),
            )
        }

        notify(
            notificationId = conversationHandle.notificationIdEmojiReaction,
            notification = privateNotification,
            schema = notificationSchema,
            defaultChannelId = defaultChannelId,
        )
        notifyConversationsSummary(defaultChannelId)
    }

    private fun createPendingIntentForConversationJumpToMessage(message: MessageModel, requestCode: Int): PendingIntent {
        val intent = ComposeMessageActivity.createIntentJumpToMessage(appContext, message)
            .setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION,
            )
        return createPendingIntentWithTaskStack(intent, requestCode)
    }

    override fun cancelAllConversationNotifications(conversationId: ConversationId) {
        cancelAllConversationNotifications(setOf(conversationId))
    }

    override fun cancelAllConversationNotifications(conversationIds: Set<ConversationId>) {
        val filteredConversationIds = conversationIds.filter { it !is DistributionListConversationId }
        if (filteredConversationIds.isEmpty()) {
            return
        }
        accessExclusively { storeAccessor ->
            filteredConversationIds.forEach { conversationId ->
                // It is important here that we cancel the notification even if the conversationNotificationStore does not know about it,
                //  as the store gets reset every time the app is stopped but notifications might persist longer
                cancel(
                    notificationId = getNotificationIdForConversation(conversationId),
                )
                cancel(
                    notificationId = getNotificationIdForConversation(
                        conversationId = conversationId,
                        offset = NotificationIDs.Offset.EMOJI_REACTION_NOTIFICATION,
                    ),
                )
                storeAccessor.removeNotificationsForConversation(conversationId)
            }
            if (!storeAccessor.notificationsExist()) {
                cancelAppLockedNewMessagesNotification()
            }
            cancelConversationSummaryNotificationIfNeeded()
            badgeUpdater.showIconBadge(storeAccessor.getTotalMessageNotificationCount())
        }

        // TODO(ANDR-4706): This should not be called from here, but from a monitor instead
        widgetUpdater.updateWidgets()
    }

    override fun cancelConversationMessageNotifications(conversationId: ConversationId) {
        if (conversationId is DistributionListConversationId) {
            return
        }
        accessExclusively { storeAccessor ->
            // It is important here that we cancel the notification even if the conversationNotificationStore does not know about it,
            //  as the store gets reset every time the app is stopped but notifications might persist longer
            cancel(
                notificationId = getNotificationIdForConversation(conversationId),
            )
            storeAccessor.removeMessageNotificationsForConversation(conversationId)
            if (!storeAccessor.notificationsExist()) {
                cancelAppLockedNewMessagesNotification()
            }
            cancelConversationSummaryNotificationIfNeeded()
            badgeUpdater.showIconBadge(storeAccessor.getTotalMessageNotificationCount())
        }
        // TODO(ANDR-4706): This should not be called from here, but from a monitor instead
        widgetUpdater.updateWidgets()
    }

    override fun cancelConversationNotificationsOnLockApp() {
        accessExclusively { storeAccessor ->
            val containedAnyNotificationToAnUnmutedReceiver = storeAccessor.getAllNotifications()
                .any { conversationNotification ->
                    val notificationTriggerPolicyOverride = conversationNotification.conversationHandle.notificationTriggerPolicyOverride
                        ?: return@any true
                    return@any when (conversationNotification) {
                        is ConversationMessageNotification -> !doNotDisturbUtil.isMessageMuted(
                            notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
                            rawMessageText = conversationNotification.rawMessage,
                        )
                        is ConversationReactionNotification -> !notificationTriggerPolicyOverride.muteAppliesAt(timeProvider.get())
                    }
                }
            val anyCancelled = cancelAllMessageCategoryNotifications()

            /*
             * We do not want to show the app-locked-new-message notification if all the current conversation
             * notifications originated from NOW muted receivers.
             *
             * In some cases we can't really tell if all the canceled system notifications are from blocked
             * receivers or not. That all the system notifications that were canceled here belonging to NOW
             * muted receivers is an extreme edge case. So we display the app-locked-new-message notification.
             *
             * Note: One could determine the actual receiver of the canceled system notifications by its tag.
             * But still than we would be missing the raw-message required for `DoNotDisturbUtil.isMessageMuted` method.
             */
            if (containedAnyNotificationToAnUnmutedReceiver || anyCancelled) {
                showDefaultAppLockedNewMessageNotification()
            }

            storeAccessor.removeAllNotifications()
            cancelConversationSummaryNotificationIfNeeded()
            badgeUpdater.showIconBadge(storeAccessor.getTotalMessageNotificationCount())
        }
    }

    /**
     * Cancel all conversation notifications of category [NotificationCompat.CATEGORY_MESSAGE]
     * @returns true if any have been canceled
     */
    private fun cancelAllMessageCategoryNotifications(): Boolean {
        var cancelledIDs = false
        try {
            notificationManagerCompat.activeNotifications.forEach { notification ->
                if (notification.notification?.category == NotificationCompat.CATEGORY_MESSAGE) {
                    cancel(notificationId = notification.id)
                    cancelledIDs = true
                }
            }
        } catch (e: Exception) {
            logger.error("Could not cancel notifications of CATEGORY_MESSAGE", e)
        }
        return cancelledIDs
    }

    private fun showDefaultAppLockedNewMessageNotification() {
        showAppLockedNewMessageNotification(
            channelId = NotificationChannels.NOTIFICATION_CHANNEL_CHAT_UPDATE,
        )
    }

    override fun cancelGroupCallNotification(groupId: LocalGroupId) {
        val conversationId = GroupConversationId(groupId.id.toLong())
        cancelGroupCallJoinPendingIntent(conversationId)
        cancel(
            notificationId = NotificationIDs.INCOMING_GROUP_CALL_NOTIFICATION_ID,
            tag = getNotificationTagForGroup(groupId),
        )
    }

    override fun cancelSafeBackupFailed() {
        cancel(NotificationIDs.SAFE_FAILED_NOTIFICATION_ID)
    }

    override fun cancelWorkSyncProgress() {
        cancel(NotificationIDs.WORK_SYNC_NOTIFICATION_ID)
    }

    override fun cancelRestartNotification() {
        cancel(NotificationIDs.APP_RESTART_NOTIFICATION_ID)
    }

    override fun cancelRestoreCompletionNotification() {
        cancel(NotificationIDs.RESTORE_COMPLETION_NOTIFICATION_ID)
    }

    override fun cancelServerMessageNotification() {
        cancel(NotificationIDs.SERVER_MESSAGE_NOTIFICATION_ID)
    }

    override fun cancelBackupCompletionNotification() {
        cancel(NotificationIDs.BACKUP_COMPLETION_NOTIFICATION_ID)
        cancel(NotificationIDs.CROSS_PLATFORM_BACKUP_CREATE_COMPLETION_NOTIFICATION_ID)
    }

    override fun cancelAppLockedNewMessagesNotification() {
        cancel(NotificationIDs.NEW_MESSAGE_PIN_LOCKED_NOTIFICATION_ID)
    }

    override fun onConversationNotificationDismissed() {
        cancelConversationSummaryNotificationIfNeeded()
    }

    private fun determineChannelId(conversationHandle: NotificationConversationHandle): String {
        val conversationIdObfuscated = conversationHandle.conversationId.obfuscated
        val doesPerConversationChannelExist = NotificationChannels.doesPerConversationChannelExist(
            context = appContext,
            uid = conversationIdObfuscated.value,
        )
        return if (doesPerConversationChannelExist) {
            conversationIdObfuscated.value
        } else {
            determineDefaultChannelId(conversationHandle)
        }
    }

    private fun determineDefaultChannelId(conversationHandle: NotificationConversationHandle): String {
        val isGroupConversation = conversationHandle.conversationId is GroupConversationId
        return if (isGroupConversation) {
            NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT
        } else {
            NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT
        }
    }

    private fun accessExclusively(block: (StoreAccessor) -> Unit) {
        // To avoid race-conditions, updates to conversation notifications should be done only holding a lock.
        // This also applies for operations that do not need to access the conversationNotificationStore.
        conversationNotificationStore.accessExclusively(block)
    }

    companion object {
        private val NOTIFY_AGAIN_TIMEOUT = 30.seconds
        private const val MAX_TICKER_TEXT_LENGTH = 256

        private fun getNotificationTagForGroup(groupId: LocalGroupId) =
            groupId.id.toString()
    }
}
