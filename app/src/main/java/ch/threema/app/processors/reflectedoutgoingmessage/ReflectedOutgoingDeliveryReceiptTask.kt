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

package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.MessageUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.DeliveryReceiptMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageState
import java.util.Date

private val logger = LoggingUtil.getThreemaLogger("ReflectedOutgoingDeliveryReceiptTask")

internal class ReflectedOutgoingDeliveryReceiptTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<DeliveryReceiptMessage>(
    outgoingMessage = outgoingMessage,
    message = DeliveryReceiptMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.DELIVERY_RECEIPT,
    serviceManager = serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }
    private val notificationService by lazy { serviceManager.notificationService }
    private val myIdentity by lazy { serviceManager.identityStore.identity }

    override fun processOutgoingMessage() {
        logger.info("Processing reflected outgoing delivery receipt")

        val deliveryReceiptMessage = DeliveryReceiptMessage.fromReflected(outgoingMessage)
        val state = MessageUtil.receiptTypeToMessageState(deliveryReceiptMessage.receiptType)

        if (state == null) {
            logger.warn("Message {} error: unknown delivery receipt type", outgoingMessage.messageId)
            return
        }

        val identity = outgoingMessage.conversation.contact

        for (messageId in deliveryReceiptMessage.receiptMessageIds) {
            val messageModel = messageService.getContactMessageModel(messageId, identity)
            if (messageModel == null) {
                logger.warn(
                    "Message model ({}) for reflected outgoing delivery receipt is null",
                    messageId,
                )
                continue
            }

            updateMessage(messageModel, state)

            if (state == MessageState.READ) {
                notificationService.cancel(messageReceiver)
            }
        }
    }

    private fun updateMessage(messageModel: AbstractMessageModel, state: MessageState) {
        if (MessageUtil.isReaction(state)) {
            messageService.addMessageReaction(
                messageModel,
                state,
                // the identity that reacted (this is us => reflected outgoing message)
                myIdentity,
                Date(outgoingMessage.createdAt),
            )
        } else {
            when (state) {
                MessageState.DELIVERED -> {
                    val date = Date(outgoingMessage.createdAt)
                    // The delivered at date is stored in created at for incoming messages
                    messageModel.createdAt = date
                    messageModel.modifiedAt = date
                    messageService.save(messageModel)
                    ListenerManager.messageListeners.handle { l -> l.onModified(listOf(messageModel)) }
                }

                MessageState.READ -> {
                    val date = Date(outgoingMessage.createdAt)
                    messageModel.readAt = date
                    messageModel.modifiedAt = date
                    messageModel.isRead = true
                    messageService.save(messageModel)
                    ListenerManager.messageListeners.handle { l -> l.onModified(listOf(messageModel)) }
                }

                else -> logger.error("Unsupported delivery receipt reflected of state {}", state)
            }
        }
    }
}
