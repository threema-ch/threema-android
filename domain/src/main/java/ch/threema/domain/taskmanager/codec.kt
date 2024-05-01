/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.csp.ProtocolDefines

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
     * Reflect the given message.
     */
    suspend fun reflect(message: OutboundMessage)
}

interface TaskCodec : ActiveTaskCodec

private val logger = LoggingUtil.getThreemaLogger("TaskCodec")

suspend fun PassiveTaskCodec.waitForServerAck(
    messageId: MessageId,
    recipientIdentity: String,
) {
    read { inboundMessage ->
        return@read when (inboundMessage.payloadType.toInt()) {
            ProtocolDefines.PLTYPE_OUTGOING_MESSAGE_ACK -> {
                val ack = (inboundMessage as CspMessage).toOutgoingMessageAck()

                logger.debug(
                    "Checking message ack for message {} to {}",
                    ack.messageId,
                    ack.recipient
                )

                if (ack.messageId == messageId && ack.recipient == recipientIdentity) {
                    MessageFilterInstruction.ACCEPT
                } else {
                    MessageFilterInstruction.BYPASS_OR_BACKLOG
                }
            }

            else -> {
                MessageFilterInstruction.BYPASS_OR_BACKLOG
            }
        }
    }
}
