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
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.LocationDataModel
import ch.threema.storage.models.data.MessageContentsType
import java.util.Date

internal class ReflectedOutgoingGroupLocationTask(
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask(
    message,
    Common.CspE2eMessageType.GROUP_LOCATION,
    serviceManager,
) {
    private val messageService by lazy { serviceManager.messageService }

    private val groupLocationMessage: GroupLocationMessage by lazy {
        GroupLocationMessage.fromReflected(
            message,
        )
    }

    override val storeNonces: Boolean
        get() = groupLocationMessage.protectAgainstReplay()

    override val shouldBumpLastUpdate: Boolean
        get() = groupLocationMessage.bumpLastUpdate()

    override fun processOutgoingMessage() {
        check(message.conversation.hasGroup()) {
            "The message does not have a group identity set"
        }
        val groupMessageModel: GroupMessageModel = messageReceiver.createLocalModel(
            MessageType.LOCATION,
            MessageContentsType.LOCATION,
            Date(message.createdAt),
        )
        initializeMessageModelsCommonFields(groupMessageModel)
        groupMessageModel.locationData = LocationDataModel(
            latitude = groupLocationMessage.latitude,
            longitude = groupLocationMessage.longitude,
            accuracy = groupLocationMessage.accuracy,
            poi = groupLocationMessage.poi,
        )
        groupMessageModel.state = MessageState.SENT
        messageService.save(groupMessageModel)
        ListenerManager.messageListeners.handle { it.onNew(groupMessageModel) }
    }
}
