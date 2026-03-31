package ch.threema.app.tasks

import ch.threema.app.services.ContactService
import ch.threema.common.now
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.types.IdentityString

/**
 * This class provides methods to send the csp messages set-profile-picture,
 * request-profile-picture, and delete-profile-picture messages.
 */
sealed class OutgoingProfilePictureTask() : OutgoingCspMessageTask(), PersistableTask {
    /**
     * Send request profile picture message to the receiver.
     */
    protected suspend fun sendRequestProfilePictureMessage(
        toIdentity: IdentityString,
        handle: ActiveTaskCodec,
    ) {
        // Create the message
        val message = ContactRequestProfilePictureMessage()

        // Encapsulate and send the message
        sendContactMessage(
            message = message,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = MessageId.random(),
            createdAt = now(),
            handle = handle,
        )
    }

    /**
     * Send a set profile picture message to the receiver.
     *
     * @param data the profile picture upload data
     */
    protected suspend fun sendSetProfilePictureMessage(
        data: ContactService.ProfilePictureUploadData,
        toIdentity: IdentityString,
        handle: ActiveTaskCodec,
    ) {
        // Create the message
        val message = SetProfilePictureMessage(
            blobId = data.blobId,
            size = data.size,
            encryptionKey = data.encryptionKey,
        )

        sendContactMessage(
            message = message,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = MessageId.random(),
            createdAt = now(),
            handle = handle,
        )
    }

    /**
     * Send a delete profile picture message to the receiver.
     */
    protected suspend fun sendDeleteProfilePictureMessage(
        toIdentity: IdentityString,
        handle: ActiveTaskCodec,
    ) {
        // Create the message
        val message = DeleteProfilePictureMessage()

        sendContactMessage(
            message = message,
            messageModel = null,
            toIdentity = toIdentity,
            messageId = MessageId.random(),
            createdAt = now(),
            handle = handle,
        )
    }
}
