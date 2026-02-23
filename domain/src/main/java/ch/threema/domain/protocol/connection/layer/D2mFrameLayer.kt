package ch.threema.domain.protocol.connection.layer

import ch.threema.domain.protocol.D2mProtocolDefines
import ch.threema.domain.protocol.connection.InvalidSizeException
import ch.threema.domain.protocol.connection.MappingPipe
import ch.threema.domain.protocol.connection.PipeProcessor
import ch.threema.domain.protocol.connection.ServerConnectionException
import ch.threema.domain.protocol.connection.data.D2mContainer
import ch.threema.domain.protocol.connection.data.InboundL1Message
import ch.threema.domain.protocol.connection.data.OutboundL2Message
import ch.threema.domain.protocol.connection.data.toHex
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil

private val logger = ConnectionLoggingUtil.getConnectionLogger("D2mFrameLayer")

internal class D2mFrameLayer : Layer1Codec {
    override val encoder: PipeProcessor<OutboundL2Message, ByteArray, Unit> = MappingPipe {
        if (it is D2mContainer) {
            logger.info("Encoding D2mContainer with payloadType={}, {} bytes", it.payloadType.toHex(), it.bytes.size)
            it.bytes
        } else {
            throw ServerConnectionException("OutboundL2Message has invalid type `${it.type}`")
        }
    }

    override val decoder: PipeProcessor<ByteArray, InboundL1Message, ServerSocketCloseReason> =
        MappingPipe {
            logger.trace("Handle inbound message with {} bytes", it.size)

            if (it.size > D2mProtocolDefines.D2M_FRAME_MAX_BYTES_LENGTH) {
                throw InvalidSizeException("Inbound frame too large: ${it.size} bytes")
            }

            if (it.size < D2mProtocolDefines.D2M_FRAME_MIN_BYTES_LENGTH) {
                throw InvalidSizeException("Inbound frame too small: ${it.size} bytes")
            }

            D2mContainer(it[0].toUByte(), it.copyOfRange(4, it.size))
        }
}
