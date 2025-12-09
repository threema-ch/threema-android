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

package ch.threema.app.processors.incomingcspmessage.contactcontrol

import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.ReflectContactSyncUpdateImmediateTask.ReflectContactProfilePicture
import ch.threema.app.utils.ExifInterface
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.contentEquals
import ch.threema.domain.protocol.blob.BlobLoader
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.SetProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.taskmanager.catchAllExceptNetworkException

private val logger = getThreemaLogger("IncomingSetProfilePictureTask")

class IncomingSetProfilePictureTask(
    message: SetProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
) : IncomingCspMessageSubTask<SetProfilePictureMessage>(message, triggerSource, serviceManager) {
    private val apiService by lazy { serviceManager.apiService }
    private val fileService by lazy { serviceManager.fileService }
    private val contactService by lazy { serviceManager.contactService }
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val nonceFactory by lazy { serviceManager.nonceFactory }

    private val identity by lazy { message.fromIdentity }

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        val contactModel = contactModelRepository.getByIdentity(identity) ?: run {
            logger.warn("Received profile picture from unknown contact")
            return ReceiveStepsResult.DISCARD
        }

        contactModel.setIsRestored(false)

        val identity = message.fromIdentity

        val blobLoader: BlobLoader = this.apiService.createLoader(message.blobId)

        // Download blob and throw exception if there is no blob
        val blob: ByteArray = {
            blobLoader.load(
                // since its an incoming message, always use the public scope
                BlobScope.Public,
            )
        }.catchAllExceptNetworkException {
            logger.error("Could not download profile picture", it)
            // TODO(ANDR-2869): We should act differently depending on the cause of the failure
            throw it
        }?.also { encryptedBlob ->
            NaCl.symmetricDecryptDataInPlace(
                encryptedBlob,
                message.encryptionKey,
                ProtocolDefines.CONTACT_PHOTO_NONCE,
            )
        } ?: throw IllegalStateException("Profile picture blob is null")

        // Note that we do reflect the profile picture even if it did not change. This allows the
        // other devices to remove the blob from the blob mirror.
        reflectProfilePicture(handle)

        if (!ExifInterface.isJpegFormat(blob)) {
            logger.warn("Received a profile picture that is not a jpeg")
        }

        if (fileService.getContactDefinedProfilePictureStream(identity)
                .contentEquals(blob)
        ) {
            logger.info("Profile picture did not change")
            return ReceiveStepsResult.SUCCESS
        }

        this.fileService.writeContactDefinedProfilePicture(identity, blob)
        ListenerManager.contactListeners.handle { listener: ContactListener ->
            listener.onAvatarChanged(identity)
        }

        ShortcutUtil.updateShareTargetShortcut(contactService.createReceiver(contactModel))

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        val contactModel = contactModelRepository.getByIdentity(identity) ?: run {
            logger.error("Reflected set profile picture message received from unknown contact")
            return ReceiveStepsResult.DISCARD
        }
        contactModel.setIsRestored(false)
        return ReceiveStepsResult.SUCCESS
    }

    private suspend fun reflectProfilePicture(handle: ActiveTaskCodec) {
        if (multiDeviceManager.isMultiDeviceActive) {
            ReflectContactProfilePicture(
                contactIdentity = identity,
                profilePictureUpdate = ReflectContactProfilePicture.UpdatedProfilePicture(
                    blobId = message.blobId,
                    nonce = ProtocolDefines.CONTACT_PHOTO_NONCE,
                    encryptionKey = message.encryptionKey,
                ),
                contactModelRepository = contactModelRepository,
                multiDeviceManager = multiDeviceManager,
                nonceFactory = nonceFactory,
            ).reflect(handle)
        }
    }
}
