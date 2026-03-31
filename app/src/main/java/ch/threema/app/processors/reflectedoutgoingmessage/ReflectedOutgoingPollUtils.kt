package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.utils.BallotUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.ballot.BallotData
import ch.threema.domain.protocol.csp.messages.ballot.BallotDataChoice
import ch.threema.domain.protocol.csp.messages.ballot.BallotId
import ch.threema.domain.protocol.csp.messages.ballot.BallotSetupInterface
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.ballot.BallotChoiceModel
import ch.threema.storage.models.ballot.BallotModel

private val logger = getThreemaLogger("ReflectedOutgoingPollUtils")

fun handleReflectedOutgoingPoll(
    pollSetupMessage: BallotSetupInterface,
    messageId: MessageId,
    messageReceiver: MessageReceiver<*>,
    ballotService: BallotService,
) {
    val ballotId = pollSetupMessage.ballotId ?: run {
        logger.warn("Received poll setup message without id")
        return
    }

    val ballotData = pollSetupMessage.ballotData ?: run {
        logger.warn("Received poll setup message without data")
        return
    }

    when (ballotData.state) {
        BallotData.State.OPEN -> handleReflectedOutgoingOpenPoll(
            ballotId,
            ballotData,
            messageId,
            messageReceiver,
        )

        BallotData.State.CLOSED -> handleReflectedOutgoingClosedPoll(
            ballotId,
            pollSetupMessage.ballotCreatorIdentity,
            ballotService,
            messageId,
        )

        null -> logger.warn("Received poll setup message where state is null")
    }
}

private fun handleReflectedOutgoingOpenPoll(
    ballotId: BallotId,
    ballotData: BallotData,
    messageId: MessageId,
    messageReceiver: MessageReceiver<*>,
) {
    BallotUtil.createBallot(
        messageReceiver,
        ballotData.description,
        ballotData.type.toModelType(),
        ballotData.assessmentType.toModelType(),
        ballotData.choiceList.map(BallotDataChoice::toBallotChoiceModel),
        ballotId,
        messageId,
        TriggerSource.SYNC,
    ) ?: run {
        logger.error("Ballot model is null")
        return
    }
}

private fun handleReflectedOutgoingClosedPoll(
    ballotId: BallotId,
    ballotCreatorIdentity: IdentityString?,
    ballotService: BallotService,
    messageId: MessageId,
) {
    val ballotModel = ballotService[ballotId.toString(), ballotCreatorIdentity] ?: run {
        logger.error(
            "Ballot model not found for id {} and creator {}",
            ballotId,
            ballotCreatorIdentity,
        )
        return
    }

    BallotUtil.closeBallot(
        null,
        ballotModel,
        ballotService,
        messageId,
        TriggerSource.SYNC,
    )
}

private fun BallotData.Type.toModelType(): BallotModel.Type = when (this) {
    BallotData.Type.RESULT_ON_CLOSE -> BallotModel.Type.RESULT_ON_CLOSE
    BallotData.Type.INTERMEDIATE -> BallotModel.Type.INTERMEDIATE
}

private fun BallotData.AssessmentType.toModelType(): BallotModel.Assessment = when (this) {
    BallotData.AssessmentType.SINGLE -> BallotModel.Assessment.SINGLE_CHOICE
    BallotData.AssessmentType.MULTIPLE -> BallotModel.Assessment.MULTIPLE_CHOICE
}

private fun BallotDataChoice.toBallotChoiceModel(): BallotChoiceModel = BallotChoiceModel()
    .setName(this.name)
    .setOrder(this.order)
    .setVoteCount(this.totalVotes)
    .setApiBallotChoiceId(this.id)
