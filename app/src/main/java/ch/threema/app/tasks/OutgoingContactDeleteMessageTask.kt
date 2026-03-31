package ch.threema.app.tasks

import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.DeleteMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingContactDeleteMessageTask(
    private val toIdentity: IdentityString,
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val deletedAt: Date,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingContactDeleteMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = getContactMessageModel(messageModelId)
            ?: throw ThreemaException("No contact message model found for messageModelId=$messageModelId")

        val deleteMessage = DeleteMessage(
            DeleteMessageData(messageId = message.messageId!!.messageIdLong),
        )

        sendContactMessage(deleteMessage, null, toIdentity, messageId, deletedAt, handle)
    }

    override fun serialize(): SerializableTaskData =
        OutgoingContactDeleteMessageData(
            toIdentity,
            messageModelId,
            messageId.messageId,
            deletedAt.time,
        )

    @Serializable
    class OutgoingContactDeleteMessageData(
        private val toIdentity: IdentityString,
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val deletedAt: Long,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingContactDeleteMessageTask(
                toIdentity,
                messageModelId,
                MessageId(messageId),
                Date(deletedAt),
            )
    }
}
