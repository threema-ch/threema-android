package ch.threema.domain.helpers

import ch.threema.base.crypto.NonceFactory
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.taskmanager.MessageFilterInstruction
import ch.threema.domain.taskmanager.TaskCodec

/**
 * This task codec can be used in tests as placeholder. Note that this task codec throws
 * [IllegalStateException] when one of its methods is called.
 */
class UnusedTaskCodec : TaskCodec {
    override suspend fun read(preProcess: (InboundMessage) -> MessageFilterInstruction): InboundMessage {
        throw IllegalStateException("This task codec should not be used.")
    }

    override suspend fun write(message: OutboundMessage) {
        throw IllegalStateException("This task codec should not be used.")
    }

    override suspend fun reflectAndAwaitAck(
        encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult,
        storeD2dNonce: Boolean,
        nonceFactory: NonceFactory,
    ): ULong {
        throw IllegalStateException("This task codec should not be used.")
    }

    override suspend fun reflect(encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult): UInt {
        throw IllegalStateException("This task codec should not be used.")
    }
}
