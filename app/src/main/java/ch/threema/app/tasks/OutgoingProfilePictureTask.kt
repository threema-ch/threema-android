/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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
import ch.threema.app.services.ContactService
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.ContactRequestProfilePictureMessage
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import java.util.Date

/**
 * This class provides methods to send the csp messages set-profile-picture,
 * request-profile-picture, and delete-profile-picture messages.
 */
sealed class OutgoingProfilePictureTask(serviceManager: ServiceManager) :
    OutgoingCspMessageTask(serviceManager), PersistableTask {
    /**
     * Send request profile picture message to the receiver.
     */
    protected suspend fun sendRequestProfilePictureMessage(
        toIdentity: String,
        handle: ActiveTaskCodec,
    ) {
        // Create the message
        val message = ContactRequestProfilePictureMessage()

        // Encapsulate and send the message
        sendContactMessage(message, null, toIdentity, MessageId(), Date(), handle)
    }

    /**
     * Send a set profile picture message to the receiver.
     *
     * @param data the profile picture upload data
     */
    protected suspend fun sendSetProfilePictureMessage(
        data: ContactService.ProfilePictureUploadData,
        toIdentity: String,
        handle: ActiveTaskCodec,
    ) {
        // Create the message
        val message = SetProfilePictureMessage(
            blobId = data.blobId,
            size = data.size,
            encryptionKey = data.encryptionKey,
        )

        sendContactMessage(message, null, toIdentity, MessageId(), Date(), handle)
    }

    /**
     * Send a delete profile picture message to the receiver.
     */
    protected suspend fun sendDeleteProfilePictureMessage(
        toIdentity: String,
        handle: ActiveTaskCodec,
    ) {
        // Create the message
        val message = DeleteProfilePictureMessage()

        sendContactMessage(message, null, toIdentity, MessageId(), Date(), handle)
    }
}
