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
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.protobuf.Common
import ch.threema.protobuf.d2d.MdD2D.OutgoingMessage

private val logger = LoggingUtil.getThreemaLogger("ReflectedOutgoingContactSetProfilePictureTask")

internal class ReflectedOutgoingContactSetProfilePictureTask(
    message: OutgoingMessage,
    serviceManager: ServiceManager,
) : ReflectedOutgoingContactMessageTask(
    message,
    Common.CspE2eMessageType.CONTACT_SET_PROFILE_PICTURE,
    serviceManager,
) {
    private val setProfilePictureMessage by lazy {
        SetProfilePictureMessage.fromReflected(message)
    }

    override val shouldBumpLastUpdate: Boolean = setProfilePictureMessage.bumpLastUpdate()

    override val storeNonces: Boolean = setProfilePictureMessage.protectAgainstReplay()

    override fun processOutgoingMessage() {
        val identity = messageReceiver.contact.identity
        contactModelRepository.getByIdentity(identity)
            ?.setProfilePictureBlobId(setProfilePictureMessage.blobId)
            ?: logger.error("Received reflected outgoing message to unknown contact {}", identity)
    }
}
