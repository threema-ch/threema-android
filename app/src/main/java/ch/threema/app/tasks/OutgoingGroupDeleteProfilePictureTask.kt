package ch.threema.app.tasks

import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

class OutgoingGroupDeleteProfilePictureTask(
    override val groupId: GroupId,
    override val creatorIdentity: IdentityString,
    override val recipientIdentities: Set<IdentityString>,
    messageId: MessageId?,
) : OutgoingCspGroupControlMessageTask() {
    override val type: String = "OutgoingGroupDeleteProfilePictureTask"

    override val messageId = messageId ?: MessageId.random()

    override fun createGroupMessage() = GroupDeleteProfilePictureMessage()

    override fun serialize(): SerializableTaskData = OutgoingGroupDeleteProfilePictureData(
        groupId = groupId.groupId,
        creatorIdentity = creatorIdentity,
        receiverIdentities = recipientIdentities,
        messageId = messageId.messageId,
    )

    @Serializable
    class OutgoingGroupDeleteProfilePictureData(
        private val groupId: ByteArray,
        private val creatorIdentity: IdentityString,
        private val receiverIdentities: Set<IdentityString>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupDeleteProfilePictureTask(
                groupId = GroupId(groupId),
                creatorIdentity = creatorIdentity,
                recipientIdentities = receiverIdentities,
                messageId = MessageId(messageId),
            )
    }
}
