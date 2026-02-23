package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D

internal class ReflectedOutgoingPollSetupMessageTask(
    outgoingMessage: MdD2D.OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<PollSetupMessage>(
    outgoingMessage = outgoingMessage,
    message = PollSetupMessage.fromReflected(outgoingMessage, serviceManager.identityStore.getIdentity()!!),
    type = Common.CspE2eMessageType.POLL_SETUP,
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
