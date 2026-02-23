package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D

internal class ReflectedOutgoingPollVoteMessageTask(
    outgoingMessage: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<PollVoteMessage>(
    outgoingMessage = outgoingMessage,
    message = PollVoteMessage.fromReflected(outgoingMessage).apply {
        // This property is used for the ballot service to determine who sent the vote.
        fromIdentity = serviceManager.identityStore.getIdentity()!!
    },
    type = Common.CspE2eMessageType.POLL_VOTE,
    serviceManager = serviceManager,
) {
    private val ballotService by lazy { serviceManager.ballotService }

    override fun processOutgoingMessage() {
        ballotService.vote(message)
    }
}
