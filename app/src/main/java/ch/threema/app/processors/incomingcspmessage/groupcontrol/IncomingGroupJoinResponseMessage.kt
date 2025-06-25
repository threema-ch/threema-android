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

package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.grouplinks.GroupJoinResponseListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseData
import ch.threema.domain.protocol.csp.messages.group.GroupJoinResponseMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.group.OutgoingGroupJoinRequestModel
import java8.util.Optional

private val logger = LoggingUtil.getThreemaLogger("IncomingGroupJoinResponseMessage")

class IncomingGroupJoinResponseMessage(
    message: GroupJoinResponseMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<GroupJoinResponseMessage>(message, triggerSource, serviceManager) {
    private val outgoingGroupJoinRequestModelFactory =
        serviceManager.databaseService.outgoingGroupJoinRequestModelFactory

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        val responseData: GroupJoinResponseData = message.data
        val token = responseData.token

        val joinRequest: Optional<OutgoingGroupJoinRequestModel> =
            outgoingGroupJoinRequestModelFactory
                .getByInviteToken(token.toString())

        if (joinRequest.isEmpty) {
            logger.info("Group Join Response: Ignore with unknown request")
            return ReceiveStepsResult.DISCARD
        }

        val outgoingGroupJoinRequestModel = joinRequest.get()

        val sender: String = message.fromIdentity
        if (outgoingGroupJoinRequestModel.adminIdentity != sender) {
            logger.info(
                "Group Join Response: Ignore with invalid sender {}",
                sender,
            )
            return ReceiveStepsResult.DISCARD
        }

        val response = responseData.response
        val status: OutgoingGroupJoinRequestModel.Status

        val updatedRequestBuilder =
            OutgoingGroupJoinRequestModel.Builder(outgoingGroupJoinRequestModel)

        status = when (response) {
            is GroupJoinResponseData.Accept -> {
                val groupId = response.groupId
                updatedRequestBuilder.withGroupApiId(GroupId(groupId)).build()
                OutgoingGroupJoinRequestModel.Status.ACCEPTED
            }

            is GroupJoinResponseData.Reject -> OutgoingGroupJoinRequestModel.Status.REJECTED
            is GroupJoinResponseData.GroupFull -> OutgoingGroupJoinRequestModel.Status.GROUP_FULL
            is GroupJoinResponseData.Expired -> OutgoingGroupJoinRequestModel.Status.EXPIRED
            else -> throw IllegalStateException("Invalid response: " + responseData.response)
        }

        updatedRequestBuilder.withResponseStatus(status)

        val updateModel = updatedRequestBuilder.build()
        outgoingGroupJoinRequestModelFactory.update(
            updateModel,
        )

        ListenerManager.groupJoinResponseListener.handle { listener: GroupJoinResponseListener ->
            listener.onReceived(
                updateModel,
                status,
            )
        }

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        // TODO(ANDR-2741): Support group synchronization
        return ReceiveStepsResult.DISCARD
    }
}
