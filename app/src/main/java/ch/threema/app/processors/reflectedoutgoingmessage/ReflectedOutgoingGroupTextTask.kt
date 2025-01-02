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

package ch.threema.app.processors.reflectedoutgoingmessage

import androidx.core.util.component1
import androidx.core.util.component2
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.QuoteUtil
import ch.threema.domain.protocol.csp.messages.GroupTextMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType
import java.util.Date

internal class ReflectedOutgoingGroupTextTask(
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask(
    message,
    Common.CspE2eMessageType.GROUP_TEXT,
    serviceManager
) {
    private val messageService by lazy { serviceManager.messageService }

    private val groupTextMessage by lazy { GroupTextMessage.fromByteArray(message.body.toByteArray()) }

    override val storeNonces: Boolean
        get() = groupTextMessage.protectAgainstReplay()

    override val shouldBumpLastUpdate: Boolean = true

    override fun processOutgoingMessage() {
        if (!message.conversation.hasGroup()) {
            throw IllegalStateException("The message does not have a group identity set")
        }

        val messageModel = messageReceiver.createLocalModel(
            MessageType.TEXT,
            MessageContentsType.TEXT,
            Date(message.createdAt)
        )
        initializeMessageModelsCommonFields(messageModel)

        val (body, messageId) = QuoteUtil.getBodyAndQuotedMessageId(groupTextMessage.text)
        messageModel.body = body
        messageModel.quotedMessageId = messageId
        messageModel.state = MessageState.SENT
        messageService.save(messageModel)
        ListenerManager.messageListeners.handle { it.onNew(messageModel) }
    }
}
