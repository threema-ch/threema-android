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
import ch.threema.app.processors.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.tasks.runCommonDeleteMessageReceiveSteps
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.GroupDeleteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("IncomingGroupDeleteMessageTask")

class IncomingGroupDeleteMessageTask(
    private val deleteMessage: GroupDeleteMessage,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask(serviceManager) {

    private val messageService = serviceManager.messageService
    private val groupService = serviceManager.groupService

    override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        logger.debug("IncomingGroupDeleteMessageTask id: {}", deleteMessage.data.messageId)

        val groupModel = runCommonGroupReceiveSteps(deleteMessage, handle, serviceManager)
            ?: return ReceiveStepsResult.DISCARD

        val receiver = groupService.createReceiver(groupModel)
        val message = runCommonDeleteMessageReceiveSteps(deleteMessage, receiver, messageService)
            ?: return ReceiveStepsResult.DISCARD

        messageService.deleteMessageContents(message, deleteMessage.date)
        return ReceiveStepsResult.SUCCESS
    }
}
