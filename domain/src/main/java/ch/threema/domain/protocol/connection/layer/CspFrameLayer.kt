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
import ch.threema.domain.protocol.connection.PipeProcessor
import ch.threema.domain.protocol.connection.data.ByteContainer
import ch.threema.domain.protocol.connection.data.CspData
import ch.threema.domain.protocol.connection.data.InboundL1Message
import ch.threema.domain.protocol.connection.data.OutboundL2Message
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil

private val logger = ConnectionLoggingUtil.getConnectionLogger("CspFrameLayer")

internal class CspFrameLayer : Layer1Codec {
    override val encoder: PipeProcessor<OutboundL2Message, ByteArray, Unit> = MappingPipe {
        logger.trace("Handle outbound message of type `{}`", it.type)
        if (it is ByteContainer) {
            it.bytes
        } else {
            throw ServerConnectionException("OutboundL2Message has invalid type `${it.type}`")
        }
    }

    override val decoder: PipeProcessor<ByteArray, InboundL1Message, ServerSocketCloseReason> =
        MappingPipe {
            logger.trace("Handle inbound message with {} bytes", it.size)
            CspData(it)
        }
}
