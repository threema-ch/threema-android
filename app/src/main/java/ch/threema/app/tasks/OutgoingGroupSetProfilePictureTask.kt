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

import android.graphics.Bitmap
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import com.neilalexander.jnacl.NaCl
import java.io.FileNotFoundException
import java.security.SecureRandom

private val logger = LoggingUtil.getThreemaLogger("OutgoingGroupSetProfilePictureTask")

class OutgoingGroupSetProfilePictureTask(
    override val groupId: GroupId,
    override val creatorIdentity: String,
    override val recipientIdentities: Set<String>,
    private val groupPhoto: Bitmap,
    messageId: MessageId?,
    serviceManager: ServiceManager,
): OutgoingCspGroupControlMessageTask(serviceManager) {
    private val apiService by lazy { serviceManager.apiService }
    private val groupPhotoUploadResult by lazy { tryUploadingGroupPhoto(groupPhoto) }

    override val type = "OutgoingGroupSetProfilePictureTask"

    override val messageId = messageId ?: MessageId()

    override fun createGroupMessage(): AbstractGroupMessage {
        val (blobId, encryptionKey, size) = groupPhotoUploadResult

        return GroupSetProfilePictureMessage()
            .also {
                it.blobId = blobId
                it.encryptionKey = encryptionKey
                it.size = size
            }
    }

    override fun serialize() = null

    private fun tryUploadingGroupPhoto(picture: Bitmap): GroupPhotoUploadResult = try {
        // Upload group photo
        uploadGroupPhoto(picture)
    } catch (e: FileNotFoundException) {
        // On onprem builds this exception may occur if the auth token is not valid anymore.
        // Therefore, we invalidate the auth token and retry it once more.
        if (ConfigUtils.isOnPremBuild()) {
            logger.info("Invalidating auth token")
            apiService.invalidateAuthToken()
            logger.info("Retrying upload")
            uploadGroupPhoto(picture)
        } else {
            throw e
        }
    }

    private fun uploadGroupPhoto(picture: Bitmap): GroupPhotoUploadResult {
        val rnd = SecureRandom()
        val encryptionKey = ByteArray(NaCl.SYMMKEYBYTES)
        rnd.nextBytes(encryptionKey)

        val bitmapArray = BitmapUtil.bitmapToJpegByteArray(picture)
        val encryptedData = NaCl.symmetricEncryptData(
            bitmapArray,
            encryptionKey,
            ProtocolDefines.GROUP_PHOTO_NONCE
        )
        val blobUploader = apiService.createUploader(encryptedData)
        val blobId = blobUploader.upload()
        val size = encryptedData.size

        return GroupPhotoUploadResult(blobId, encryptionKey, size)
    }

    private data class GroupPhotoUploadResult(
        val blobId: ByteArray,
        val encryptionKey: ByteArray,
        val size: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GroupPhotoUploadResult

            if (!blobId.contentEquals(other.blobId)) return false
            if (!encryptionKey.contentEquals(other.encryptionKey)) return false
            if (size != other.size) return false

            return true
        }

        override fun hashCode(): Int {
            var result = blobId.contentHashCode()
            result = 31 * result + encryptionKey.contentHashCode()
            result = 31 * result + size
            return result
        }
    }

}
