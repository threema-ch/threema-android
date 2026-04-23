package ch.threema.app.processors.reflectedmessageupdate

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.common.GroupIdentity
import ch.threema.protobuf.d2d.ConversationId
import ch.threema.protobuf.d2d.OutgoingMessageUpdate
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageState
import java.util.Date

private val logger = getThreemaLogger("ReflectedOutgoingMessageUpdateTask")

class ReflectedOutgoingMessageUpdateTask(
    private val outgoingMessageUpdate: OutgoingMessageUpdate,
    private val timestamp: ULong,
    serviceManager: ServiceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    fun run() {
        outgoingMessageUpdate.updatesList.forEach { update ->
            when (update.updateCase) {
                OutgoingMessageUpdate.Update.UpdateCase.SENT -> applySentUpdate(update)
                else -> logger.error("Received an unknown outgoing message update '${update.updateCase}'")
            }
        }
    }

    private fun applySentUpdate(update: OutgoingMessageUpdate.Update) {
        val conversation = update.conversation
        val messageId = MessageId(update.messageId)
        when (conversation.idCase) {
            ConversationId.IdCase.CONTACT -> applyContactMessageSentUpdate(messageId, conversation.contact)

            ConversationId.IdCase.GROUP -> applyGroupMessageSentUpdate(messageId, conversation.group)

            ConversationId.IdCase.DISTRIBUTION_LIST -> updateDistributionListMessageState(
                messageId,
                conversation.distributionList,
            )

            ConversationId.IdCase.ID_NOT_SET -> logger.warn("Received outgoing message update where id is not set")

            null -> logger.warn("Received outgoing message update where id is null")
        }
    }

    private fun applyContactMessageSentUpdate(
        messageId: MessageId,
        recipientIdentity: IdentityString,
    ) {
        val messageModel = messageService.getContactMessageModel(messageId, recipientIdentity)

        if (messageModel == null) {
            logger.warn(
                "Message model for message {} to {} not found",
                messageId,
                recipientIdentity,
            )
            return
        }

        updateMessageModelSentState(messageModel)
    }

    private fun applyGroupMessageSentUpdate(
        messageId: MessageId,
        groupIdentity: GroupIdentity,
    ) {
        val messageModel = messageService.getGroupMessageModel(
            messageId,
            groupIdentity.creatorIdentity,
            GroupId(groupIdentity.groupId),
        )

        if (messageModel == null) {
            logger.warn("Group message model for message {} not found", messageId)
            return
        }

        updateMessageModelSentState(messageModel)
    }

    private fun updateDistributionListMessageState(
        messageId: MessageId,
        distributionList: Long,
    ) {
        // TODO(ANDR-2718)
    }

    private fun updateMessageModelSentState(messageModel: AbstractMessageModel) {
        messageService.updateOutgoingMessageState(
            messageModel,
            MessageState.SENT,
            Date(timestamp.toLong()),
        )
        ListenerManager.messageListeners.handle {
            it.onModified(listOf(messageModel))
        }
    }
}
