package ch.threema.app.processors.push

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.utils.PushUtil
import ch.threema.domain.protocol.csp.messages.WebSessionResumeMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingWebSessionResumeMessageTask(
    message: WebSessionResumeMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<WebSessionResumeMessage>(message, triggerSource, serviceManager) {

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        PushUtil.processRemoteMessage(message.getData())
        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult = ReceiveStepsResult.DISCARD
}
