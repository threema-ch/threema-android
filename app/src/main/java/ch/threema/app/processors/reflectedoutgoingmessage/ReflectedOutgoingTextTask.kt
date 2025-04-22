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

import androidx.core.util.component1
import androidx.core.util.component2
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.QuoteUtil
import ch.threema.domain.protocol.csp.messages.TextMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType
import java.util.Date

internal class ReflectedOutgoingTextTask(
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask(message, Common.CspE2eMessageType.TEXT, serviceManager) {
    private val messageService by lazy { serviceManager.messageService }

    private val textMessage: TextMessage by lazy { TextMessage.fromByteArray(message.body.toByteArray()) }

    override val storeNonces: Boolean
        get() = textMessage.protectAgainstReplay()

    override val shouldBumpLastUpdate: Boolean = true

    override fun processOutgoingMessage() {
        val messageModel: MessageModel = messageReceiver.createLocalModel(
            MessageType.TEXT,
            MessageContentsType.TEXT,
            Date(message.createdAt),
        )
        initializeMessageModelsCommonFields(messageModel)
        val (body, messageId) = QuoteUtil.getBodyAndQuotedMessageId(textMessage.text)
        messageModel.body = body
        messageModel.quotedMessageId = messageId
        messageModel.state = MessageState.SENT
        messageService.save(messageModel)
        ListenerManager.messageListeners.handle { it.onNew(messageModel) }
    }
}
