package ch.threema.app.tasks

import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.protocol.csp.messages.EditMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingContactEditMessageTask(
    private val toIdentity: IdentityString,
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val editedText: String,
    private val editedAt: Date,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingContactEditMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val messageModel = getContactMessageModel(messageModelId)
            ?: throw ThreemaException("No contact message model found for messageModelId=$messageModelId")

        val editMessage = EditMessage(
            EditMessageData(
                messageId = messageModel.messageId!!.messageIdLong,
                text = editedText,
            ),
        )

        sendContactMessage(
            message = editMessage,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = messageId,
            createdAt = editedAt,
            handle = handle,
        )
    }

    override fun serialize(): SerializableTaskData = OutgoingContactEditMessageData(
        toIdentity = toIdentity,
        messageModelId = messageModelId,
        messageId = messageId.messageId,
        editedText = editedText,
        editedAt = editedAt.time,
    )

    @Serializable
    class OutgoingContactEditMessageData(
        private val toIdentity: IdentityString,
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val editedText: String,
        private val editedAt: Long,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingContactEditMessageTask(
                toIdentity = toIdentity,
                messageModelId = messageModelId,
                messageId = MessageId(messageId),
                editedText = editedText,
                editedAt = Date(editedAt),
            )
    }
}
