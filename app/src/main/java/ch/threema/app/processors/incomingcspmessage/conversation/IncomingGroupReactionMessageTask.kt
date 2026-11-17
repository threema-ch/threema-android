package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.managers.ServiceManager
import ch.threema.app.notifications.ConversationNotificationConverter
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.tasks.runCommonReactionMessageReceiveEmojiSequenceConversion
import ch.threema.app.tasks.runCommonReactionMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.MessageModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("IncomingGroupReactionMessageTask")

class IncomingGroupReactionMessageTask(
    message: GroupReactionMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupReactionMessage>(message, triggerSource, serviceManager), KoinComponent {
    private val messageService by lazy { serviceManager.messageService }
    private val groupService by lazy { serviceManager.groupService }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }
    private val notificationService by lazy { serviceManager.notificationService }
    private val conversationNotificationConverter: ConversationNotificationConverter? by injectNullableNonBinding()
    private val preferenceService: PreferenceService by inject()

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.debug("IncomingGroupReactionMessageTask from remote id: {}", message.data.messageId)

        val groupModel = runCommonGroupReceiveSteps(message, handle, serviceManager)
            ?: return ReceiveStepsResult.DISCARD

        return applyGroupReaction(groupModel)
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        logger.debug("IncomingGroupReactionMessageTask from sync id: {}", message.data.messageId)

        val groupModel = groupModelRepository.getByGroupIdentity(
            GroupIdentity(
                message.groupCreator,
                message.apiGroupId.toLong(),
            ),
        )

        if (groupModel == null) {
            logger.error("Received a reflected group reaction message in an unknown group")
            return ReceiveStepsResult.DISCARD
        }

        return applyGroupReaction(groupModel)
    }

    private fun applyGroupReaction(groupModel: GroupModel): ReceiveStepsResult {
        val receiver = groupService.createReceiver(groupModel) ?: run {
            logger.error("Could not create receiver for group")
            return ReceiveStepsResult.DISCARD
        }

        val targetMessage: MessageModel = runCommonReactionMessageReceiveSteps(
            reactionMessage = message,
            receiver = receiver,
            messageService = messageService,
        ) ?: return ReceiveStepsResult.DISCARD

        val emojiSequence: String = runCommonReactionMessageReceiveEmojiSequenceConversion(
            emojiSequenceBytes = message.data.emojiSequenceBytes,
        ) ?: return ReceiveStepsResult.DISCARD

        val savedSuccessfully: Boolean = messageService.saveEmojiReactionMessage(
            /* targetMessage = */
            targetMessage,
            /* senderIdentity = */
            message.fromIdentity,
            /* actionCase = */
            message.data.actionCase,
            /* emojiSequence = */
            emojiSequence,
            /* createdAt = */
            message.timestamp,
        )

        if (savedSuccessfully) {
            handleNotification(targetMessage)
        }

        return if (savedSuccessfully) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }

    private fun handleNotification(targetMessage: MessageModel) {
        if (!targetMessage.isOutbox) {
            return
        }
        val messageUid = targetMessage.uid
            ?: return
        val shouldShowEmojiReactionNotificationsInGroupConversations = preferenceService.shouldShowEmojiReactionNotificationsInGroupConversations()
        if (!shouldShowEmojiReactionNotificationsInGroupConversations) {
            return
        }
        val conversationReactionNotification = conversationNotificationConverter?.convertReactionNotification(targetMessage)
        if (conversationReactionNotification == null) {
            logger.error("Failed to create emoji reaction notification for message {}", targetMessage.messageId)
            return
        }
        if (conversationReactionNotification.anyEmojiReactionExists) {
            notificationService.showEmojiReactionNotification(conversationReactionNotification)
        } else {
            notificationService.cancelEmojiReactionNotification(
                conversationId = conversationReactionNotification.conversationHandle.conversationId,
                messageUid = messageUid,
            )
        }
    }
}
