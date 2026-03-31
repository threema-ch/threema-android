package ch.threema.app.tasks

import ch.threema.base.ThreemaException
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.protocol.csp.messages.GroupReactionMessage
import ch.threema.domain.protocol.csp.messages.ReactionMessageData
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.Reaction.ActionCase
import com.google.protobuf.ByteString
import java.util.Date
import kotlin.Throws
import kotlinx.serialization.Serializable

class OutgoingGroupReactionMessageTask(
    private val targetMessageModelId: Int,
    private val reactionMessageId: MessageId,
    private val actionCase: ActionCase,
    private val emojiSequence: String,
    private val reactedAt: Date,
    private val recipientIdentities: Set<IdentityString>,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingGroupReactionMessageTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val messageModel = getGroupMessageModel(targetMessageModelId)
            ?: throw ThreemaException("No group message model found for messageModelId=$targetMessageModelId")

        val group = groupService.getById(messageModel.groupId)
            ?: throw ThreemaException("No group model found for groupId=${messageModel.groupId}")

        val targetMessageIdLong = messageModel.messageId!!.messageIdLong

        sendGroupMessage(
            group = group,
            recipients = recipientIdentities,
            messageModel = null,
            createdAt = reactedAt,
            messageId = reactionMessageId,
            createAbstractMessage = {
                createReactionMessage(targetMessageIdLong)
            },
            handle = handle,
        )
    }

    @Throws(ThreemaException::class)
    private fun createReactionMessage(targetMessageId: Long): GroupReactionMessage {
        val reactionMessageData = try {
            ReactionMessageData.forActionCase(
                actionCase = actionCase,
                messageId = targetMessageId,
                emojiSequenceBytes = ByteString.copyFromUtf8(emojiSequence),
            )
        } catch (e: BadMessageException) {
            throw ThreemaException("Failed to create reaction message data", e)
        }
        return GroupReactionMessage(
            payloadData = reactionMessageData,
        )
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupReactionMessageData(
        messageModelId = targetMessageModelId,
        messageId = reactionMessageId.messageId,
        actionCase = actionCase,
        emojiSequence = emojiSequence,
        reactedAt = reactedAt.time,
        recipientIdentities = recipientIdentities,
    )

    @Serializable
    class OutgoingGroupReactionMessageData(
        private val messageModelId: Int,
        private val messageId: ByteArray,
        private val actionCase: ActionCase,
        private val emojiSequence: String,
        private val reactedAt: Long,
        private val recipientIdentities: Set<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupReactionMessageTask(
                targetMessageModelId = messageModelId,
                reactionMessageId = MessageId(messageId),
                actionCase = actionCase,
                emojiSequence = emojiSequence,
                reactedAt = Date(reactedAt),
                recipientIdentities = recipientIdentities,
            )
    }
}
