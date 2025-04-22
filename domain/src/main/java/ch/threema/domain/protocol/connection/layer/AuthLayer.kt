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

import ch.threema.domain.protocol.connection.*
import ch.threema.domain.protocol.connection.data.CspContainer
import ch.threema.domain.protocol.connection.data.CspFrame
import ch.threema.domain.protocol.connection.data.CspLoginMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundL2Message
import ch.threema.domain.protocol.connection.data.InboundL3Message
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundL3Message
import ch.threema.domain.protocol.connection.data.OutboundL4Message
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.connection.util.Layer3Controller
import ch.threema.domain.protocol.connection.util.MdLayer3Controller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = ConnectionLoggingUtil.getConnectionLogger("AuthLayer")

internal class AuthLayer(
    private val controller: Layer3Controller,
) : Layer3Codec {
    init {
        CoroutineScope(controller.dispatcher.coroutineContext).launch {
            controller.connected.await()
            initiateCspHandshake()
        }
    }

    private val mdController: MdLayer3Controller by lazy {
        if (controller is MdLayer3Controller) {
            controller
        } else {
            throw ServerConnectionException("Requested md controller in non-md configuration")
        }
    }

    private val inbound =
        ProcessingPipe<InboundL2Message, InboundL3Message, ServerSocketCloseReason> {
            handleInbound(it)
        }
    private val outbound =
        ProcessingPipe<OutboundL4Message, OutboundL3Message, Unit> { handleOutbound(it) }

    override val encoder: PipeProcessor<OutboundL4Message, OutboundL3Message, Unit> = outbound
    override val decoder: PipeProcessor<InboundL2Message, InboundL3Message, ServerSocketCloseReason> =
        inbound

    private fun initiateCspHandshake() {
        controller.dispatcher.assertDispatcherContext()

        controller.cspSession.startLogin(outbound)
    }

    private fun handleInbound(message: InboundL2Message) {
        controller.dispatcher.assertDispatcherContext()

        logger.trace("Handle inbound message of type `{}`", message.type)
        when (message) {
            is CspLoginMessage -> handleInboundCspLoginMessage(message)
            is CspFrame -> handleInboundCspFrame(message)
            is InboundD2mMessage -> handleInboundD2mMessage(message)
        }
    }

    private fun handleOutbound(message: OutboundL4Message) {
        controller.dispatcher.assertDispatcherContext()

        logger.trace("Handle outbound message of type `{}`", message.type)
        when (message) {
            is CspContainer -> handlePostHandshakeOutboundCspMessage(message)
            is OutboundD2mMessage -> handleOutboundD2mMessage(message)
        }
    }

    private fun handlePostHandshakeOutboundCspMessage(container: CspContainer) {
        controller.dispatcher.assertDispatcherContext()

        outbound.send(controller.cspSession.encryptContainer(container))
    }

    private fun handleInboundCspLoginMessage(message: CspLoginMessage) {
        controller.cspSession.handleLoginMessage(message, outbound)
        if (controller.cspSession.isLoginDone) {
            controller.cspAuthenticated.complete(Unit)
            logger.debug("Csp handshake completed")
        }
    }

    private fun handleInboundCspFrame(frame: CspFrame) {
        controller.dispatcher.assertDispatcherContext()

        inbound.send(controller.cspSession.decryptBox(frame))
    }

    private fun handleOutboundD2mMessage(container: OutboundD2mMessage) {
        controller.dispatcher.assertDispatcherContext()

        outbound.send(container)
    }

    private fun handleInboundD2mMessage(message: InboundD2mMessage) {
        controller.dispatcher.assertDispatcherContext()

        if (mdController.d2mSession.isLoginDone) {
            inbound.send(message)
        } else {
            mdController.d2mSession.handleHandshakeMessage(message, outbound)
        }
    }
}
