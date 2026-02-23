package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.protocol.csp.messages.EditMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingContactEditMessageTask(
    private val toIdentity: Identity,
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val editedText: String,
    private val editedAt: Date,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
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
            editMessage,
            null,
            toIdentity,
            messageId,
            editedAt,
            handle,
        )
    }

    override fun serialize(): SerializableTaskData = OutgoingContactEditMessageData(
        toIdentity,
        messageModelId,
        messageId.messageId,
        editedText,
        editedAt.time,
    )

    @Serializable
    class OutgoingContactEditMessageData(
        private val toIdentity: Identity,
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val editedText: String,
        private val editedAt: Long,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingContactEditMessageTask(
                toIdentity,
                messageModelId,
                MessageId(messageId),
                editedText,
                Date(editedAt),
                serviceManager,
            )
    }
}
