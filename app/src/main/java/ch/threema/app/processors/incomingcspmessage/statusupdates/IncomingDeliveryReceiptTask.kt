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

package ch.threema.app.processors.incomingcspmessage.statusupdates

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

private val logger = LoggingUtil.getThreemaLogger("IncomingDeliveryReceiptTask")

class IncomingDeliveryReceiptTask(
    message: DeliveryReceiptMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<DeliveryReceiptMessage>(message, triggerSource, serviceManager) {
    private val messageService = serviceManager.messageService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec) =
        processIncomingDeliveryReceipt()

    override suspend fun executeMessageStepsFromSync() = processIncomingDeliveryReceipt()

    private fun processIncomingDeliveryReceipt(): ReceiveStepsResult {
        val state = MessageUtil.receiptTypeToMessageState(message.receiptType)

        if (state == null) {
            logger.warn(
                "Message {} error: unknown delivery receipt type: {}",
                message.messageId,
                message.receiptType
            )
            return ReceiveStepsResult.DISCARD
        }

        message.receiptMessageIds.forEach {
            logger.info(
                "Processing message {}: delivery receipt for {} (state = {})",
                message.messageId,
                it,
                state
            )
        }

        message.receiptMessageIds.mapNotNull { receiptMessageId ->
            messageService.getContactMessageModel(receiptMessageId, message.fromIdentity)
        }.forEach { messageModel ->
            if (MessageUtil.isReaction(state)) {
                messageService.addMessageReaction(
                    messageModel,
                    state,
                    message.fromIdentity,
                    message.date
                )
            } else {
                messageService.updateOutgoingMessageState(
                    messageModel,
                    state,
                    message.date
                )
            }
        }
        return ReceiveStepsResult.SUCCESS
    }
}
