package ch.threema.app.tasks

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingContactDeliveryReceiptMessageTask(
    private val receiptType: Int,
    private val messageIds: Array<MessageId>,
    private val date: Long,
    private val toIdentity: IdentityString,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingContactDeliveryReceiptMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = DeliveryReceiptMessage().also {
            it.receiptType = receiptType
            it.receiptMessageIds = messageIds
        }

        sendContactMessage(message, null, toIdentity, MessageId.random(), Date(date), handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingDeliveryReceiptMessageData(
        receiptType = receiptType,
        messageIds = messageIds.map { it.toString() },
        date = date,
        toIdentity = toIdentity,
    )

    @Serializable
    data class OutgoingDeliveryReceiptMessageData(
        private val receiptType: Int,
        private val messageIds: List<String>,
        private val date: Long,
        private val toIdentity: IdentityString,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingContactDeliveryReceiptMessageTask(
                receiptType = receiptType,
                messageIds = messageIds.map { MessageId.fromString(it) }.toTypedArray(),
                date = date,
                toIdentity = toIdentity,
            )
    }
}
