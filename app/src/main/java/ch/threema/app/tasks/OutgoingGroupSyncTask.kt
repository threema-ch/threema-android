package ch.threema.app.tasks

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("OutgoingGroupSyncTask")

/**
 * Send a group sync to the given receiver identities. This includes sending a setup message, a
 * name message, and a set-profile-picture or delete-profile-picture message. Note that this task
 * does not update the synchronized-at-timestamp of the group as this task may also be used to send
 * a sync to individual members.
 */
class OutgoingGroupSyncTask(
    private val groupId: GroupId,
    private val creatorIdentity: IdentityString,
    private val receiverIdentities: Set<IdentityString>,
) : OutgoingCspMessageTask() {
    override val type: String = "OutgoingGroupSyncTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        val myIdentity = userService.identity
        if (creatorIdentity != myIdentity) {
            logger.warn("Only the group creator should send a group sync")
            return
        }

        val group = groupService.getByApiGroupIdAndCreator(groupId, creatorIdentity)
        if (group == null) {
            logger.error(
                "Could not find group {} with creator {} to send a group sync",
                groupId,
                creatorIdentity,
            )
            return
        }

        // Send a group setup message (run task immediately)
        OutgoingGroupSetupTask(
            groupId,
            creatorIdentity,
            groupService.getGroupMemberIdentities(group).toSet(),
            receiverIdentities,
            null,
        ).invoke(handle)

        // Send a group name message (run task immediately)
        OutgoingGroupNameTask(
            groupId,
            creatorIdentity,
            group.name ?: "",
            receiverIdentities,
            null,
        ).invoke(handle)

        // Send a profile picture (delete) message (run task immediately)
        OutgoingGroupProfilePictureTask(
            groupId,
            creatorIdentity,
            receiverIdentities,
            null,
        ).invoke(handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupSyncData(
        groupId = groupId.groupId,
        creatorIdentity = creatorIdentity,
        receiverIdentities = receiverIdentities,
    )

    @Serializable
    private class OutgoingGroupSyncData(
        private val groupId: ByteArray,
        private val creatorIdentity: IdentityString,
        private val receiverIdentities: Set<IdentityString>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupSyncTask(
                groupId = GroupId(groupId),
                creatorIdentity = creatorIdentity,
                receiverIdentities = receiverIdentities,
            )
    }
}
