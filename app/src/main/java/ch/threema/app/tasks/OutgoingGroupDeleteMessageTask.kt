package ch.threema.app.tasks

import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.DeleteMessageData
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingGroupDeleteMessageTask(
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val deletedAt: Date,
    private val recipientIdentities: Set<IdentityString>,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingGroupDeleteMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = getGroupMessageModel(messageModelId)
            ?: throw ThreemaException("No group message model found for messageModelId=$messageModelId")

        val editedMessageIdLong = message.messageId!!.messageIdLong

        val group = groupService.getById(message.groupId)
            ?: throw ThreemaException("No group model found for groupId=${message.groupId}")

        sendGroupMessage(
            group,
            recipientIdentities,
            null,
            deletedAt,
            messageId,
            createAbstractMessage = { createDeleteMessage(editedMessageIdLong) },
            handle,
        )
    }

    private fun createDeleteMessage(messageId: Long): GroupDeleteMessage {
        val deleteMessage = GroupDeleteMessage(
            DeleteMessageData(messageId = messageId),
        )
        return deleteMessage
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupDeleteMessageData(
        messageModelId = messageModelId,
        messageId = messageId.messageId,
        deletedAt = deletedAt.time,
        recipientIdentities = recipientIdentities,
    )

    @Serializable
    class OutgoingGroupDeleteMessageData(
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val deletedAt: Long,
        private val recipientIdentities: Set<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupDeleteMessageTask(
                messageModelId = messageModelId,
                messageId = MessageId(messageId),
                deletedAt = Date(deletedAt),
                recipientIdentities = recipientIdentities,
            )
    }
}
