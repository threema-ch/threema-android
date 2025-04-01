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

package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("IncomingGroupLocationMessageTask")

class IncomingGroupLocationMessageTask(
    private val groupLocationMessage: GroupLocationMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupLocationMessage>(
    groupLocationMessage,
    triggerSource,
    serviceManager
) {

    private val messageService by lazy { serviceManager.messageService }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        runCommonGroupReceiveSteps(message, handle, serviceManager) ?: run {
            logger.warn("Discarding message ${message.messageId}: Could not find group for incoming group location message")
            return ReceiveStepsResult.DISCARD
        }
        return processGroupLocationMessage()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processGroupLocationMessage()

    private fun processGroupLocationMessage(): ReceiveStepsResult {
        val processedMessageSuccessfully =
            messageService.processIncomingGroupMessage(groupLocationMessage)
        return if (processedMessageSuccessfully) ReceiveStepsResult.SUCCESS else ReceiveStepsResult.DISCARD
    }
}
