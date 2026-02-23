package ch.threema.domain.helpers

import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage

/**
 * This task codec is used only for tests. It acts as the server and creates server acknowledgements
 * for sent transaction begin and commit messages. Note that this also acts as [ServerAckTaskCodec].
 */
open class TransactionAckTaskCodec : ServerAckTaskCodec() {
    var transactionBeginCount = 0
    var transactionCommitCount = 0

    override suspend fun write(message: OutboundMessage) {
        if (message is OutboundD2mMessage) {
            when (message) {
                is OutboundD2mMessage.BeginTransaction -> {
                    transactionBeginCount++
                    inboundMessages.add(InboundD2mMessage.BeginTransactionAck())
                }

                is OutboundD2mMessage.CommitTransaction -> {
                    transactionCommitCount++
                    inboundMessages.add(InboundD2mMessage.CommitTransactionAck())
                }

                else -> Unit
            }
            outboundMessages.add(message)
        } else {
            super.write(message)
        }
    }
}
