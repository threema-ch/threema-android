/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.taskmanager

import ch.threema.base.ThreemaException
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.D2mPayloadType
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.taskmanager.MessageFilterInstruction.ACCEPT
import ch.threema.domain.taskmanager.MessageFilterInstruction.BYPASS_OR_BACKLOG
import ch.threema.domain.types.Identity
import ch.threema.protobuf.d2d.MdD2D.TransactionScope.Scope

/**
 * A passive task codec is used to retrieve messages from the server. To send messages we need an
 * [ActiveTaskCodec].
 */
sealed interface PassiveTaskCodec {
    /**
     * Wait for a message by the server. All messages that are received by the server are checked
     * with the given [preProcess] argument. The message where the preprocessor returns
     * [MessageFilterInstruction.ACCEPT] will be returned for potential further processing. For
     * messages that are not needed by the current task, the preprocessor should return
     * [MessageFilterInstruction.BYPASS_OR_BACKLOG]. Messages that should be rejected at this point,
     * should result in [MessageFilterInstruction.REJECT].
     */
    suspend fun read(preProcess: (InboundMessage) -> MessageFilterInstruction): InboundMessage
}

/**
 * An active task codec can be used to retrieve, send, and reflect messages from and to the server.
 */
sealed interface ActiveTaskCodec : PassiveTaskCodec {
    /**
     * Write a message to the server. If the connection is lost, this suspends. Note that there is
     * no guarantee that a message has been sent to the server if this method returns successfully.
     */
    suspend fun write(message: OutboundMessage)

    /**
     * Reflect the given message. Returns the timestamp of the reflect ack.
     * TODO(ANDR-2983): Adapt storing of d2d nonces to protocol
     */
    suspend fun reflectAndAwaitAck(
        encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult,
        storeD2dNonce: Boolean,
        nonceFactory: NonceFactory,
    ): ULong

    /**
     * Reflect the given message. Returns the used reflect id.
     */
    suspend fun reflect(encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult): UInt
}

interface TaskCodec : ActiveTaskCodec

private val logger = getThreemaLogger("TaskCodec")

suspend fun PassiveTaskCodec.awaitOutgoingMessageAck(messageId: MessageId, identity: Identity) {
    read { inboundMessage ->
        // If the inbound message is not a csp message, we bypass it
        if (inboundMessage !is CspMessage) {
            return@read BYPASS_OR_BACKLOG
        }

        return@read when (inboundMessage.payloadType.toInt()) {
            ProtocolDefines.PLTYPE_OUTGOING_MESSAGE_ACK -> {
                val ack = inboundMessage.toOutgoingMessageAck()

                if (ack.messageId == messageId && ack.recipient == identity) {
                    ACCEPT
                } else {
                    BYPASS_OR_BACKLOG
                }
            }

            else -> {
                BYPASS_OR_BACKLOG
            }
        }
    }
}

suspend fun PassiveTaskCodec.awaitReflectAck(reflectId: UInt): ULong {
    val acceptedMessage = read { inboundMessage ->
        // If the inbound message is not a d2m message, we backlog it
        if (inboundMessage !is InboundD2mMessage) {
            return@read BYPASS_OR_BACKLOG
        }

        return@read when (inboundMessage.payloadType) {
            D2mPayloadType.REFLECT_ACK -> {
                val ack = inboundMessage as InboundD2mMessage.ReflectAck
                if (ack.reflectId == reflectId) {
                    ACCEPT
                } else {
                    BYPASS_OR_BACKLOG
                }
            }

            else -> {
                BYPASS_OR_BACKLOG
            }
        }
    }
    return (acceptedMessage as InboundD2mMessage.ReflectAck).timestamp
}

private val transactionLogger = getThreemaLogger("ActiveTaskCodec")

/**
 * Use the maximum time to live for this transaction. The maximum time is used, when the value 0 is
 * used as ttl.
 */
const val TRANSACTION_TTL_MAX: UInt = 0u

fun ActiveTaskCodec.createTransaction(
    keys: MultiDeviceKeys,
    scope: Scope,
    ttl: UInt,
    precondition: (suspend () -> Boolean)? = null,
): TransactionScope {
    logger.trace("Create transaction (scope={}, ttl={})", scope, ttl)
    return TransactionScope(this, keys, scope, ttl, precondition)
}

class TransactionScope(
    private val codec: ActiveTaskCodec,
    private val keys: MultiDeviceKeys,
    private val scope: Scope,
    private val ttl: UInt,
    private val precondition: (suspend () -> Boolean)?,
) {
    class TransactionException(msg: String) : ThreemaException(msg)

    /**
     * Execute the provided block within a transaction.
     *
     * If another transaction is currently running, this method will suspend, until it was possible to
     * create a transaction and run the provided block.
     *
     * If a precondition is provided but not met a [TransactionException] will be thrown. If the [block]
     * throws an exception when executed, this exception will also be thrown by this method.
     *
     * @throws [TransactionException] if something related to the transaction failed (e.g. precondition was not met)
     */
    suspend fun <R> execute(block: suspend () -> R): R {
        logger.trace("Execute transaction (hasPrecondition={})", precondition != null)
        assertPrecondition()
        codec.startTransaction(keys, scope, ttl)
        return try {
            assertPrecondition()
            block.invoke()
        } finally {
            codec.commitTransaction()
        }
    }

    private suspend fun assertPrecondition() {
        if (precondition?.invoke() == false) {
            throw TransactionException("Precondition failed")
        }
    }

    private suspend fun ActiveTaskCodec.startTransaction(
        keys: MultiDeviceKeys,
        scope: Scope,
        ttl: UInt,
    ) {
        transactionLogger.trace("Start transaction (scope={}, ttl={})", scope, ttl)
        val encryptedScope = keys.encryptTransactionScope(scope)

        do {
            write(OutboundD2mMessage.BeginTransaction(encryptedScope, ttl))

            val message = read {
                when (it) {
                    is InboundD2mMessage.BeginTransactionAck -> ACCEPT
                    is InboundD2mMessage.TransactionRejected -> ACCEPT
                    else -> BYPASS_OR_BACKLOG
                }
            }

            if (message is InboundD2mMessage.TransactionRejected) {
                if (transactionLogger.isTraceEnabled) {
                    val decryptedScope = keys.decryptTransactionScope(message.encryptedScope)
                    transactionLogger.trace(
                        "Transaction rejected (deviceId={}, scope={}). Wait for ongoing transaction to end",
                        message.deviceId,
                        decryptedScope,
                    )
                }
                read {
                    when (it) {
                        is InboundD2mMessage.TransactionEnded -> ACCEPT
                        else -> BYPASS_OR_BACKLOG
                    }
                }
            }
        } while (message !is InboundD2mMessage.BeginTransactionAck)
        transactionLogger.trace("Transaction started")
    }

    private suspend fun ActiveTaskCodec.commitTransaction() {
        transactionLogger.trace("Commit transaction")
        write(OutboundD2mMessage.CommitTransaction())

        read {
            if (it is InboundD2mMessage.CommitTransactionAck) {
                ACCEPT
            } else {
                BYPASS_OR_BACKLOG
            }
        }
        transactionLogger.trace("Transaction committed")
    }
}
