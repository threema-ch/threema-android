package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollVoteMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage

internal class ReflectedOutgoingGroupPollVoteMessageTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupPollVoteMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupPollVoteMessage.fromReflected(outgoingMessage).apply {
        // This property is used for the ballot service to determine who sent the vote.
        fromIdentity = serviceManager.identityStore.getIdentityString()!!
    },
    type = CspE2eMessageType.GROUP_POLL_VOTE,
    serviceManager = serviceManager,
) {
    private val ballotService by lazy { serviceManager.ballotService }

    override fun processOutgoingMessage() {
        ballotService.vote(message)
    }
}
