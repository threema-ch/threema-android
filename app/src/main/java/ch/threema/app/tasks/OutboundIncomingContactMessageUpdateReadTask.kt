package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.getEncryptedIncomingContactMessageUpdateReadEnvelope
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("OutboundIncomingContactMessageUpdateReadTask")

class OutboundIncomingContactMessageUpdateReadTask(
    private val messageIds: Set<MessageId>,
    private val timestamp: Long,
    private val recipientIdentity: IdentityString,
) : OutboundD2mMessageTask<Unit>, PersistableTask, KoinComponent {
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val multiDeviceProperties by lazy { multiDeviceManager.propertiesProvider.get() }
    private val deviceId by lazy { multiDeviceProperties.mediatorDeviceId }
    private val multiDeviceKeys by lazy { multiDeviceProperties.keys }

    private val nonceFactory: NonceFactory by inject()

    override val type: String = "OutboundIncomingContactMessageUpdateReadTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Multi device is not active")
            return
        }

        val encryptedEnvelopeResult = getEncryptedIncomingContactMessageUpdateReadEnvelope(
            messageIds,
            timestamp,
            recipientIdentity,
            deviceId,
            multiDeviceKeys,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override fun serialize(): SerializableTaskData = OutboundIncomingContactMessageUpdateReadData(
        messageIds.map { it.messageId }.toSet(),
        timestamp,
        recipientIdentity,
    )

    @Serializable
    data class OutboundIncomingContactMessageUpdateReadData(
        private val messageIds: Set<ByteArray>,
        private val timestamp: Long,
        private val recipientIdentity: IdentityString,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutboundIncomingContactMessageUpdateReadTask(
                messageIds.map { MessageId(it) }.toSet(),
                timestamp,
                recipientIdentity,
            )
    }
}
