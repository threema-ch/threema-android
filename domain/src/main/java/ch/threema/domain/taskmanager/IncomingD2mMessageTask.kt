package ch.threema.domain.taskmanager

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.D2mPayloadType
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import kotlinx.coroutines.flow.MutableStateFlow

private val logger = getThreemaLogger("IncomingD2mMessageTask")

class IncomingD2mMessageTask(
    private val message: InboundD2mMessage,
    private val incomingMessageProcessor: IncomingMessageProcessor,
    private val reflectionQueueDryState: MutableStateFlow<ReflectionQueueDryState>,
    private val leaderState: MutableStateFlow<LeaderState>,
) : ActiveTask<Unit> {
    override val type: String = "IncomingD2mMessageTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        when (message.payloadType) {
            D2mPayloadType.REFLECTED ->
                handleReflected(message as InboundD2mMessage.Reflected, handle)

            D2mPayloadType.REFLECTION_QUEUE_DRY -> handleReflectionQueueDry()
            D2mPayloadType.ROLE_PROMOTED_TO_LEADER -> handlePromotedToLeader()

            else -> logger.warn(
                "Unexpected d2m message of type 0x{} received",
                message.payloadType.toHexString(),
            )
        }
    }

    private suspend fun handleReflected(
        message: InboundD2mMessage.Reflected,
        handle: ActiveTaskCodec,
    ) {
        incomingMessageProcessor.processIncomingD2mMessage(message, handle)
    }

    private fun handleReflectionQueueDry() {
        logger.info("Reflection queue was flushed; setting the reflection queue dry state to DRY")
        reflectionQueueDryState.value = ReflectionQueueDryState.DRY
    }

    private fun handlePromotedToLeader() {
        logger.info("Device was promoted to leader; setting the leader state to LEADER")
        leaderState.value = LeaderState.LEADER
    }
}
