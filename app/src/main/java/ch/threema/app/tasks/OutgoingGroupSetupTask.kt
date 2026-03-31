package ch.threema.app.tasks

import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

class OutgoingGroupSetupTask(
    override val groupId: GroupId,
    override val creatorIdentity: IdentityString,
    private val memberIdentities: Set<IdentityString>,
    override val recipientIdentities: Set<IdentityString>,
    messageId: MessageId?,
) : OutgoingCspGroupControlMessageTask() {
    override val type: String = "OutgoingGroupSetupTask"

    override val messageId = messageId ?: MessageId.random()

    override fun createGroupMessage() = GroupSetupMessage().also {
        it.members = memberIdentities.toTypedArray()
    }

    override fun serialize(): SerializableTaskData =
        OutgoingGroupSetupData(
            groupId = groupId.groupId,
            creatorIdentity = creatorIdentity,
            memberIdentities = memberIdentities,
            receiverIdentities = recipientIdentities,
            messageId = messageId.messageId,
        )

    @Serializable
    class OutgoingGroupSetupData(
        private val groupId: ByteArray,
        private val creatorIdentity: IdentityString,
        private val memberIdentities: Set<IdentityString>,
        private val receiverIdentities: Set<IdentityString>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupSetupTask(
                groupId = GroupId(groupId),
                creatorIdentity = creatorIdentity,
                memberIdentities = memberIdentities,
                recipientIdentities = receiverIdentities,
                messageId = MessageId(messageId),
            )
    }
}
