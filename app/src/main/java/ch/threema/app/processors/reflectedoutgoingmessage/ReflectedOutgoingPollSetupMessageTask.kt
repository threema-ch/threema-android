package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.protobuf.common.CspE2eMessageType
import ch.threema.protobuf.d2d.OutgoingMessage

internal class ReflectedOutgoingPollSetupMessageTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<PollSetupMessage>(
    outgoingMessage = outgoingMessage,
    message = PollSetupMessage.fromReflected(outgoingMessage, serviceManager.identityStore.getIdentityString()!!),
    type = CspE2eMessageType.POLL_SETUP,
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
