package ch.threema.domain.taskmanager

import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import java.util.function.Supplier

class BypassTaskCodec(taskCodecSupplier: Supplier<TaskCodec>) : TaskCodec {
    private val taskCodec by lazy { taskCodecSupplier.get() }

    override suspend fun write(message: OutboundMessage) {
        require(message is OutboundD2mMessage.ReflectedAck) {
            "Unexpected outgoing message with payload ${message.payloadType} in bypassed task"
        }
        taskCodec.write(message)
    }

    override suspend fun reflectAndAwaitAck(
        encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult,
        storeD2dNonce: Boolean,
        nonceFactory: NonceFactory,
    ): ULong {
        throw IllegalStateException("Unexpected reflection with awaiting ack inside bypassed task")
    }

    override suspend fun reflect(encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult): UInt {
        throw IllegalStateException("Unexpected reflection inside bypassed task")
    }

    override suspend fun read(preProcess: (InboundMessage) -> MessageFilterInstruction): InboundMessage {
        throw IllegalStateException("Unexpected read operation inside bypassed task")
    }
}
