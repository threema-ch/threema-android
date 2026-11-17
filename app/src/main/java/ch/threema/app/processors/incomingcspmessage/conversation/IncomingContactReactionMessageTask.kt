package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.notifications.ConversationNotificationConverter
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.runCommonReactionMessageReceiveEmojiSequenceConversion
import ch.threema.app.tasks.runCommonReactionMessageReceiveSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.ReactionMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.MessageModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("IncomingContactReactionMessageTask")

class IncomingContactReactionMessageTask(
    message: ReactionMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<ReactionMessage>(message, triggerSource, serviceManager), KoinComponent {
    private val messageService by lazy { serviceManager.messageService }
    private val contactService by lazy { serviceManager.contactService }
    private val notificationService by lazy { serviceManager.notificationService }
    private val conversationNotificationConverter: ConversationNotificationConverter? by injectNullableNonBinding()
    private val preferenceService: PreferenceService by inject()

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) =
        processContactReactionMessage()

    override suspend fun executeMessageStepsFromSync() = processContactReactionMessage()

    private fun processContactReactionMessage(): ReceiveStepsResult {
        logger.debug("IncomingContactReactionMessageTask id: {}", message.data.messageId)

        val contactModelOld = contactService.getByIdentity(message.fromIdentity)
        if (contactModelOld == null) {
            logger.warn("Incoming Reaction Message: No contact found for ${message.fromIdentity}")
            return ReceiveStepsResult.DISCARD
        }

        val receiver: ContactMessageReceiver = contactService.createReceiver(contactModelOld)
        val contactModel = receiver.contactModel
        if (contactModel == null) {
            logger.warn("Incoming Reaction Message: No contact model found for ${message.fromIdentity}")
            return ReceiveStepsResult.DISCARD
        }
        val targetMessage = runCommonReactionMessageReceiveSteps(message, receiver, messageService)
            ?: return ReceiveStepsResult.DISCARD
        val emojiSequence = runCommonReactionMessageReceiveEmojiSequenceConversion(message.data.emojiSequenceBytes)
            ?: return ReceiveStepsResult.DISCARD

        val savedSuccessfully = messageService.saveEmojiReactionMessage(
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
        return ReceiveStepsResult.SUCCESS
    }

    private fun handleNotification(targetMessage: MessageModel) {
        if (!targetMessage.isOutbox) {
            return
        }
        val messageUid = targetMessage.uid
            ?: return
        val shouldShowEmojiReactionNotificationsInContactConversations = preferenceService
            .shouldShowEmojiReactionNotificationsInContactConversations()
        if (!shouldShowEmojiReactionNotificationsInContactConversations) {
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
