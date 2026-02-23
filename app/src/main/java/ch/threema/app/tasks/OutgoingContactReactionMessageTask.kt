package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.ReactionMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import ch.threema.protobuf.csp.e2e.Reaction.ActionCase
import com.google.protobuf.ByteString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingContactReactionMessageTask(
    private val toIdentity: Identity,
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val actionCase: ActionCase,
    private val emojiSequence: String,
    private val createdAt: Date,
    serviceManager: ServiceManager,
) : OutgoingCspMessageTask(serviceManager) {
    override val type: String = "OutgoingContactReactionMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val messageModel = getContactMessageModel(messageModelId)
            ?: throw ThreemaException("No contact message model found for messageModelId=$messageModelId")

        val reactionMessageData = try {
            ReactionMessageData.forActionCase(
                actionCase = actionCase,
                messageId = messageModel.messageId!!.messageIdLong,
                emojiSequenceBytes = ByteString.copyFromUtf8(emojiSequence),
            )
        } catch (e: BadMessageException) {
            throw ThreemaException("Failed to create reaction message data", e)
        }

        val reactionMessage = ReactionMessage(reactionMessageData)

        sendContactMessage(
            message = reactionMessage,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = messageId,
            createdAt = createdAt,
            handle = handle,
        )
    }

    override fun serialize(): SerializableTaskData = OutgoingContactReactionMessageData(
        toIdentity,
        messageModelId,
        messageId.messageId,
        actionCase,
        emojiSequence,
        createdAt.time,
    )

    @Serializable
    class OutgoingContactReactionMessageData(
        private val toIdentity: Identity,
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val actionCase: ActionCase,
        private val emojiSequence: String,
        private val createdAt: Long,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingContactReactionMessageTask(
                toIdentity,
                messageModelId,
                MessageId(messageId),
                actionCase,
                emojiSequence,
                Date(createdAt),
                serviceManager,
            )
    }
}
