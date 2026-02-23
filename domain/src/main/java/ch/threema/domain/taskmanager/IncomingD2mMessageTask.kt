package ch.threema.domain.taskmanager

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.D2mPayloadType
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.toHex

private val logger = getThreemaLogger("IncomingD2mMessageTask")

class IncomingD2mMessageTask(
    private val message: InboundD2mMessage,
    private val incomingMessageProcessor: IncomingMessageProcessor,
) : ActiveTask<Unit> {
    override val type: String = "IncomingD2mMessageTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        when (message.payloadType) {
            D2mPayloadType.REFLECTED ->
                handleReflected(message as InboundD2mMessage.Reflected, handle)

            else -> logger.warn(
                "Unexpected d2m message of type 0x{} received",
                message.payloadType.toHex(),
            )
        }
    }

    private suspend fun handleReflected(
        message: InboundD2mMessage.Reflected,
        handle: ActiveTaskCodec,
    ) {
        incomingMessageProcessor.processIncomingD2mMessage(message, handle)
    }
}
