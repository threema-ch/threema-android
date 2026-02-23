package ch.threema.domain.protocol.csp.messages.ballot

import ch.threema.domain.types.Identity

interface BallotMessageInterface {
    var ballotId: BallotId?

    var ballotCreatorIdentity: Identity?
}
