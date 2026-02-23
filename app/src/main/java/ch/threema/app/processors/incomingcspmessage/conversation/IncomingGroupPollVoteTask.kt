package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.processors.incomingcspmessage.groupcontrol.runCommonGroupReceiveSteps
import ch.threema.app.services.ballot.BallotVoteResult
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollVoteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingGroupPollVoteTask(
    private val groupPollVoteMessage: GroupPollVoteMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<Nothing?>(null, triggerSource, serviceManager) {
    private val ballotService = serviceManager.ballotService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        if (runCommonGroupReceiveSteps(groupPollVoteMessage, handle, serviceManager) == null) {
            return ReceiveStepsResult.DISCARD
        }
        return processPollVoteMessage()
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processPollVoteMessage()

    private fun processPollVoteMessage(): ReceiveStepsResult {
        val ballotVoteResult: BallotVoteResult? = ballotService.vote(groupPollVoteMessage)
        return if (ballotVoteResult?.isSuccess == true) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }
}
