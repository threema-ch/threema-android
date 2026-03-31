package ch.threema.app.tasks

import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.ballot.BallotId
import ch.threema.domain.protocol.csp.messages.ballot.BallotVote
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingPollVoteContactMessageTask(
    private val messageId: MessageId,
    private val ballotId: BallotId,
    private val ballotCreator: String,
    private val ballotVotes: Array<BallotVote>,
    private val toIdentity: IdentityString,
) : OutgoingCspMessageTask() {
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
        private val toIdentity: IdentityString,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingPollVoteContactMessageTask(
                messageId = MessageId.fromString(messageId),
                ballotId = BallotId(ballotId),
                ballotCreator = ballotCreator,
                ballotVotes = ballotVotes.map {
                    BallotVote(it.first, it.second)
                }.toTypedArray(),
                toIdentity = toIdentity,
            )
    }
}
