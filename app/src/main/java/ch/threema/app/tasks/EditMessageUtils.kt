/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.tasks

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.MessageService
import ch.threema.app.utils.canBeEdited
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.EditMessage
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.storage.models.AbstractMessageModel
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("EditMessageUtils")

fun runCommonEditMessageReceiveSteps(
    editMessage: EditMessage,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? {
    return runCommonEditMessageReceiveSteps(
        editMessage,
        editMessage.data.messageId,
        receiver,
        messageService,
    )
}

fun runCommonEditMessageReceiveSteps(
    editMessage: GroupEditMessage,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? {
    return runCommonEditMessageReceiveSteps(
        editMessage,
        editMessage.data.messageId,
        receiver,
        messageService,
    )
}

private fun runCommonEditMessageReceiveSteps(
    editMessage: AbstractMessage,
    messageId: Long,
    receiver: MessageReceiver<*>,
    messageService: MessageService,
): AbstractMessageModel? {
    val apiMessageId = MessageId(messageId).toString()
    val message = messageService.getMessageModelByApiMessageIdAndReceiver(apiMessageId, receiver)

    // 2. "If referred-message is not defined or ..., discard"
    if (message == null) {
        logger.warn("Incoming Edit Message: No message found for id: $apiMessageId")
        return null
    }

    // 2.1 "If referred-message is ... or the sender is not the original sender of referred-message, discard"
    // Note: We only perform this check if the message is inbox
    if (!message.isOutbox && editMessage.fromIdentity != message.identity) {
        logger.warn(
            "Incoming Edit Message: original message's sender ${message.identity} does not equal edited message's sender ${editMessage.fromIdentity}",
        )
        return null
    }

    // 3. "If referred-message is not editable (...), discard"
    if (message.type?.canBeEdited != true) {
        logger.warn("Incoming Edit Message: Message of type {} cannot be edited", message.type)
        return null
    }

    // 4. "Edit referred-message ... and add an indicator to referred-message, informing the user that
    // the message has been edited by the sender at the message's (the EditMessage's) created-at."
    message.editedAt = editMessage.date

    return message
}
