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
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeliveryReceiptMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageState

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupDeliveryReceiptTask")

class IncomingGroupDeliveryReceiptTask(
    message: GroupDeliveryReceiptMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupDeliveryReceiptMessage>(message, triggerSource, serviceManager) {
    private val messageService = serviceManager.messageService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        executeMessageSteps(
            runCommonGroupReceiveSteps = {
                runCommonGroupReceiveSteps(message, handle, serviceManager)
            }
        )

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult = executeMessageSteps()

    private suspend fun executeMessageSteps(
        runCommonGroupReceiveSteps: (suspend () -> GroupModel?)? = null
    ): ReceiveStepsResult {
        logger.info("Processing message {}: incoming group delivery receipt", message.messageId)

        val messageState: MessageState? = MessageUtil.receiptTypeToMessageState(message.receiptType)
        if (messageState == null || !MessageUtil.isReaction(messageState)) {
            logger.warn(
                "Message {} error: unknown or unsupported delivery receipt type: {}",
                message.messageId,
                message.receiptType
            )
            return ReceiveStepsResult.DISCARD
        }

        if (runCommonGroupReceiveSteps != null) {
            // If the common group receive steps did not succeed (null is returned), ignore this delivery receipt
            runCommonGroupReceiveSteps() ?: return ReceiveStepsResult.DISCARD
        }

        for (receiptMessageId: MessageId in message.receiptMessageIds) {
            logger.info(
                "Processing message {}: group delivery receipt for {} (state = {})",
                message.messageId,
                receiptMessageId,
                messageState
            )
            val groupMessageModel: GroupMessageModel? = messageService.getGroupMessageModel(
                receiptMessageId,
                message.groupCreator,
                message.apiGroupId
            )
            if (groupMessageModel == null) {
                logger.warn("Group message model ({}) for incoming group delivery receipt is null", receiptMessageId)
                continue
            }
            messageService.addMessageReaction(
                groupMessageModel,
                messageState,
                message.fromIdentity,
                message.date
            )
        }

        return ReceiveStepsResult.SUCCESS
    }
}
