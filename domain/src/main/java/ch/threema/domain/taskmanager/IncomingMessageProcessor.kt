package ch.threema.domain.taskmanager

import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.csp.coders.MessageBox

interface IncomingMessageProcessor {
    /**
     * Process an incoming csp message.
     */
    suspend fun processIncomingCspMessage(messageBox: MessageBox, handle: ActiveTaskCodec)

    /**
     * Process an incoming d2m message.
     */
    suspend fun processIncomingD2mMessage(
        message: InboundD2mMessage.Reflected,
        handle: ActiveTaskCodec,
    )

    /**
     * Process an incoming server alert
     */
    fun processIncomingServerAlert(alertData: CspMessage.ServerAlertData)

    /**
     * Process an incoming server error
     */
    fun processIncomingServerError(errorData: CspMessage.ServerErrorData)
}
