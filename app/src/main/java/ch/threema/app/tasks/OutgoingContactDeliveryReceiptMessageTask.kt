package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingContactDeliveryReceiptMessageTask(
    private val receiptType: Int,
    private val messageIds: Array<MessageId>,
    private val date: Long,
    private val toIdentity: Identity,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingContactDeliveryReceiptMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val message = DeliveryReceiptMessage().also {
            it.receiptType = receiptType
            it.receiptMessageIds = messageIds
        }

        sendContactMessage(message, null, toIdentity, MessageId.random(), Date(date), handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingDeliveryReceiptMessageData(
        receiptType,
        messageIds.map { it.toString() },
        date,
        toIdentity,
    )

    @Serializable
    data class OutgoingDeliveryReceiptMessageData(
        private val receiptType: Int,
        private val messageIds: List<String>,
        private val date: Long,
        private val toIdentity: Identity,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingContactDeliveryReceiptMessageTask(
                receiptType,
                messageIds.map { MessageId.fromString(it) }.toTypedArray(),
                date,
                toIdentity,
                serviceManager,
            )
    }
}
