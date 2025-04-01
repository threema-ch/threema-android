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

import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.runCommonEditMessageReceiveSteps
import ch.threema.domain.protocol.csp.messages.GroupEditMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.AbstractMessageModel

internal class ReflectedOutgoingGroupEditMessageTask(
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask(
    message,
    Common.CspE2eMessageType.GROUP_EDIT_MESSAGE,
    serviceManager
) {
    private val messageService by lazy { serviceManager.messageService }

    private val groupEditMessage by lazy { GroupEditMessage.fromReflected(message) }

    override val storeNonces: Boolean
        get() = groupEditMessage.protectAgainstReplay()

    override val shouldBumpLastUpdate: Boolean
        get() = groupEditMessage.bumpLastUpdate()

    override fun processOutgoingMessage() {
        check(message.conversation.hasGroup()) { "The message does not have a group identity set" }
        runCommonEditMessageReceiveSteps(
            editMessage = groupEditMessage,
            receiver = messageReceiver,
            messageService = messageService
        )?.let { validEditMessageModel: AbstractMessageModel ->
            messageService.saveEditedMessageText(
                validEditMessageModel,
                groupEditMessage.data.text,
                groupEditMessage.date
            )
        }
    }
}
