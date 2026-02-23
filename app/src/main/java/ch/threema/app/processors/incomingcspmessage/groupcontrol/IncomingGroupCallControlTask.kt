package ch.threema.app.processors.incomingcspmessage.groupcontrol

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallControlMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingGroupCallControlTask(
    private val groupCallControlMessage: GroupCallControlMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<Nothing?>(null, triggerSource, serviceManager) {
    private val groupCallManager = serviceManager.groupCallManager

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        runCommonGroupReceiveSteps(
            message = groupCallControlMessage as AbstractGroupMessage,
            handle = handle,
            serviceManager = serviceManager,
        ) ?: return ReceiveStepsResult.DISCARD
        return processGroupCallControl()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processGroupCallControl()

    private fun processGroupCallControl() =
        if (groupCallManager.handleControlMessage(groupCallControlMessage)) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
}
