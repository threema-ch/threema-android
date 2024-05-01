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

package ch.threema.domain.protocol.connection.layer

import ch.threema.domain.protocol.connection.InboundPipeProcessor
import ch.threema.domain.protocol.connection.PipeSink
import ch.threema.domain.protocol.connection.PipeSource
import ch.threema.domain.protocol.connection.OutboundPipeProcessor
import ch.threema.domain.protocol.connection.data.InboundL1Message
import ch.threema.domain.protocol.connection.data.InboundL2Message
import ch.threema.domain.protocol.connection.data.InboundL3Message
import ch.threema.domain.protocol.connection.data.InboundL4Message
import ch.threema.domain.protocol.connection.data.OutboundL2Message
import ch.threema.domain.protocol.connection.data.OutboundL3Message
import ch.threema.domain.protocol.connection.data.OutboundL4Message
import ch.threema.domain.protocol.connection.data.OutboundL5Message
import ch.threema.domain.protocol.connection.data.OutboundMessage

/**
 * The frame layer decodes incoming byte arrays to the correct container type.
 * The container type depends on the implementation and is different for multi device and
 * non-multi device environment.
 */
internal interface Layer1Codec : InboundPipeProcessor<ByteArray, InboundL1Message>, OutboundPipeProcessor<OutboundL2Message, ByteArray>

/**
 * The multiplex layer is primarly used in a multi device setup and has the responsibility to demultiplex
 * d2m.container to either D2M messages or CSP frames.
 * In a non-multi device environment it will probably only pass the messages on to the next layer.
 */
internal interface Layer2Codec : InboundPipeProcessor<InboundL1Message, InboundL2Message>, OutboundPipeProcessor<OutboundL3Message, OutboundL2Message>

/**
 * The authentication and transport encryption layer is responsible for the server handshakes
 * (chatserver, mediator) and the transport decryption of handshakes.
 */
internal interface Layer3Codec : InboundPipeProcessor<InboundL2Message, InboundL3Message>, OutboundPipeProcessor<OutboundL4Message, OutboundL3Message>

/**
 * The connection monitoring and keep alive layer.
 * This layer is responsible for sending and receiving echo requests and thereby keeping the connection
 * alive.
 * It is also responsible for correctly handle events, when the connection is closed by the server
 * e.g. with a close-error.
 */
internal interface Layer4Codec : InboundPipeProcessor<InboundL3Message, InboundL4Message>, OutboundPipeProcessor<OutboundL5Message, OutboundL4Message>

/**
 * The so called end-to-end layer which is responsible for linking the connection to the task manager.
 * This includes dispatching inbound messages to the task manager and passing on outbound messages to the
 * outer layers.
 */
internal interface Layer5Codec : PipeSource<OutboundL5Message>, PipeSink<InboundL4Message> {
    /**
     * Send an outbound message.
     */
    fun sendOutbound(message: OutboundMessage)

    /**
     * Trigger a connection restart after waiting for [delayMs] milliseconds. Note that this does
     * only have an effect, if the connection is still running.
     */
    fun restartConnection(delayMs: Long)
}
