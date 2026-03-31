package ch.threema.app.tasks

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.types.IdentityString
import kotlinx.serialization.Serializable

private val logger = getThreemaLogger("OutgoingContactRequestProfilePictureTask")

/**
 * Sends a request-profile-picture message to the given contact. Note that it is only sent, if the
 * contact has been restored. After sending the profile picture request, the restored flag is
 * cleared from the contact.
 */
class OutgoingContactRequestProfilePictureTask(
    private val toIdentity: IdentityString,
) : OutgoingProfilePictureTask() {
    override val type = "OutgoingContactRequestProfilePictureTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        // Get contact and check that sending a profile picture request is necessary
        val contactModel = contactModelRepository.getByIdentity(toIdentity)
        if (contactModel == null) {
            logger.warn(
                "Contact {} is unknown, even though a profile picture request should be sent",
                toIdentity,
            )
            return
        }

        val contactModelData = contactModel.data
        if (contactModelData == null) {
            logger.warn(
                "Contact model data for identity {} is null, even though a profile picture request should be sent",
                toIdentity,
            )
            return
        }

        if (!contactModelData.isRestored) {
            logger.warn(
                "Contact {} is not restored; sending profile picture request is skipped",
                toIdentity,
            )
            return
        }

        // Send the profile picture request message
        sendRequestProfilePictureMessage(toIdentity, handle)

        contactModel.setIsRestored(false)
    }

    override fun serialize() = OutgoingContactRequestProfilePictureData(toIdentity)

    @Serializable
    data class OutgoingContactRequestProfilePictureData(
        private val toIdentity: IdentityString,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            OutgoingContactRequestProfilePictureTask(toIdentity)
    }
}
