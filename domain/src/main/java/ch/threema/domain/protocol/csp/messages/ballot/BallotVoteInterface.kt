package ch.threema.domain.protocol.csp.messages.ballot

interface BallotVoteInterface : BallotMessageInterface {
    val votes: List<BallotVote>

    fun addVotes(votes: List<BallotVote>)
}
