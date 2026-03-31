package ch.threema.app.tasks

import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.EditMessageData
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingGroupEditMessageTask(
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val editedText: String,
    private val editedAt: Date,
    private val recipientIdentities: Set<IdentityString>,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingGroupEditMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = getGroupMessageModel(messageModelId)
            ?: throw ThreemaException("No group message model found for messageModelId=$messageModelId")

        val group = groupService.getById(message.groupId)
            ?: throw ThreemaException("No group model found for groupId=${message.groupId}")

        val editedMessageIdLong = message.messageId!!.messageIdLong

        sendGroupMessage(
            group,
            groupService.getGroupMemberIdentities(group).toSet(),
            null,
            editedAt,
            messageId,
            createAbstractMessage = { createEditMessage(editedMessageIdLong) },
            handle,
        )
    }

    private fun createEditMessage(messageId: Long) = GroupEditMessage(
        EditMessageData(
            messageId = messageId,
            text = editedText,
        ),
    )

    override fun serialize(): SerializableTaskData = OutgoingGroupEditMessageData(
        messageModelId,
        messageId.messageId,
        editedText,
        editedAt.time,
        recipientIdentities,
    )

    @Serializable
    class OutgoingGroupEditMessageData(
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val editedText: String,
        private val editedAt: Long,
        private val recipientIdentities: Set<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupEditMessageTask(
                messageModelId = messageModelId,
                messageId = MessageId(messageId),
                editedText = editedText,
                editedAt = Date(editedAt),
                recipientIdentities = recipientIdentities,
            )
    }
}
