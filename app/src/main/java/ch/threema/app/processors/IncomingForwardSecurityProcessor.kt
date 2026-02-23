package ch.threema.app.processors

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.fs.IncomingForwardSecurityAcceptTask
import ch.threema.app.processors.incomingcspmessage.fs.IncomingForwardSecurityInitTask
import ch.threema.app.processors.incomingcspmessage.fs.IncomingForwardSecurityMessageTask
import ch.threema.app.processors.incomingcspmessage.fs.IncomingForwardSecurityRejectTask
import ch.threema.app.processors.incomingcspmessage.fs.IncomingForwardSecurityTerminateTask
import ch.threema.base.ThreemaException
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.csp.fs.ForwardSecurityDecryptionResult
import ch.threema.domain.protocol.csp.fs.UnknownMessageTypeException
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataAccept
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataInit
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataMessage
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataReject
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataTerminate
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec

class IncomingForwardSecurityProcessor(
    private val serviceManager: ServiceManager,
) {
    private val fsmp = serviceManager.forwardSecurityMessageProcessor

    /**
     * Process a forward security envelope message by attempting to decapsulate/decrypt it.
     *
     * @param sender Sender contact
     * @param envelopeMessage The envelope with the encapsulated message
     *
     * @return Decapsulated message or null in case of an invalid inner message or a control message
     * that has been consumed and does not need further processing
     */
    @Throws(ThreemaException::class, BadMessageException::class)
    suspend fun processEnvelopeMessage(
        sender: Contact,
        envelopeMessage: ForwardSecurityEnvelopeMessage,
        handle: ActiveTaskCodec,
    ): ForwardSecurityDecryptionResult {
        return when (val data = envelopeMessage.data) {
            is ForwardSecurityDataInit -> IncomingForwardSecurityInitTask(fsmp, sender, data)
            is ForwardSecurityDataAccept -> IncomingForwardSecurityAcceptTask(fsmp, sender, data)
            is ForwardSecurityDataReject -> IncomingForwardSecurityRejectTask(
                sender,
                data,
                serviceManager,
            )

            is ForwardSecurityDataTerminate -> IncomingForwardSecurityTerminateTask(
                fsmp,
                sender,
                data,
            )

            is ForwardSecurityDataMessage -> IncomingForwardSecurityMessageTask(
                fsmp,
                sender,
                envelopeMessage,
            )

            else -> throw UnknownMessageTypeException("Unsupported message type")
        }.run(handle)
    }
}
