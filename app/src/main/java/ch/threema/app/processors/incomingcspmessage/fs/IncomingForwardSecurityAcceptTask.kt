package ch.threema.app.processors.incomingcspmessage.fs

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.csp.fs.ForwardSecurityDecryptionResult
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataAccept
import ch.threema.domain.taskmanager.ActiveTaskCodec

private val logger = getThreemaLogger("IncomingForwardSecurityAcceptTask")

class IncomingForwardSecurityAcceptTask(
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    private val contact: Contact,
    private val data: ForwardSecurityDataAccept,
) : IncomingForwardSecurityEnvelopeTask {
    override suspend fun run(handle: ActiveTaskCodec): ForwardSecurityDecryptionResult {
        logger.info("Received forward security accept message")
        // TODO(ANDR-2519): Remove when md allows fs
        // Note that we should only send a terminate message when we receive an encapsulated message.
        if (!forwardSecurityMessageProcessor.canForwardSecurityMessageBeProcessed(
                sender = contact,
                sessionId = data.sessionId,
                sendTerminate = false,
                handle = handle,
            )
        ) {
            return ForwardSecurityDecryptionResult.NONE
        }

        forwardSecurityMessageProcessor.processAccept(contact, data, handle)
        return ForwardSecurityDecryptionResult.NONE
    }
}
