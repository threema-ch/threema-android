package ch.threema.app.processors

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.storage.models.ServerMessageModel

private val logger = getThreemaLogger("IncomingMessageProcessorImpl")

class IncomingMessageProcessorImpl(private val serviceManager: ServiceManager) :
    IncomingMessageProcessor {
    private val messageService by lazy { serviceManager.messageService }

    override suspend fun processIncomingCspMessage(
        messageBox: MessageBox,
        handle: ActiveTaskCodec,
    ) {
        IncomingMessageTask(messageBox, serviceManager).run(handle)
    }

    override suspend fun processIncomingD2mMessage(
        message: InboundD2mMessage.Reflected,
        handle: ActiveTaskCodec,
    ) {
        IncomingReflectedMessageTask(message, serviceManager).run(handle)
    }

    override fun processIncomingServerAlert(alertData: CspMessage.ServerAlertData) {
        val msg = ServerMessageModel(alertData.message, ServerMessageModel.TYPE_ALERT)
        messageService.saveIncomingServerMessage(msg)
    }

    override fun processIncomingServerError(errorData: CspMessage.ServerErrorData) {
        val errorMessage = errorData.message
        if (errorMessage.contains("Another connection")) {
            // See `MonitoringLayer#handleCloseError(CspContainer)` for more info
            logger.info("Do not display `Another connection` close-error")
        } else {
            val msg = ServerMessageModel(errorMessage, ServerMessageModel.TYPE_ERROR)
            messageService.saveIncomingServerMessage(msg)
        }
    }
}
