package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.Identity
import kotlinx.serialization.Serializable

class OutgoingGroupSetupTask(
    override val groupId: GroupId,
    override val creatorIdentity: Identity,
    private val memberIdentities: Set<Identity>,
    override val recipientIdentities: Set<Identity>,
    messageId: MessageId?,
    serviceManager: ServiceManager,
) : OutgoingCspGroupControlMessageTask(serviceManager) {
    override val type: String = "OutgoingGroupSetupTask"

    override val messageId = messageId ?: MessageId.random()

    override fun createGroupMessage() = GroupSetupMessage().also {
        it.members = memberIdentities.toTypedArray()
    }

    override fun serialize(): SerializableTaskData =
        OutgoingGroupSetupData(
            groupId.groupId,
            creatorIdentity,
            memberIdentities,
            recipientIdentities,
            messageId.messageId,
        )

    @Serializable
    class OutgoingGroupSetupData(
        private val groupId: ByteArray,
        private val creatorIdentity: Identity,
        private val memberIdentities: Set<Identity>,
        private val receiverIdentities: Set<Identity>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            OutgoingGroupSetupTask(
                GroupId(groupId),
                creatorIdentity,
                memberIdentities,
                receiverIdentities,
                MessageId(messageId),
                serviceManager,
            )
    }
}
