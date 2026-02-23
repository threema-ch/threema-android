package ch.threema.app.processors.incomingcspmessage.fs

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.Contact
import ch.threema.domain.protocol.csp.fs.ForwardSecurityDecryptionResult
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.protocol.csp.messages.fs.ForwardSecurityDataTerminate
import ch.threema.domain.taskmanager.ActiveTaskCodec

private val logger = getThreemaLogger("IncomingForwardSecurityTerminateTask")

class IncomingForwardSecurityTerminateTask(
    private val forwardSecurityMessageProcessor: ForwardSecurityMessageProcessor,
    private val contact: Contact,
    private val data: ForwardSecurityDataTerminate,
) : IncomingForwardSecurityEnvelopeTask {
    override suspend fun run(handle: ActiveTaskCodec): ForwardSecurityDecryptionResult {
        logger.info("Received forward security terminate message")
        // TODO(ANDR-2519): Remove when md allows fs
        // Note that in this case we should not send a terminate if we do not support fs. Sending a
        // terminate could trigger the sender to respond with a terminate again.
        if (!forwardSecurityMessageProcessor.canForwardSecurityMessageBeProcessed(
                sender = contact,
                sessionId = data.sessionId,
                sendTerminate = false,
                handle = handle,
            )
        ) {
            return ForwardSecurityDecryptionResult.NONE
        }

        forwardSecurityMessageProcessor.processTerminate(contact, data)
        return ForwardSecurityDecryptionResult.NONE
    }
}
