/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.processors.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.IncomingCspMessageSubTask
import ch.threema.app.processors.ReceiveStepsResult
import ch.threema.app.tasks.runCommonDeleteMessageReceiveSteps
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("IncomingContactDeleteMessageTask")

class IncomingContactDeleteMessageTask(
    private val deleteMessage: DeleteMessage,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask(serviceManager) {

    private val messageService by lazy { serviceManager.messageService }
    private val contactService by lazy { serviceManager.contactService }

    override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.debug("IncomingContactDeleteMessageTask id: {}", deleteMessage.data.messageId)

        val contactModel = contactService.getByIdentity(deleteMessage.fromIdentity)
        if (contactModel == null) {
            logger.warn("Incoming Delete Message: No contact found for {}", deleteMessage.fromIdentity)
            return ReceiveStepsResult.DISCARD
        }

        val receiver = contactService.createReceiver(contactModel)
        val message = runCommonDeleteMessageReceiveSteps(deleteMessage, receiver, messageService)
            ?: return ReceiveStepsResult.DISCARD

        messageService.deleteMessageContentsAndEditHistory(message, deleteMessage.date)

        return ReceiveStepsResult.SUCCESS
    }
}
