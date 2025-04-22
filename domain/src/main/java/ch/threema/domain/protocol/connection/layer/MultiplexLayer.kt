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

package ch.threema.domain.protocol.connection.layer

import ch.threema.domain.protocol.D2mPayloadType
import ch.threema.domain.protocol.connection.InvalidSizeException
import ch.threema.domain.protocol.connection.PipeProcessor
import ch.threema.domain.protocol.connection.ProcessingPipe
import ch.threema.domain.protocol.connection.ServerConnectionException
import ch.threema.domain.protocol.connection.data.CspData
import ch.threema.domain.protocol.connection.data.CspFrame
import ch.threema.domain.protocol.connection.data.CspLoginMessage
import ch.threema.domain.protocol.connection.data.D2mContainer
import ch.threema.domain.protocol.connection.data.D2mProtocolException
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundL1Message
import ch.threema.domain.protocol.connection.data.InboundL2Message
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundL2Message
import ch.threema.domain.protocol.connection.data.OutboundL3Message
import ch.threema.domain.protocol.connection.data.toHex
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.connection.util.MdServerConnectionController
import ch.threema.domain.protocol.connection.util.ServerConnectionController
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val logger = ConnectionLoggingUtil.getConnectionLogger("MultiplexLayer")

internal class MultiplexLayer(private val controller: ServerConnectionController) : Layer2Codec {
    private val inbound =
        ProcessingPipe<InboundL1Message, InboundL2Message, ServerSocketCloseReason> {
            handleInbound(it)
        }
    private val outbound =
        ProcessingPipe<OutboundL3Message, OutboundL2Message, Unit> { handleOutbound(it) }

    override val encoder: PipeProcessor<OutboundL3Message, OutboundL2Message, Unit> = outbound
    override val decoder: PipeProcessor<InboundL1Message, InboundL2Message, ServerSocketCloseReason> =
        inbound

    private fun handleInbound(message: InboundL1Message) {
        controller.dispatcher.assertDispatcherContext()

        logger.trace("Handle inbound message of type `{}`", message.type)
        when (message) {
            is CspData -> handleInboundCspMessage(message)
            is D2mContainer -> handleInboundD2mContainer(message)
        }
    }

    private fun handleInboundCspMessage(message: CspData) {
        if (controller.cspSessionState.isLoginDone) {
            // When the csp login is done the data should be treated as a csp frame
            if (message.bytes.size < ProtocolDefines.OVERHEAD_PKT_HDR + ProtocolDefines.OVERHEAD_NACL_BOX) {
                logger.error("Short payload received ({} bytes)", message.bytes.size)
            } else {
                logger.debug("Received payload ({} bytes)", message.bytes.size)
                inbound.send(CspFrame(message.bytes))
            }
        } else {
            // When the login is not yet completed the data has to be handled as a login message
            inbound.send(CspLoginMessage(message.bytes))
        }
    }

    private fun handleInboundD2mContainer(container: D2mContainer) {
        if (controller !is MdServerConnectionController) {
            throw ServerConnectionException("Received D2mContainer in non-md configuration")
        }
        logger.info(
            "Handle inbound D2mContainer with payloadType={}",
            container.payloadType.toHex(),
        )
        if (container.payloadType == D2mPayloadType.PROXY) {
            handleInboundCspMessage(getCspDataFromD2mProxyMessage(container))
        } else {
            try {
                inbound.send(InboundD2mMessage.decodeContainer(container))
            } catch (e: D2mProtocolException) {
                logger.error("Could not decode D2mContainer", e)
            }
        }
    }

    /**
     * Extract the `CspData` from a D2m Proxy message.
     * <p>
     * If the csp login is not yet completed, the full payload is relevant data which must be passed
     * on to the next layer as a [CspLoginMessage].
     * If the csp login is already done, the length has to be stripped from the payload, as this is
     * not required for further processing (will be passed on as a [CspFrame]).
     * Note: The conversion to a [CspLoginMessage] or a [CspFrame] will be completed in [handleInboundCspMessage].
     * <p>
     * When the [ch.threema.domain.protocol.connection.CspConnection] is used, the differentiation between
     * login messages and frames is already performed in the [ch.threema.domain.protocol.connection.socket.CspSocket].
     */
    private fun getCspDataFromD2mProxyMessage(container: D2mContainer): CspData {
        if (container.payloadType != D2mPayloadType.PROXY) {
            throw D2mProtocolException("Not a proxy message (payloadType=${container.payloadType})")
        }
        return if (controller.cspSessionState.isLoginDone) {
            val length = ByteBuffer
                .wrap(container.payload.copyOfRange(0, 2))
                .order(ByteOrder.LITTLE_ENDIAN)
                .short.toUShort().toInt()
            val size = container.payload.size
            val payload = container.payload.copyOfRange(2, size)
            if (container.payload.size - 2 != length) {
                throw InvalidSizeException("Encoded and actual data length do not match")
            }
            // Extract only the payload, as the length is not used anymore [CspFrame]
            CspData(payload)
        } else {
            // Use the full payload [CspLoginMessage]
            CspData(container.payload)
        }
    }

    private fun handleOutbound(message: OutboundL3Message) {
        logger.trace("Handle outbound message of type `{}`", message.type)
        when (message) {
            is CspFrame -> handleOutboundCspFrame(message)
            is CspLoginMessage -> handleOutboundCspLoginMessage(message)
            is OutboundD2mMessage -> handleOutboundD2mMessage(message)
        }
    }

    private fun handleOutboundCspFrame(message: CspFrame) {
        try {
            sendOutboundCspData(message.toCspData())
        } catch (e: InvalidSizeException) {
            logger.info("Ignore packet with invalid size", e)
        }
    }

    private fun handleOutboundCspLoginMessage(message: CspLoginMessage) {
        sendOutboundCspData(CspData(message.bytes))
    }

    private fun sendOutboundCspData(data: CspData) {
        val outboundData = if (controller is MdServerConnectionController) {
            // MD Configuration -> wrap in proxy message
            logger.debug("Send csp data as d2m proxy message")
            D2mContainer(D2mPayloadType.PROXY, data.bytes)
        } else {
            data
        }
        outbound.send(outboundData)
    }

    private fun handleOutboundD2mMessage(message: OutboundD2mMessage) {
        outbound.send(message.toContainer())
    }
}
