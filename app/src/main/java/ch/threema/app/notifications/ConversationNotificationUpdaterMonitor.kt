package ch.threema.app.notifications

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.monitors.Monitor
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.GroupService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.notification.NotificationService
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.localGroupId
import ch.threema.domain.protocol.csp.messages.file.FileData.RENDERING_MEDIA
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType
import ch.threema.storage.models.getConversationId
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class ConversationNotificationUpdaterMonitor(
    private val globalEventFlows: GlobalEventFlows,
    private val conversationNotificationConverter: ConversationNotificationConverter,
    private val identityProvider: IdentityProvider,
    private val lockAppService: LockAppService,
    private val preferenceService: PreferenceService,
) : Monitor("ConversationNotificationUpdaterMonitor"), KoinComponent {
    private val notificationService: NotificationService? by injectNullableNonBinding()
    private val conversationService: ConversationService? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()

    override suspend fun run(): Unit = coroutineScope {
        launch {
            globalEventFlows.conversations.collect { event ->
                when (event) {
                    is ConversationEvent.ConversationArchived -> cancelNotification(event.conversation.id)
                    is ConversationEvent.ConversationDeleted -> cancelNotification(event.conversation.id)
                    is ConversationEvent.NewConversation,
                    is ConversationEvent.ConversationUpdated,
                    is ConversationEvent.ConversationRemoved,
                    ConversationEvent.AllConversationsUpdated,
                    -> Unit
                }
            }
        }
        launch {
            globalEventFlows.messages.collect { event ->
                when (event) {
                    is MessageEvent.NewMessage -> onNewMessage(event.message)
                    is MessageEvent.MessageDeletedForAll -> {
                        showConversationNotification(event.message, updateExisting = true)
                        cancelEmojiReactionNotification(event.message)
                    }
                    is MessageEvent.MessageEdited -> {
                        showConversationNotification(event.message, updateExisting = true)
                        if (shouldShowEmojiReactionNotification(event.message)) {
                            showEmojiReactionNotification(event.message)
                        }
                    }
                    is MessageEvent.MessagesUpdated -> onModifiedMessages(event.messages)
                    is MessageEvent.MessageRemovedLocally -> cancelEmojiReactionNotification(event.message)
                }
            }
        }
        launch {
            globalEventFlows.groups.collect { event ->
                when (event) {
                    is GroupEvent.MemberKicked -> if (event.identity == identityProvider.getIdentity()) {
                        removeGroupCallNotifications(event.groupIdentity)
                    }
                    is GroupEvent.UserLeftGroup -> removeGroupCallNotifications(event.groupIdentity)
                    is GroupEvent.GroupRemoved,
                    is GroupEvent.MemberLeft,
                    is GroupEvent.GroupProfilePictureUpdated,
                    is GroupEvent.GroupRenamed,
                    is GroupEvent.GroupStateChanged,
                    is GroupEvent.GroupUpdated,
                    is GroupEvent.NewGroup,
                    is GroupEvent.NewMember,
                    -> Unit
                }
            }
        }
        launch {
            watchAppLock()
        }
    }

    private fun cancelNotification(conversationId: ConversationId) {
        notificationService?.cancelAllConversationNotifications(conversationId)
    }

    private fun onNewMessage(message: MessageModel) {
        if (message.isStatusMessage) {
            return
        }

        val conversation = conversationService?.get(message)
        if (conversation != null) {
            showConversationNotification(message, updateExisting = false)
        }
    }

    private fun onModifiedMessages(messages: List<MessageModel>) {
        val shouldShowEmojiReactionNotificationsInContactConversations = preferenceService
            .shouldShowEmojiReactionNotificationsInContactConversations()
        val shouldShowEmojiReactionNotificationsInGroupConversations = preferenceService
            .shouldShowEmojiReactionNotificationsInGroupConversations()
        messages.forEach { message ->
            if (message.isImageOrVideoMessage()) {
                showConversationNotification(message, updateExisting = true)
            }
            if (message.isOutbox && !message.isStatusMessage) {
                when (message) {
                    is ContactMessageModel -> {
                        if (shouldShowEmojiReactionNotificationsInContactConversations) {
                            showEmojiReactionNotification(message)
                        }
                    }
                    is GroupMessageModel -> {
                        if (shouldShowEmojiReactionNotificationsInGroupConversations) {
                            showEmojiReactionNotification(message)
                        }
                    }
                    is DistributionListMessageModel -> Unit
                }
            }
        }
    }

    private fun MessageModel.isImageOrVideoMessage(): Boolean =
        type == MessageType.FILE &&
            fileData.renderingType == RENDERING_MEDIA &&
            (messageContentsType == MessageContentsType.IMAGE || messageContentsType == MessageContentsType.VIDEO)

    private fun showConversationNotification(message: MessageModel, updateExisting: Boolean) {
        if (message.isOutbox || message.isStatusMessage || message.isRead) {
            return
        }
        notificationService?.showConversationNotification(
            conversationNotificationConverter.convertMessageNotification(message) ?: return,
            updateExisting,
        )
    }

    private fun showEmojiReactionNotification(message: MessageModel) {
        val conversationReactionNotification = conversationNotificationConverter
            .convertReactionNotification(
                targetMessage = message,
            )
            ?: return
        if (conversationReactionNotification.anyEmojiReactionExists) {
            notificationService?.showEmojiReactionNotification(conversationReactionNotification)
        }
    }

    private fun shouldShowEmojiReactionNotification(message: MessageModel): Boolean {
        if (!message.isOutbox || message.isStatusMessage) {
            return false
        }
        return when (message) {
            is ContactMessageModel -> preferenceService.shouldShowEmojiReactionNotificationsInContactConversations()
            is GroupMessageModel -> preferenceService.shouldShowEmojiReactionNotificationsInGroupConversations()
            is DistributionListMessageModel -> false
        }
    }

    private fun cancelEmojiReactionNotification(message: MessageModel) {
        if (!message.isOutbox || message.isStatusMessage) {
            return
        }
        val messageUid = message.uid
            ?: return
        notificationService?.cancelEmojiReactionNotification(
            conversationId = message.getConversationId(),
            messageUid = messageUid,
        )
    }

    private fun removeGroupCallNotifications(groupIdentity: GroupIdentity) {
        val group = groupService?.getByGroupIdentity(groupIdentity) ?: return
        notificationService?.cancelGroupCallNotification(group.localGroupId)
    }

    private suspend fun watchAppLock() {
        lockAppService.watchLockState()
            .collect { state ->
                if (state == LockAppService.LockState.LOCKED) {
                    notificationService?.cancelConversationNotificationsOnLockApp()
                }
            }
    }
}
