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

package ch.threema.app.processors

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.storage.models.ServerMessageModel

private val logger = LoggingUtil.getThreemaLogger("IncomingMessageProcessorImpl")

class IncomingMessageProcessorImpl(private val serviceManager: ServiceManager) :
    IncomingMessageProcessor {
    private val messageService by lazy { serviceManager.messageService }

    override suspend fun processIncomingCspMessage(
        messageBox: MessageBox,
        handle: ActiveTaskCodec,
    ) {
        IncomingMessageTask(messageBox, serviceManager).run(handle)
    }

    override suspend fun processIncomingD2mMessage(
        message: InboundD2mMessage.Reflected,
        handle: ActiveTaskCodec,
    ) {
        IncomingReflectedMessageTask(message, serviceManager).run(handle)
    }

    override fun processIncomingServerAlert(alertData: CspMessage.ServerAlertData) {
        val msg = ServerMessageModel(alertData.message, ServerMessageModel.TYPE_ALERT)
        messageService.saveIncomingServerMessage(msg)
    }

    override fun processIncomingServerError(errorData: CspMessage.ServerErrorData) {
        val errorMessage = errorData.message
        if (errorMessage.contains("Another connection")) {
            // See `MonitoringLayer#handleCloseError(CspContainer)` for more info
            logger.info("Do not display `Another connection` close-error")
        } else {
            val msg = ServerMessageModel(errorMessage, ServerMessageModel.TYPE_ERROR)
            messageService.saveIncomingServerMessage(msg)
        }
    }
}
