package ch.threema.app.processors.incomingcspmessage.fs

import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.csp.fs.ForwardSecurityDecryptionResult
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityEnvelopeMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec

class IncomingForwardSecurityMessageTask(
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    private val contact: Contact,
    private val envelopeMessage: ForwardSecurityEnvelopeMessage,
) : IncomingForwardSecurityEnvelopeTask {
    override suspend fun run(handle: ActiveTaskCodec): ForwardSecurityDecryptionResult {
        // TODO(ANDR-2519): Remove when md allows fs
        if (!forwardSecurityMessageProcessor.canForwardSecurityMessageBeProcessed(
                sender = contact,
                sessionId = envelopeMessage.data.sessionId,
                sendTerminate = true,
                handle = handle,
            )
        ) {
            return ForwardSecurityDecryptionResult.NONE
        }

        return forwardSecurityMessageProcessor.processMessage(contact, envelopeMessage, handle)
    }
}
