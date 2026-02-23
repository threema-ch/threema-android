package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupDeleteProfilePictureMessage
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import kotlinx.serialization.Serializable

class OutgoingGroupDeleteProfilePictureTask(
    override val groupId: GroupId,
    override val creatorIdentity: Identity,
    override val recipientIdentities: Set<Identity>,
    messageId: MessageId?,
    serviceManager: ServiceManager,
) : OutgoingCspGroupControlMessageTask(serviceManager) {
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
        private val creatorIdentity: Identity,
        private val receiverIdentities: Set<Identity>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupDeleteProfilePictureTask(
                GroupId(groupId),
                creatorIdentity,
                receiverIdentities,
                MessageId(messageId),
                serviceManager,
            )
    }
}
