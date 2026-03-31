package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.getEncryptedIncomingGroupMessageUpdateReadEnvelope
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("OutboundIncomingGroupMessageUpdateReadTask")

class OutboundIncomingGroupMessageUpdateReadTask(
    private val messageIds: Set<MessageId>,
    private val timestamp: Long,
    private val groupId: GroupId,
    private val creatorIdentity: IdentityString,
) : OutboundD2mMessageTask<Unit>, PersistableTask, KoinComponent {
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val multiDeviceProperties by lazy { multiDeviceManager.propertiesProvider.get() }
    private val deviceId by lazy { multiDeviceProperties.mediatorDeviceId }
    private val multiDeviceKeys by lazy { multiDeviceProperties.keys }

    private val nonceFactory: NonceFactory by inject()

    override val type: String = "OutboundIncomingGroupMessageUpdateReadTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Multi device is not active")
            return
        }

        val encryptedEnvelopeResult = getEncryptedIncomingGroupMessageUpdateReadEnvelope(
            messageIds = messageIds,
            timestamp = timestamp,
            creatorIdentity = creatorIdentity,
            groupId = groupId,
            mediatorDeviceId = deviceId,
            multiDeviceKeys = multiDeviceKeys,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override fun serialize() = OutboundIncomingGroupMessageUpdateReadData(
        messageIds = messageIds.map { it.messageId }.toSet(),
        timestamp = timestamp,
        groupId = groupId.groupId,
        creatorIdentity = creatorIdentity,
    )

    @Serializable
    class OutboundIncomingGroupMessageUpdateReadData(
        private val messageIds: Set<ByteArray>,
        private val timestamp: Long,
        private val groupId: ByteArray,
        private val creatorIdentity: IdentityString,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutboundIncomingGroupMessageUpdateReadTask(
                messageIds = messageIds.map { MessageId(it) }.toSet(),
                timestamp = timestamp,
                groupId = GroupId(groupId),
                creatorIdentity = creatorIdentity,
            )
    }
}
