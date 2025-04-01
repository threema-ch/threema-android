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
import ch.threema.domain.protocol.csp.messages.location.LocationMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.LocationDataModel
import ch.threema.storage.models.data.MessageContentsType
import java.util.Date

internal class ReflectedOutgoingLocationTask(
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask(
    message,
    Common.CspE2eMessageType.LOCATION,
    serviceManager
) {

    private val messageService by lazy { serviceManager.messageService }

    private val locationMessage: LocationMessage by lazy { LocationMessage.fromReflected(message) }

    override val storeNonces: Boolean
        get() = locationMessage.protectAgainstReplay()

    override val shouldBumpLastUpdate: Boolean
        get() = locationMessage.bumpLastUpdate()

    override fun processOutgoingMessage() {
        val messageModel: MessageModel = messageReceiver.createLocalModel(
            MessageType.LOCATION,
            MessageContentsType.LOCATION,
            Date(message.createdAt)
        )
        initializeMessageModelsCommonFields(messageModel)
        messageModel.locationData = LocationDataModel(
            latitude = locationMessage.latitude,
            longitude = locationMessage.longitude,
            accuracy = locationMessage.accuracy,
            poi = locationMessage.poi
        )
        messageModel.state = MessageState.SENT
        messageService.save(messageModel)
        ListenerManager.messageListeners.handle { it.onNew(messageModel) }
    }
}
