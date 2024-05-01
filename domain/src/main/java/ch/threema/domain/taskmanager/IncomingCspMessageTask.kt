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
import ch.threema.domain.protocol.connection.PayloadProcessingException
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.coders.MessageBox

private val logger = LoggingUtil.getThreemaLogger("IncomingCspMessageTask")

class IncomingCspMessageTask(
    private val message: CspMessage,
    private val incomingMessageProcessor: IncomingMessageProcessor,
) : ActiveTask<Unit> {
    override val type: String = "IncomingCspMessageTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        when(message.payloadType.toInt()) {
            ProtocolDefines.PLTYPE_ERROR -> processError(message.toServerErrorData())
            ProtocolDefines.PLTYPE_ALERT -> processAlert(message.toServerAlertData())
            ProtocolDefines.PLTYPE_OUTGOING_MESSAGE_ACK -> processOutgoingMessageAck(message.toOutgoingMessageAck())
            ProtocolDefines.PLTYPE_INCOMING_MESSAGE -> processIncomingMessage(message.toIncomingMessageData(), handle)
            ProtocolDefines.PLTYPE_QUEUE_SEND_COMPLETE -> processQueueSendComplete()
            ProtocolDefines.PLTYPE_DEVICE_COOKIE_CHANGE_INDICATION -> processDeviceCookieChangeIndication()
            else -> throw PayloadProcessingException("Unknown payload type ${message.payloadType}")
        }
    }

    private fun processError(data: CspMessage.ServerErrorData) {
        logger.warn("Processed server error in incoming csp message task: '{}'", data.message)
    }

    private fun processAlert(data: CspMessage.ServerAlertData) {
        logger.warn("Processed server alert in incoming csp message task: '{}'", data.message)
    }

    private fun processOutgoingMessageAck(data: CspMessage.MessageAck) {
        logger.warn(
            "Processed ack for outgoing message {} to {} in incoming csp message task",
            data.messageId,
            data.recipient
        )
    }

    private suspend fun processIncomingMessage(
        data: CspMessage.IncomingMessageData,
        handle: ActiveTaskCodec,
    ) {
        if (data.data.size < ProtocolDefines.OVERHEAD_MSG_HDR) {
            throw PayloadProcessingException("Bad length (${data.data.size}) for message payload")
        }
        suspend {
            val messageBox = MessageBox.parseBinary(data.data)

            incomingMessageProcessor.processIncomingMessage(messageBox, handle)
        }.catchAllExceptNetworkException {
            logger.error("Could not process incoming message", it)
        }
    }

    private fun processQueueSendComplete() {
        logger.warn("Processed queue send complete inside incoming csp message task")
    }

    private fun processDeviceCookieChangeIndication() {
        logger.warn("Processed device cookie change indication inside incoming csp message task")
    }

}
