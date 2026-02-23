package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.Identity
import java.util.Date

class OutgoingGroupCallStartTask(
    override val groupId: GroupId,
    override val creatorIdentity: Identity,
    override val recipientIdentities: Set<Identity>,
    private val protocolVersion: UInt,
    private val gck: ByteArray,
    private val sfuBaseUrl: String,
    createdAt: Date,
    serviceManager: ServiceManager,
) : OutgoingCspGroupControlMessageTask(serviceManager) {
    override val type: String = "OutgoingGroupCallStartTask"

    override val messageId = MessageId.random()

    override val date = createdAt

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        super.runSendingSteps(handle)

        groupService.getByApiGroupIdAndCreator(groupId, creatorIdentity)?.let {
            groupService.bumpLastUpdate(it)
        }
    }

    override fun createGroupMessage() =
        GroupCallStartMessage(GroupCallStartData(protocolVersion, gck, sfuBaseUrl))

    override fun serialize(): SerializableTaskData? = null
}
