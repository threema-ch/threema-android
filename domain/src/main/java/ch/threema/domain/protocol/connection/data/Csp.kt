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

package ch.threema.domain.protocol.connection.data

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.connection.InvalidSizeException
import ch.threema.domain.protocol.connection.PayloadProcessingException
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * A simple container for byte arrays that are exchanged with the chat server.
 * There is no special logic such as prepending the length. If the length is required (as in a CspFrame)
 * a [CspData] is typically acquired by utilising [CspFrame.toCspData].
 */
internal class CspData(bytes: ByteArray) :
    ByteContainer(bytes),
    InboundL1Message,
    OutboundL2Message {
    override val type: String = "CspData"
}

internal class CspLoginMessage(bytes: ByteArray) :
    ByteContainer(bytes),
    InboundL2Message,
    OutboundL3Message {
    override val type: String = "CspLoginMessage"
}

internal class CspFrame(val box: ByteArray) :
    InboundL2Message,
    OutboundL3Message {
    override val type: String = "CspFrame"

    fun toCspData(): CspData {
        // wrap messages with `frame`
        // (https://clients.pages.threema.dev/protocols/threema-protocols/structbuf/csp/index.html#m:payload:frame)
        if (box.size > ProtocolDefines.MAX_PKT_LEN) {
            throw InvalidSizeException("Package is too big (${box.size} bytes)")
        }
        val length = ByteBuffer.wrap(ByteArray(2))
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(box.size.toShort())
            .array()
        return CspData(length + box)
    }
}

/**
 * Contains data of a decrypted csp message
 * https://clients.pages.threema.dev/protocols/threema-protocols/structbuf/csp/index.html#m:payload:container
 */
internal class CspContainer(val payloadType: UByte, val data: ByteArray) :
    InboundL3Message,
    InboundL4Message,
    OutboundL4Message,
    OutboundL5Message {
    override val type: String = "CspContainer"

    override fun toInboundMessage(): CspMessage {
        return CspMessage(payloadType, data)
    }
}

/**
 * A csp message that is ready to be processed (inbound) or sent to server (outbound)
 */
class CspMessage(
    override val payloadType: UByte,
    private val data: ByteArray,
) : InboundMessage, OutboundMessage {
    // Retrofit constructor that can also be called from java code (with signed payload type)
    constructor(payloadType: Int, data: ByteArray) : this(payloadType.toUByte(), data)

    @JvmInline
    value class ServerErrorData(private val data: ByteArray) {
        val canReconnect: Boolean
            get() = data[0] != 0.toByte()

        val message: String
            get() = String(data, 1, data.size - 1, StandardCharsets.UTF_8)
    }

    @JvmInline
    value class ServerAlertData(val data: ByteArray) {
        val message: String
            get() = String(data, StandardCharsets.UTF_8)
    }

    @JvmInline
    value class MessageAck(val data: ByteArray) {
        val recipient: String
            get() {
                if (data.size != ProtocolDefines.IDENTITY_LEN + ProtocolDefines.MESSAGE_ID_LEN) {
                    throw PayloadProcessingException("Bad length (${data.size}) for message ack payload")
                }

                return String(
                    data,
                    0,
                    ProtocolDefines.IDENTITY_LEN,
                    StandardCharsets.UTF_8,
                )
            }

        val messageId: MessageId
            get() {
                if (data.size != ProtocolDefines.IDENTITY_LEN + ProtocolDefines.MESSAGE_ID_LEN) {
                    throw PayloadProcessingException("Bad length (${data.size}) for message ack payload")
                }

                // Note that IDENTITY_LEN specifies the offset where the message id starts
                return MessageId(data, ProtocolDefines.IDENTITY_LEN)
            }
    }

    @JvmInline
    value class IncomingMessageData(val data: ByteArray)

    @JvmInline
    value class OutgoingMessageData(val data: ByteArray)

    fun toServerErrorData(): ServerErrorData {
        assertPayloadType(ProtocolDefines.PLTYPE_ERROR)
        if (data.isEmpty()) {
            throw PayloadProcessingException("Bad length (${data.size}) for error payload")
        }
        return ServerErrorData(data)
    }

    fun toServerAlertData(): ServerAlertData {
        assertPayloadType(ProtocolDefines.PLTYPE_ALERT)
        return ServerAlertData(data)
    }

    fun toOutgoingMessageAck(): MessageAck {
        assertPayloadType(ProtocolDefines.PLTYPE_OUTGOING_MESSAGE_ACK)
        return MessageAck(data)
    }

    fun toIncomingMessageData(): IncomingMessageData {
        assertPayloadType(ProtocolDefines.PLTYPE_INCOMING_MESSAGE)
        return IncomingMessageData(data)
    }

    fun toOutgoingMessageData(): OutgoingMessageData {
        assertPayloadType(ProtocolDefines.PLTYPE_OUTGOING_MESSAGE)
        return OutgoingMessageData(data)
    }

    fun toIncomingMessageAck(): MessageAck {
        assertPayloadType(ProtocolDefines.PLTYPE_INCOMING_MESSAGE_ACK)
        return MessageAck(data)
    }

    internal fun toCspContainer(): CspContainer {
        return CspContainer(payloadType, data)
    }

    private fun assertPayloadType(payloadType: Int) {
        if (this.payloadType != payloadType.toUByte()) {
            throw PayloadProcessingException("CspMessage has invalid payload type. Expected: ${payloadType.toUByte()}, actual: ${this.payloadType}")
        }
    }
}
