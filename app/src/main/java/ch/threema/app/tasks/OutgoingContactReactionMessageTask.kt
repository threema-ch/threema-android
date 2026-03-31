package ch.threema.app.tasks

import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.ReactionMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.Reaction.ActionCase
import com.google.protobuf.ByteString
import java.util.Date
import kotlinx.serialization.Serializable

class OutgoingContactReactionMessageTask(
    private val toIdentity: IdentityString,
    private val messageModelId: Int,
    private val messageId: MessageId,
    private val actionCase: ActionCase,
    private val emojiSequence: String,
    private val createdAt: Date,
) : OutgoingCspMessageTask() {
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
        toIdentity = toIdentity,
        messageModelId = messageModelId,
        messageId = messageId.messageId,
        actionCase = actionCase,
        emojiSequence = emojiSequence,
        createdAt = createdAt.time,
    )

    @Serializable
    class OutgoingContactReactionMessageData(
        private val toIdentity: IdentityString,
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val actionCase: ActionCase,
        private val emojiSequence: String,
        private val createdAt: Long,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingContactReactionMessageTask(
                toIdentity = toIdentity,
                messageModelId = messageModelId,
                messageId = MessageId(messageId),
                actionCase = actionCase,
                emojiSequence = emojiSequence,
                createdAt = Date(createdAt),
            )
    }
}
