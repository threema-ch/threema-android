package ch.threema.domain.protocol.connection.layer

import ch.threema.domain.protocol.connection.*
import ch.threema.domain.protocol.connection.PipeProcessor
import ch.threema.domain.protocol.connection.data.ByteContainer
import ch.threema.domain.protocol.connection.data.CspData
import ch.threema.domain.protocol.connection.data.InboundL1Message
import ch.threema.domain.protocol.connection.data.OutboundL2Message
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil

private val logger = ConnectionLoggingUtil.getConnectionLogger("CspFrameLayer")

internal class CspFrameLayer : Layer1Codec {
    override val encoder: PipeProcessor<OutboundL2Message, ByteArray, Unit> = MappingPipe {
        if (it is ByteContainer) {
            logger.info("Encoding ByteContainer of type={}, {} bytes", it.type, it.bytes.size)
            it.bytes
        } else {
            throw ServerConnectionException("OutboundL2Message has invalid type `${it.type}`")
        }
    }

    override val decoder: PipeProcessor<ByteArray, InboundL1Message, ServerSocketCloseReason> =
        MappingPipe {
            logger.trace("Handle inbound message with {} bytes", it.size)
            CspData(it)
        }
}
