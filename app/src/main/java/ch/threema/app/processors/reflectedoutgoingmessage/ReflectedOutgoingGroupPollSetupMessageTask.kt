package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage

internal class ReflectedOutgoingGroupPollSetupMessageTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupPollSetupMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupPollSetupMessage.fromReflected(outgoingMessage, serviceManager.identityStore.getIdentityString()!!),
    type = CspE2eMessageType.GROUP_POLL_SETUP,
    serviceManager = serviceManager,
) {
    private val ballotService by lazy { serviceManager.ballotService }

    override fun processOutgoingMessage() {
        handleReflectedOutgoingPoll(
            message,
            message.messageId,
            messageReceiver,
            ballotService,
        )
    }
}
