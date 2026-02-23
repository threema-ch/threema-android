package ch.threema.app.processors.incomingcspmessage.conversation

import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.services.ballot.BallotVoteResult
import ch.threema.domain.protocol.csp.messages.ballot.PollVoteMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource

class IncomingContactPollVoteTask(
    private val pollVoteMessage: PollVoteMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<Nothing?>(null, triggerSource, serviceManager) {
    private val ballotService = serviceManager.ballotService

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult =
        processPollVoteMessage()

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult =
        processPollVoteMessage()

    private fun processPollVoteMessage(): ReceiveStepsResult {
        val ballotVoteResult: BallotVoteResult? = this.ballotService.vote(pollVoteMessage)
        return if (ballotVoteResult?.isSuccess == true) {
            ReceiveStepsResult.SUCCESS
        } else {
            ReceiveStepsResult.DISCARD
        }
    }
}
