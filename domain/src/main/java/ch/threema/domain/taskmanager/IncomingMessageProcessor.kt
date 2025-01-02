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

import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.csp.coders.MessageBox

interface IncomingMessageProcessor {

    /**
     * Process an incoming csp message.
     */
    suspend fun processIncomingCspMessage(messageBox: MessageBox, handle: ActiveTaskCodec)

    /**
     * Process an incoming d2m message.
     */
    suspend fun processIncomingD2mMessage(message: InboundD2mMessage.Reflected, handle: ActiveTaskCodec)

    /**
     * Process an incoming server alert
     */
    fun processIncomingServerAlert(alertData: CspMessage.ServerAlertData)

    /**
     * Process an incoming server error
     */
    fun processIncomingServerError(errorData: CspMessage.ServerErrorData)

}
