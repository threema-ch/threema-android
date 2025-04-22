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

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType
import ch.threema.domain.protocol.csp.messages.ballot.BallotData
import ch.threema.domain.protocol.csp.messages.ballot.BallotId
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import kotlinx.serialization.Serializable

class OutgoingPollSetupMessageTask(
    private val messageModelId: Int,
    @MessageReceiverType
    private val receiverType: Int,
    private val recipientIdentities: Set<String>,
    private val ballotId: BallotId,
    private val ballotData: BallotData,
    private val serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingPollSetupMessageTask"

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

        // Create the message
        val message = PollSetupMessage().also {
            it.ballotCreatorIdentity = serviceManager.identityStore.identity
            it.ballotId = ballotId
            it.ballotData = ballotData
        }

        sendContactMessage(
            message,
            messageModel,
            messageModel.identity!!,
            ensureMessageId(messageModel),
            messageModel.createdAt!!,
            handle,
        )
    }

    private suspend fun sendGroupMessage(handle: ActiveTaskCodec) {
        val messageModel = getGroupMessageModel(messageModelId) ?: return

        val group = serviceManager.groupService.getById(messageModel.groupId)
            ?: throw IllegalStateException("Could not get group for message model ${messageModel.apiMessageId}")

        sendGroupMessage(
            group,
            recipientIdentities,
            messageModel,
            messageModel.createdAt!!,
            ensureMessageId(messageModel),
            {
                GroupPollSetupMessage().also {
                    it.ballotCreatorIdentity = serviceManager.identityStore.identity
                    it.ballotId = ballotId
                    it.ballotData = ballotData
                }
            },
            handle,
        )
    }

    override fun serialize(): SerializableTaskData = OutgoingPollSetupMessageData(
        messageModelId,
        receiverType,
        recipientIdentities,
        ballotId.ballotId,
        ballotData.generateString(),
    )

    @Serializable
    class OutgoingPollSetupMessageData(
        private val messageModelId: Int,
        @MessageReceiverType
        private val receiverType: Int,
        private val recipientIdentities: Set<String>,
        private val ballotId: ByteArray,
        private val ballotData: String,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingPollSetupMessageTask(
                messageModelId,
                receiverType,
                recipientIdentities,
                BallotId(ballotId),
                BallotData.parse(ballotData),
                serviceManager,
            )
    }
}
