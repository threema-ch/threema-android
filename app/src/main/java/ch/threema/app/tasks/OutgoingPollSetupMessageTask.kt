package ch.threema.app.tasks

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver.MessageReceiverType
import ch.threema.domain.protocol.csp.messages.ballot.BallotData
import ch.threema.domain.protocol.csp.messages.ballot.BallotId
import ch.threema.domain.protocol.csp.messages.ballot.GroupPollSetupMessage
import ch.threema.domain.protocol.csp.messages.ballot.PollSetupMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

class OutgoingPollSetupMessageTask(
    private val messageModelId: Int,
    @MessageReceiverType
    private val receiverType: Int,
    private val recipientIdentities: Set<IdentityString>,
    private val ballotId: BallotId,
    private val ballotData: BallotData,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingPollSetupMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        when (receiverType) {
            MessageReceiver.Type_CONTACT -> sendContactMessage(handle)
            MessageReceiver.Type_GROUP -> sendGroupMessage(handle)
            else -> throw IllegalStateException("Invalid message receiver type $receiverType")
        }
    }

    override fun onSendingStepsFailed(e: Exception) {
        getMessageModel(receiverType, messageModelId)?.saveWithStateFailed()
    }

    private suspend fun sendContactMessage(handle: ActiveTaskCodec) {
        val messageModel = getContactMessageModel(messageModelId) ?: return

        // Create the message
        val message = PollSetupMessage().also {
            it.ballotCreatorIdentity = identityStore.getIdentityString()
            it.ballotId = ballotId
            it.ballotData = ballotData
        }

        sendContactMessage(
            message,
            messageModel,
            messageModel.identity!!,
            ensureMessageId(messageModel),
            messageModel.createdAt!!,
            handle,
        )
    }

    private suspend fun sendGroupMessage(handle: ActiveTaskCodec) {
        val messageModel = getGroupMessageModel(messageModelId) ?: return

        val group = groupService.getById(messageModel.groupId)
            ?: throw IllegalStateException("Could not get group for message model ${messageModel.apiMessageId}")

        sendGroupMessage(
            group,
            recipientIdentities,
            messageModel,
            messageModel.createdAt!!,
            ensureMessageId(messageModel),
            {
                GroupPollSetupMessage().also {
                    it.ballotCreatorIdentity = identityStore.getIdentityString()
                    it.ballotId = ballotId
                    it.ballotData = ballotData
                }
            },
            handle,
        )
    }

    override fun serialize(): SerializableTaskData = OutgoingPollSetupMessageData(
        messageModelId,
        receiverType,
        recipientIdentities,
        ballotId.ballotId,
        ballotData.generateString(),
    )

    @Serializable
    class OutgoingPollSetupMessageData(
        private val messageModelId: Int,
        @MessageReceiverType
        private val receiverType: Int,
        private val recipientIdentities: Set<IdentityString>,
        private val ballotId: ByteArray,
        private val ballotData: String,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingPollSetupMessageTask(
                messageModelId = messageModelId,
                receiverType = receiverType,
                recipientIdentities = recipientIdentities,
                ballotId = BallotId(ballotId),
                ballotData = BallotData.parse(ballotData),
            )
    }
}
