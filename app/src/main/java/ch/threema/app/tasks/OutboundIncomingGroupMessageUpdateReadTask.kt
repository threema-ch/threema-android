package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.getEncryptedIncomingGroupMessageUpdateReadEnvelope
import ch.threema.domain.types.Identity
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("OutboundIncomingGroupMessageUpdateReadTask")

class OutboundIncomingGroupMessageUpdateReadTask(
    private val messageIds: Set<MessageId>,
    private val timestamp: Long,
    private val groupId: GroupId,
    private val creatorIdentity: Identity,
    serviceManager: ServiceManager,
) : OutboundD2mMessageTask<Unit>, PersistableTask {
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val multiDeviceProperties by lazy { multiDeviceManager.propertiesProvider.get() }
    private val deviceId by lazy { multiDeviceProperties.mediatorDeviceId }
    private val multiDeviceKeys by lazy { multiDeviceProperties.keys }

    private val nonceFactory by lazy { serviceManager.nonceFactory }

    override val type: String = "OutboundIncomingGroupMessageUpdateReadTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Multi device is not active")
            return
        }

        val encryptedEnvelopeResult = getEncryptedIncomingGroupMessageUpdateReadEnvelope(
            messageIds,
            timestamp,
            creatorIdentity,
            groupId,
            deviceId,
            multiDeviceKeys,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override fun serialize() = OutboundIncomingGroupMessageUpdateReadData(
        messageIds.map { it.messageId }.toSet(),
        timestamp,
        groupId.groupId,
        creatorIdentity,
    )

    @Serializable
    class OutboundIncomingGroupMessageUpdateReadData(
        private val messageIds: Set<ByteArray>,
        private val timestamp: Long,
        private val groupId: ByteArray,
        private val creatorIdentity: Identity,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutboundIncomingGroupMessageUpdateReadTask(
                messageIds.map { MessageId(it) }.toSet(),
                timestamp,
                GroupId(groupId),
                creatorIdentity,
                serviceManager,
            )
    }
}
