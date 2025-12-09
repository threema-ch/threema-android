/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.processors.reflectedoutgoingmessage

import ch.threema.app.managers.ServiceManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage
import ch.threema.storage.models.ContactModel

private val logger = getThreemaLogger("ReflectedOutgoingDeleteProfilePictureTask")

internal class ReflectedOutgoingDeleteProfilePictureTask(
    outgoingMessage: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask<DeleteProfilePictureMessage>(
    outgoingMessage = outgoingMessage,
    message = DeleteProfilePictureMessage.fromReflected(outgoingMessage),
    type = Common.CspE2eMessageType.CONTACT_DELETE_PROFILE_PICTURE,
    serviceManager = serviceManager,
) {
    override fun processOutgoingMessage() {
        val identity = messageReceiver.contact.identity
        contactModelRepository.getByIdentity(identity)
            ?.setProfilePictureBlobId(ContactModel.NO_PROFILE_PICTURE_BLOB_ID)
            ?: logger.error("Received reflected outgoing message for unknown contact {}", identity)
    }
}
