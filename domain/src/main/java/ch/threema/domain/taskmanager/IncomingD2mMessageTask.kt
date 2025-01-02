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
import ch.threema.domain.protocol.D2mPayloadType
import ch.threema.domain.protocol.connection.data.InboundD2mMessage

private val logger = LoggingUtil.getThreemaLogger("IncomingD2mMessageTask")

class IncomingD2mMessageTask(
    private val message: InboundD2mMessage,
    private val incomingMessageProcessor: IncomingMessageProcessor,
) : ActiveTask<Unit> {
    override val type: String = "IncomingD2mMessageTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        when (message.payloadType) {
            D2mPayloadType.REFLECTED ->
                handleReflected(message as InboundD2mMessage.Reflected, handle)

            else -> logger.warn("Unexpected d2m message of type {} received", message.payloadType)
        }
    }

    private suspend fun handleReflected(
        message: InboundD2mMessage.Reflected,
        handle: ActiveTaskCodec,
    ) {
        incomingMessageProcessor.processIncomingD2mMessage(message, handle)
    }
}
