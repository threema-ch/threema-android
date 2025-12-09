/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.routines

import ch.threema.app.ThreemaApplication
import ch.threema.app.listeners.MessageListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.ConversationService
import ch.threema.app.services.MessageService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.utils.MessageUtil
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.storage.models.AbstractMessageModel

private val logger = getThreemaLogger("MarkAsReadRoutine")

class MarkAsReadRoutine @JvmOverloads constructor(
    private val conversationService: ConversationService? = ThreemaApplication.getServiceManager()?.getConversationService(),
    private val messageService: MessageService,
    private val notificationService: NotificationService,
    private val messageListeners: ListenerManager.TypedListenerManager<MessageListener> = ListenerManager.messageListeners,
) {
    /**
     * Marks all the [messages] as read, as well as the corresponding conversation, by resetting its unread counter
     * and removing the 'unread' tag. If there are notifications for these messages, they are dismissed.
     */
    fun run(
        messages: List<AbstractMessageModel>?,
        messageReceiver: MessageReceiver<*>? = null,
    ): Boolean {
        logger.debug("MarkAsReadRoutine.run()")
        var success = true
        val modifiedMessageModels = mutableListOf<AbstractMessageModel>()

        if (!messages.isNullOrEmpty()) {
            // It is possible that the list gets modified while we're iterating. In that case,
            // we repeat the operation on exception (to avoid the overhead of copying the whole
            // list before we start).
            repeatOnException {
                success = true
                messages.forEach { messageModel ->
                    if (MessageUtil.canMarkAsRead(messageModel)) {
                        try {
                            if (messageService.markAsRead(messageModel, true)) {
                                modifiedMessageModels.add(messageModel)
                            }
                        } catch (e: ThreemaException) {
                            logger.error("Exception", e)
                            success = false
                        }
                    }
                }
            }
        }

        if (messageReceiver != null) {
            conversationService?.markConversationAsRead(messageReceiver)
        } else {
            messages?.firstOrNull()?.let { message ->
                conversationService?.markConversationAsRead(message)
            }
        }

        if (modifiedMessageModels.isNotEmpty()) {
            messageListeners.handle { listener ->
                listener.onModified(modifiedMessageModels)
            }

            val notificationUids = modifiedMessageModels
                .map(AbstractMessageModel::uid)
            notificationService.cancelConversationNotification(*notificationUids.toTypedArray())
        }

        return success
    }

    private fun repeatOnException(maxTries: Int = 10, block: () -> Unit) {
        repeat(maxTries) {
            try {
                block()
                return
            } catch (e: ConcurrentModificationException) {
                logger.error("Failed to mark message as read", e)
            }
        }
    }

    @JvmOverloads
    fun runAsync(
        messages: List<AbstractMessageModel>?,
        messageReceiver: MessageReceiver<*>? = null,
        onSuccess: () -> Unit = {},
    ) {
        Thread {
            val success = run(messages, messageReceiver)
            if (success) {
                onSuccess()
            }
        }.start()
    }
}
