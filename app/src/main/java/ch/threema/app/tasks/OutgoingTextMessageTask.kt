package ch.threema.app.tasks

import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType
import ch.threema.app.messagereceiver.MessageReceiver.Type_CONTACT
import ch.threema.app.messagereceiver.MessageReceiver.Type_GROUP
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

class OutgoingTextMessageTask(
    private val messageModelId: Int,
    @MessageReceiverType
    private val receiverType: Int,
    private val recipientIdentities: Set<IdentityString>,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingTextMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        when (receiverType) {
            Type_CONTACT -> sendContactMessage(handle)
            Type_GROUP -> sendGroupMessage(handle)
            else -> throw IllegalStateException("Invalid message receiver type $receiverType")
        }
    }

    override fun onSendingStepsFailed(e: Exception) {
        getMessageModel(receiverType, messageModelId)?.saveWithStateFailed()
    }

    private suspend fun sendContactMessage(handle: ActiveTaskCodec) {
        val messageModel = getContactMessageModel(messageModelId) ?: return

        // Create the message
        val message = TextMessage()
        message.text = messageModel.bodyAndQuotedMessageId

        sendContactMessage(
            message,
            messageModel,
            messageModel.identity!!,
            ensureMessageId(messageModel),
            messageModel.createdAt!!,
            handle,
        )
    }

    private suspend fun sendGroupMessage(handle: ActiveTaskCodec) {
        val messageModel = getGroupMessageModel(messageModelId) ?: return

        val group = groupService.getById(messageModel.groupId)
            ?: throw IllegalStateException("Could not get group for message model ${messageModel.apiMessageId}")

        val textIncludingQuote = messageModel.bodyAndQuotedMessageId

        sendGroupMessage(
            group = group,
            recipients = recipientIdentities,
            messageModel = messageModel,
            createdAt = messageModel.createdAt!!,
            messageId = ensureMessageId(messageModel),
            createAbstractMessage = {
                GroupTextMessage().apply {
                    text = textIncludingQuote
                }
            },
            handle = handle,
        )
    }

    override fun serialize(): SerializableTaskData =
        OutgoingTextMessageData(
            messageModelId,
            receiverType,
            recipientIdentities,
        )

    @Serializable
    class OutgoingTextMessageData(
        private val messageModelId: Int,
        @MessageReceiverType
        private val receiverType: Int,
        private val recipientIdentities: Set<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingTextMessageTask(
                messageModelId = messageModelId,
                receiverType = receiverType,
                recipientIdentities = recipientIdentities,
            )
    }
}
