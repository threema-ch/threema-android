/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.domain.helpers

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.taskmanager.MessageFilterInstruction
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.coroutines.runBlocking

/**
 * This task codec is used only for tests. It acts as the server and creates server acknowledgements
 * for the sent messages. Messages sent using this task codec are never sent to the server.
 */
open class ServerAckTaskCodec : TaskCodec {
    val inboundMessages = mutableListOf<InboundMessage>()
    val outboundMessages = mutableListOf<OutboundMessage>()
    val ackedIncomingMessages = mutableListOf<MessageId>()
    val reflectedMessages = mutableListOf<OutboundMessage>()

    override suspend fun read(preProcess: (InboundMessage) -> MessageFilterInstruction): InboundMessage {
        for (inboundMessage in inboundMessages) {
            when (preProcess(inboundMessage)) {
                MessageFilterInstruction.ACCEPT -> {
                    inboundMessages.remove(inboundMessage)
                    return inboundMessage
                }

                // No need to bypass or backlog messages in test codec
                MessageFilterInstruction.BYPASS_OR_BACKLOG -> continue

                // No need to reject messages in test codec
                MessageFilterInstruction.REJECT -> continue
            }
        }

        throw AssertionError("No message provided that was expected")
    }

    open fun writeAsync(message: OutboundMessage) {
        runBlocking {
            write(message)
        }
    }

    override suspend fun write(message: OutboundMessage) {
        outboundMessages.add(message)

        val cspMessage = message as CspMessage
        when (cspMessage.payloadType.toInt()) {
            ProtocolDefines.PLTYPE_OUTGOING_MESSAGE -> {
                handleOutgoingMessage(cspMessage)
            }

            ProtocolDefines.PLTYPE_INCOMING_MESSAGE_ACK -> {
                handleIncomingMessageAck(cspMessage)
            }
        }
    }

    override suspend fun reflect(message: OutboundMessage) {
        reflectedMessages.add(message)
    }

    /**
     * The server ack task codec creates the server ack here. Other tasks may perform additional
     * tasks or override this behavior completely.
     */
    protected open suspend fun handleOutgoingMessageBox(messageBox: MessageBox) {
        if ((messageBox.flags and ProtocolDefines.MESSAGE_FLAG_NO_SERVER_ACK) == 0) {
            // Create a server ack and add it to the inbound message queue, as the server
            val serverAck = createServerAck(messageBox)
            inboundMessages.add(serverAck)
        }
    }

    /**
     * This is called when a message has been sent using this task codec.
     */
    private suspend fun handleOutgoingMessage(cspMessage: CspMessage) {
        val messageBox = parseMessageBox(cspMessage)
        handleOutgoingMessageBox(messageBox)
    }

    /**
     * This is called when an ack for an incoming message has been sent using this task codec.
     */
    private fun handleIncomingMessageAck(cspMessage: CspMessage) {
        val data = cspMessage.toIncomingMessageAck()
        ackedIncomingMessages.add(data.messageId)
    }

    private fun parseMessageBox(cspMessage: CspMessage): MessageBox {
        val data = cspMessage.toOutgoingMessageData()
        return MessageBox.parseBinary(data.data)
    }

    private fun createServerAck(messageBox: MessageBox): CspMessage {
        val outgoingMessageAckData =
            messageBox.toIdentity.encodeToByteArray() + messageBox.messageId.messageId
        return CspMessage(
            ProtocolDefines.PLTYPE_OUTGOING_MESSAGE_ACK.toUByte(),
            outgoingMessageAckData
        )
    }
}

