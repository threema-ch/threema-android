/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.tasks

import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.utils.ContactUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.storage.models.ContactModel
import kotlinx.serialization.Serializable
import java.util.Arrays

private val logger = LoggingUtil.getThreemaLogger("ProfilePictureDistributionTask")

/**
 * This task runs the profile distribution
 */
class ProfilePictureDistributionTask(
    private val toIdentity: String,
    serviceManager: ServiceManager,
) : OutgoingProfilePictureTask(serviceManager) {

    override val type: String = "ProfilePictureDistributionTask"

    override suspend fun runSendingSteps(handle: ActiveTaskCodec) {
        // Step 1 is already done as this task is only scheduled for messages that allow user
        // profile distribution.

        val prefix = "Profile picture distribution"

        val contactModel = contactService.getByIdentity(toIdentity)
        if (contactModel == null) {
            logger.warn("{}: Contact model not found", prefix)
            return
        }

        // Step 2: Abort if the contact's id is ECHOECHO or a Gateway ID
        if (ContactUtil.isEchoEchoOrGatewayContact(contactModel)) {
            logger.info(
                "{}: Contact {} should not receive the profile picture",
                prefix,
                toIdentity
            )
            return
        }

        // If the contact has been restored, send a photo request message and mark contact as not
        // restored if successful. Don't do this for ECHOECHO or channel IDs.
        if (contactModel.isRestored) {
            sendRequestProfilePictureMessage(toIdentity, handle)
            contactModel.setIsRestored(false)
            contactService.save(contactModel)
        }

        // Step 3: Abort if the contact should not receive the profile picture according to settings
        if (!contactService.isContactAllowedToReceiveProfilePicture(contactModel)) {
            logger.info(
                "{}: Contact {} is not allowed to receive the profile picture",
                prefix,
                toIdentity
            )
            return
        }

        // Step 4: Upload profile picture to blob server if no valid cached blob id exists
        val data: ProfilePictureUploadData = contactService.updatedProfilePictureUploadData
        if (data.blobId == null) {
            logger.warn("{}: Blob ID is null; abort", prefix)
            return
        }

        // Step 5: If the currently cached blob ID equals the blob ID that was most recently
        // distributed to the contact, abort these steps
        if (Arrays.equals(data.blobId, contactModel.profilePicBlobID)) {
            logger.debug(
                "{}: Contact {} already has the latest profile picture",
                prefix,
                toIdentity
            )
            return
        }

        // Step 6: Send a set-profile-picture message to the contact using the cached blob ID
        if (!data.blobId.contentEquals(ContactModel.NO_PROFILE_PICTURE_BLOB_ID)) {
            sendSetProfilePictureMessage(data, toIdentity, handle)
            logger.info("{}: Profile picture successfully sent to {}", prefix, toIdentity)
        } else {
            sendDeleteProfilePictureMessage(toIdentity, handle)
            logger.info("{}: Profile picture deletion successfully sent to {}", prefix, toIdentity)
        }

        // Step 7: Store the cached blob ID as the most recently used blob ID for this contact
        contactModel.profilePicBlobID = data.blobId
        contactService.save(contactModel)
    }

    override fun serialize(): SerializableTaskData = ProfilePictureDistributionTaskData(toIdentity)

    @Serializable
    data class ProfilePictureDistributionTaskData(
        private val toIdentity: String
    ) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            ProfilePictureDistributionTask(toIdentity, serviceManager)
    }
}
