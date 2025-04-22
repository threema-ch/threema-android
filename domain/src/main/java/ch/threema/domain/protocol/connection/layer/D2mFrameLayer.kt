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

import ch.threema.domain.protocol.D2mProtocolDefines
import ch.threema.domain.protocol.connection.InvalidSizeException
import ch.threema.domain.protocol.connection.MappingPipe
import ch.threema.domain.protocol.connection.PipeProcessor
import ch.threema.domain.protocol.connection.ServerConnectionException
import ch.threema.domain.protocol.connection.data.D2mContainer
import ch.threema.domain.protocol.connection.data.InboundL1Message
import ch.threema.domain.protocol.connection.data.OutboundL2Message
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil

private val logger = ConnectionLoggingUtil.getConnectionLogger("D2mFrameLayer")

internal class D2mFrameLayer : Layer1Codec {
    override val encoder: PipeProcessor<OutboundL2Message, ByteArray, Unit> = MappingPipe {
        logger.trace("Handle outbound message of type `{}`", it.type)
        if (it is D2mContainer) {
            it.bytes
        } else {
            throw ServerConnectionException("OutboundL2Message has invalid type `${it.type}`")
        }
    }

    override val decoder: PipeProcessor<ByteArray, InboundL1Message, ServerSocketCloseReason> =
        MappingPipe {
            logger.trace("Handle inbound message with {} bytes", it.size)

            if (it.size > D2mProtocolDefines.D2M_FRAME_MAX_BYTES_LENGTH) {
                throw InvalidSizeException("Inbound frame too large: ${it.size} bytes")
            }

            if (it.size < D2mProtocolDefines.D2M_FRAME_MIN_BYTES_LENGTH) {
                throw InvalidSizeException("Inbound frame too small: ${it.size} bytes")
            }

            D2mContainer(it[0].toUByte(), it.copyOfRange(4, it.size))
        }
}
