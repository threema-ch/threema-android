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
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.ballot.BallotId
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingPollVoteContactMessageTask(
    private val messageId: MessageId,
    private val ballotId: BallotId,
    private val ballotCreator: String,
    private val ballotVotes: Array<BallotVote>,
    private val toIdentity: String,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingPollVoteContactMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        // Create the message
        val message = PollVoteMessage().also {
            it.ballotCreatorIdentity = ballotCreator
            it.ballotId = ballotId
        }

        // Add all ballot votes
        message.addVotes(ballotVotes.toList())

        // Send the message
        sendContactMessage(message, null, toIdentity, messageId, Date(), handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingPollVoteContactMessageData(
        messageId.toString(),
        ballotId.ballotId,
        ballotCreator,
        ballotVotes.map { Pair(it.id, it.value) },
        toIdentity,
    )

    @Serializable
    class OutgoingPollVoteContactMessageData(
        private val messageId: String,
        private val ballotId: ByteArray,
        private val ballotCreator: String,
        private val ballotVotes: List<Pair<Int, Int>>,
        private val toIdentity: String,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingPollVoteContactMessageTask(
                MessageId.fromString(messageId),
                BallotId(ballotId),
                ballotCreator,
                ballotVotes.map {
                    BallotVote(it.first, it.second)
                }.toTypedArray(),
                toIdentity,
                serviceManager,
            )
    }
}
