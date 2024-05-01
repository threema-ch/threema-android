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
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.storage.models.ContactModel
import kotlinx.serialization.Serializable
import java.util.Arrays

private val logger = LoggingUtil.getThreemaLogger("SendProfilePictureTask")

/**
 * This task sends out either a set-profile-picture message or a delete-profile-picture message to
 * the given contact. Note that this task does not check when the profile picture has been sent the
 * last time.
 */
class SendProfilePictureTask(private val toIdentity: String, serviceManager: ServiceManager) :
    OutgoingProfilePictureTask(serviceManager) {
    private val contactService = serviceManager.contactService

    override val type: String = "SendProfilePictureTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        val data = contactService.updatedProfilePictureUploadData
        if (data.blobId == null) {
            logger.warn("Blob ID is null; cannot send profile picture")
            return
        }

        return if (Arrays.equals(data.blobId, ContactModel.NO_PROFILE_PICTURE_BLOB_ID)) {
            sendDeleteProfilePictureMessage(toIdentity, handle)
        } else {
            sendSetProfilePictureMessage(data, toIdentity, handle)
        }
    }

    override fun serialize(): SerializableTaskData = SendProfilePictureData(toIdentity)

    @Serializable
    data class SendProfilePictureData(private val toIdentity: String) : SerializableTaskData {
        override fun createTask(serviceManager: ServiceManager): Task<*, TaskCodec> =
            SendProfilePictureTask(toIdentity, serviceManager)
    }
}
