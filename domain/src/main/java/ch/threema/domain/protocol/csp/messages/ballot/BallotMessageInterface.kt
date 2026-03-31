package ch.threema.domain.protocol.csp.messages.ballot

import ch.threema.domain.types.IdentityString

interface BallotMessageInterface {
    var ballotId: BallotId?

    var ballotCreatorIdentity: IdentityString?
}
