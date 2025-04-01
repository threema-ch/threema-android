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
import ch.threema.app.utils.ConversationNotificationUtil
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
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask(
    message,
    Common.CspE2eMessageType.DELIVERY_RECEIPT,
    serviceManager
) {
    private val messageService by lazy { serviceManager.messageService }
    private val notificationService by lazy { serviceManager.notificationService }
    private val myIdentity by lazy { serviceManager.identityStore.identity }

    private val deliveryReceiptMessage by lazy { DeliveryReceiptMessage.fromReflected(message) }

    override val shouldBumpLastUpdate: Boolean = false

    override val storeNonces: Boolean
        get() = deliveryReceiptMessage.protectAgainstReplay()

    override fun processOutgoingMessage() {
        logger.info("Processing reflected outgoing delivery receipt")

        val deliveryReceiptMessage = DeliveryReceiptMessage.fromReflected(message)
        val state = MessageUtil.receiptTypeToMessageState(deliveryReceiptMessage.receiptType)

        if (state == null) {
            logger.warn("Message {} error: unknown delivery receipt type", message.messageId)
            return
        }

        val identity = message.conversation.contact

        for (messageId in deliveryReceiptMessage.receiptMessageIds) {
            val messageModel = messageService.getContactMessageModel(messageId, identity)
            if (messageModel == null) {
                logger.warn(
                    "Message model ({}) for reflected outgoing delivery receipt is null",
                    messageId
                )
                continue
            }

            updateMessage(messageModel, state)

            if (state == MessageState.READ) {
                cancelNotification(messageModel)
            }
        }
    }

    private fun updateMessage(messageModel: AbstractMessageModel, state: MessageState) {

        if (MessageUtil.isReaction(state)) {
            messageService.addMessageReaction(
                messageModel,
                state,
                myIdentity, // the identity that reacted (this is us => reflected outgoing message)
                Date(message.createdAt)
            )
        } else {
            when (state) {
                MessageState.DELIVERED -> {
                    val date = Date(message.createdAt)
                    // The delivered at date is stored in created at for incoming messages
                    messageModel.createdAt = date
                    messageModel.modifiedAt = date
                    messageService.save(messageModel)
                    ListenerManager.messageListeners.handle { l -> l.onModified(listOf(messageModel)) }
                }

                MessageState.READ -> {
                    val date = Date(message.createdAt)
                    messageModel.readAt = date
                    messageModel.modifiedAt = date
                    messageModel.setRead(true)
                    messageService.save(messageModel)
                    ListenerManager.messageListeners.handle { l -> l.onModified(listOf(messageModel)) }
                }

                else -> logger.error("Unsupported delivery receipt reflected of state {}", state)
            }
        }
    }

    private fun cancelNotification(messageModel: AbstractMessageModel) {
        // Get notification UIDs of the messages that have just been marked as read
        val notificationUid = ConversationNotificationUtil.getUid(messageModel)

        // Cancel notification
        notificationService.cancelConversationNotification(notificationUid)
    }
}
