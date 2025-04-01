/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.utils.BallotUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.ballot.BallotData
import ch.threema.domain.protocol.csp.messages.ballot.BallotDataChoice
import ch.threema.domain.protocol.csp.messages.ballot.BallotId
import ch.threema.domain.protocol.csp.messages.ballot.BallotSetupInterface
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.ballot.BallotChoiceModel
import ch.threema.storage.models.ballot.BallotModel

private val logger = LoggingUtil.getThreemaLogger("ReflectedOutgoingPollUtils")

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
    ballotCreatorIdentity: String?,
    ballotService: BallotService,
    messageId: MessageId,
) {
    val ballotModel = ballotService[ballotId.toString(), ballotCreatorIdentity] ?: run {
        logger.error(
            "Ballot model not found for id {} and creator {}",
            ballotId,
            ballotCreatorIdentity
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
