package ch.threema.app.tasks

import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

class OutgoingGroupNameTask(
    override val groupId: GroupId,
    override val creatorIdentity: IdentityString,
    private val groupName: String,
    override val recipientIdentities: Set<IdentityString>,
    messageId: MessageId?,
) : OutgoingCspGroupControlMessageTask() {
    override val type: String = "OutgoingGroupNameTask"

    override val messageId = messageId ?: MessageId.random()

    override fun createGroupMessage() = GroupNameMessage().also { it.groupName = groupName }

    override fun serialize(): SerializableTaskData = OutgoingGroupNameData(
        groupId.groupId,
        creatorIdentity,
        groupName,
        recipientIdentities,
        messageId.messageId,
    )

    @Serializable
    class OutgoingGroupNameData(
        private val groupId: ByteArray,
        private val creatorIdentity: IdentityString,
        private val groupName: String,
        private val receiverIdentities: Set<IdentityString>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupNameTask(
                groupId = GroupId(groupId),
                creatorIdentity = creatorIdentity,
                groupName = groupName,
                recipientIdentities = receiverIdentities,
                messageId = MessageId(messageId),
            )
    }
}
