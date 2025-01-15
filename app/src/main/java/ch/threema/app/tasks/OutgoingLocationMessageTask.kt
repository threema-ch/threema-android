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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType
import ch.threema.domain.protocol.csp.messages.location.GroupLocationMessage
import ch.threema.domain.protocol.csp.messages.location.LocationMessage
import ch.threema.domain.protocol.csp.messages.location.LocationMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

class OutgoingLocationMessageTask(
    private val messageModelId: Int,
    @MessageReceiverType
    private val receiverType: Int,
    private val recipientIdentities: Set<String>,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {

    override val type: String = "OutgoingLocationMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        when (receiverType) {
            MessageReceiver.Type_CONTACT -> sendContactMessage(handle)
            MessageReceiver.Type_GROUP -> sendGroupMessage(handle)
            else -> throw IllegalStateException("Invalid message receiver type $receiverType")
        }
    }

    override fun onSendingStepsFailed(e: Exception) {
        getMessageModel(receiverType, messageModelId)?.saveWithStateFailed()
    }

    private suspend fun sendContactMessage(handle: ActiveTaskCodec) {
        val messageModel = getContactMessageModel(messageModelId) ?: return

        val locationDataModel = messageModel.locationData

        // Create the message
        val message = LocationMessage(
            LocationMessageData(
                latitude = locationDataModel.latitude,
                longitude = locationDataModel.longitude,
                accuracy = locationDataModel.accuracy,
                poi = locationDataModel.poi
            )
        )

        sendContactMessage(
            message,
            messageModel,
            messageModel.identity,
            ensureMessageId(messageModel),
            messageModel.createdAt,
            handle
        )
    }

    private suspend fun sendGroupMessage(handle: ActiveTaskCodec) {
        val messageModel = getGroupMessageModel(messageModelId) ?: return

        val group = groupService.getById(messageModel.groupId)
            ?: throw IllegalStateException("Could not get group for message model ${messageModel.apiMessageId}")

        val locationDataModel = messageModel.locationData

        sendGroupMessage(
            group = group,
            recipients = recipientIdentities,
            messageModel = messageModel,
            createdAt = messageModel.createdAt,
            messageId = ensureMessageId(messageModel),
            createAbstractMessage = {
                GroupLocationMessage(
                    LocationMessageData(
                        latitude = locationDataModel.latitude,
                        longitude = locationDataModel.longitude,
                        accuracy = locationDataModel.accuracy,
                        poi = locationDataModel.poi
                    )
                )
            },
            handle = handle
        )
    }

    override fun serialize(): SerializableTaskData =
        OutgoingLocationMessageTaskData(messageModelId, receiverType, recipientIdentities)

    @Serializable
    class OutgoingLocationMessageTaskData(
        private val messageModelId: Int,
        @MessageReceiverType
        private val receiverType: Int,
        private val recipientIdentities: Set<String>,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingLocationMessageTask(
                messageModelId,
                receiverType,
                recipientIdentities,
                serviceManager
            )
    }
}
