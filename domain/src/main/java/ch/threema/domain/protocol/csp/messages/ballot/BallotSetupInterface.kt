package ch.threema.domain.protocol.csp.messages.ballot

interface BallotSetupInterface : BallotMessageInterface {
    var ballotData: BallotData?
}
