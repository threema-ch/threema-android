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

package ch.threema.app.processors.contactcontrol

import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.IncomingCspMessageSubTask
import ch.threema.app.processors.ReceiveStepsResult
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.blob.BlobLoader
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.catchAllExceptNetworkException
import com.neilalexander.jnacl.NaCl

private val logger = LoggingUtil.getThreemaLogger("IncomingSetProfilePictureTask")

class IncomingSetProfilePictureTask(
    private val message: SetProfilePictureMessage,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask(serviceManager) {
    private val contactService by lazy { serviceManager.contactService }
    private val apiService by lazy { serviceManager.apiService }
    private val fileService by lazy { serviceManager.fileService }
    private val avatarCacheService by lazy { serviceManager.avatarCacheService }

    override suspend fun run(handle: ActiveTaskCodec): ReceiveStepsResult {
        val contactModel = contactService.getByIdentity(message.fromIdentity) ?: run {
            logger.warn("Received profile picture from unknown contact")
            return ReceiveStepsResult.DISCARD
        }

        val blobLoader: BlobLoader = this.apiService.createLoader(message.blobId)

        // Download blob and throw exception if there is no blob
        val encryptedBlob = {
            blobLoader.load(false)
        }.catchAllExceptNetworkException {
            logger.error("Could not download profile picture", it)
            // TODO(ANDR-2869): We should act differently depending on the cause of the failure
            throw it
        } ?: throw IllegalStateException("Profile picture blob is null")

        NaCl.symmetricDecryptDataInplace(
            encryptedBlob,
            message.encryptionKey,
            ProtocolDefines.CONTACT_PHOTO_NONCE
        )
        this.fileService.writeContactPhoto(contactModel, encryptedBlob)
        this.avatarCacheService.reset(contactModel)
        ListenerManager.contactListeners.handle { listener: ContactListener ->
            listener.onAvatarChanged(contactModel)
        }

        return ReceiveStepsResult.SUCCESS
    }
}
