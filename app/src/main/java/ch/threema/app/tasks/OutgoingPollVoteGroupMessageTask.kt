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
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.ballot.BallotId
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollVoteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.storage.models.ballot.BallotModel
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutgoingPollVoteGroupMessageTask")

class OutgoingPollVoteGroupMessageTask(
    private val messageId: MessageId,
    private val recipientIdentities: Set<String>,
    private val ballotId: BallotId,
    private val ballotCreator: String,
    private val ballotVotes: Array<BallotVote>,
    private val ballotType: BallotModel.Type,
    private val apiGroupId: GroupId,
    private val groupCreator: String,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {

    override val type: String = "OutgoingPollVoteGroupMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        if (ballotType == BallotModel.Type.RESULT_ON_CLOSE) {
            sendBallotVote(handle, setOf(ballotCreator))
        } else {
            sendBallotVote(handle, recipientIdentities)
        }
    }

    private suspend fun sendBallotVote(handle: ActiveTaskCodec, recipients: Set<String>) {
        val group = groupService.getByApiGroupIdAndCreator(apiGroupId, groupCreator)

        if (group == null) {
            logger.error(
                "Cannot find group model for id {} with creator {}",
                apiGroupId,
                groupCreator
            )
            return
        }

        sendGroupMessage(
            group,
            recipients,
            null,
            messageId,
            { createMessage() },
            handle
        )
    }

    private fun createMessage() = GroupPollVoteMessage().also {
        it.ballotCreator = ballotCreator
        it.ballotId = ballotId
        it.addVotes(ballotVotes.toList())
    }

    override fun serialize(): SerializableTaskData = OutgoingPollVoteGroupMessageData(
        messageId.toString(),
        recipientIdentities,
        ballotId.ballotId,
        ballotCreator,
        ballotVotes.map { Pair(it.id, it.value) },
        ballotType,
        apiGroupId.toString(),
        groupCreator
    )

    @Serializable
    class OutgoingPollVoteGroupMessageData(
        private val messageId: String,
        private val recipientIdentities: Set<String>,
        private val ballotId: ByteArray,
        private val ballotCreator: String,
        private val ballotVotes: List<Pair<Int, Int>>,
        private val ballotType: BallotModel.Type,
        private val apiGroupId: String,
        private val groupCreator: String,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingPollVoteGroupMessageTask(
                MessageId.fromString(messageId),
                recipientIdentities,
                BallotId(ballotId),
                ballotCreator,
                ballotVotes.map {
                    BallotVote().apply {
                        id = it.first
                        value = it.second
                    }
                }.toTypedArray(),
                ballotType,
                GroupId(apiGroupId),
                groupCreator,
                serviceManager
            )
    }
}
