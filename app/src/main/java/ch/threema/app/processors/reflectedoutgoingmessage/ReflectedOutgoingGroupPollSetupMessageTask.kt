package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D

internal class ReflectedOutgoingGroupPollSetupMessageTask(
    outgoingMessage: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingGroupMessageTask<GroupPollSetupMessage>(
    outgoingMessage = outgoingMessage,
    message = GroupPollSetupMessage.fromReflected(outgoingMessage, serviceManager.identityStore.getIdentityString()!!),
    type = Common.CspE2eMessageType.GROUP_POLL_SETUP,
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
