package ch.threema.app.tasks

import ch.threema.app.profilepicture.ProfilePicture
import ch.threema.app.profilepicture.RawProfilePicture
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("OutgoingGroupProfilePictureTask")

/**
 * This task sends a set-profile-picture message to the group if there is a group picture. If no
 * group picture is set for the given group, an [OutgoingGroupDeleteProfilePictureTask] is started
 * directly. Note that the messages are only sent to the given [receiverIdentities].
 */
class OutgoingGroupProfilePictureTask(
    private val groupId: GroupId,
    private val creatorIdentity: IdentityString,
    receiverIdentities: Set<IdentityString>,
    messageId: MessageId?,
) : OutgoingCspMessageTask() {
    private val messageId by lazy { messageId ?: MessageId.random() }
    private val receiverIdentities by lazy { receiverIdentities - userService.identity!! }

    override val type: String = "OutgoingGroupProfilePictureTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        if (creatorIdentity != userService.identity) {
            logger.warn("Only the group creator should send the group picture to the members")
            return
        }

        val group = groupModelRepository.getByCreatorIdentityAndId(creatorIdentity, groupId)
        if (group == null) {
            logger.error(
                "Could not find group {} with creator {} to send the profile picture",
                groupId,
                creatorIdentity,
            )
            return
        }

        val groupProfilePicture = fileService.getGroupProfilePictureBytes(group)?.let { bytes -> RawProfilePicture(bytes) }
        if (groupProfilePicture != null) {
            sendGroupPhoto(groupProfilePicture, handle)
        } else {
            sendGroupDeletePhoto(handle)
        }
    }

    private suspend fun sendGroupPhoto(profilePicture: ProfilePicture, handle: ActiveTaskCodec) {
        OutgoingGroupSetProfilePictureTask(
            groupId = groupId,
            creatorIdentity = creatorIdentity,
            recipientIdentities = receiverIdentities,
            profilePicture = profilePicture,
            messageId = null,
        ).invoke(handle)
    }

    private suspend fun sendGroupDeletePhoto(handle: ActiveTaskCodec) {
        OutgoingGroupDeleteProfilePictureTask(
            groupId,
            creatorIdentity,
            receiverIdentities,
            null,
        ).invoke(handle)
    }

    override fun serialize(): SerializableTaskData = OutgoingGroupProfilePictureData(
        groupId.groupId,
        creatorIdentity,
        receiverIdentities,
        messageId.messageId,
    )

    @Serializable
    class OutgoingGroupProfilePictureData(
        private val groupId: ByteArray,
        private val creatorIdentity: IdentityString,
        private val receiverIdentities: Set<IdentityString>,
        private val messageId: ByteArray,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingGroupProfilePictureTask(
                groupId = GroupId(groupId),
                creatorIdentity = creatorIdentity,
                receiverIdentities = receiverIdentities,
                messageId = MessageId(messageId),
            )
    }
}
