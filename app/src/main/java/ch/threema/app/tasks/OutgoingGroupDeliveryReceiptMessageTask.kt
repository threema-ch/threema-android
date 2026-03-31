package ch.threema.app.tasks

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("OutgoingGroupDeliveryReceiptMessageTask")

class OutgoingGroupDeliveryReceiptMessageTask(
    private val messageModelId: Int,
    private val receiptType: Int,
    private val recipientIdentities: Set<IdentityString>,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingGroupDeliveryReceiptMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val messageModel = getGroupMessageModel(messageModelId)
        if (messageModel == null) {
            logger.warn("Message model ($messageModelId) is null while trying to send group delivery receipt")
            return
        }
        val group = groupService.getById(messageModel.groupId)
        if (group == null) {
            logger.warn("Group (${messageModel.groupId}) is null while trying to send group delivery receipt")
            return
        }

        val messageId = MessageId.random()

        sendGroupMessage(
            group = group,
            recipients = recipientIdentities,
            messageModel = null,
            createdAt = Date(),
            messageId = messageId,
            createAbstractMessage = {
                GroupDeliveryReceiptMessage().also {
                    it.receiptType = receiptType
                    it.receiptMessageIds = arrayOf(messageModel.messageId)
                }
            },
            handle = handle,
        )
    }

    override fun serialize(): SerializableTaskData =
        OutgoingGroupDeliveryReceiptMessageData(messageModelId, receiptType, recipientIdentities)

    @Serializable
    class OutgoingGroupDeliveryReceiptMessageData(
        private val messageModelId: Int,
        private val receiptType: Int,
        private val recipientIdentities: Set<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupDeliveryReceiptMessageTask(
                messageModelId = messageModelId,
                receiptType = receiptType,
                recipientIdentities = recipientIdentities,
            )
    }
}
