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

package ch.threema.app.processors.statusupdates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.IncomingCspMessageSubTask
import ch.threema.app.processors.ReceiveStepsResult
import ch.threema.app.processors.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.storage.models.MessageState

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupDeliveryReceiptTask")

class IncomingGroupDeliveryReceiptTask(
    private val message: GroupDeliveryReceiptMessage,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask(serviceManager) {
    private val messageService = serviceManager.messageService

    override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        val state: MessageState = when (message.receiptType) {
            ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK -> MessageState.USERACK
            ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC -> MessageState.USERDEC
            else -> {
                logger.warn(
                    "Message {} error: unknown or unsupported delivery receipt type",
                    message.messageId
                )
                return ReceiveStepsResult.DISCARD
            }
        }
        if (runCommonGroupReceiveSteps(message, handle, serviceManager) == null) {
            // If the common group receive steps did not succeed, ignore this delivery receipt
            return ReceiveStepsResult.DISCARD
        }
        for (messageId in message.receiptMessageIds) {
            logger.info(
                "Message {}: group delivery receipt for {} (state = {})",
                messageId.messageId,
                messageId,
                state
            )
            messageService.updateGroupMessageState(messageId, state, message)
        }

        return ReceiveStepsResult.SUCCESS
    }
}
