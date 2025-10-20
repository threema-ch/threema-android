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

import android.graphics.Bitmap
import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ApiService
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.GroupSetProfilePictureMessage
import ch.threema.domain.types.Identity
import java.io.FileNotFoundException
import java.io.IOException
import java.security.SecureRandom
import kotlinx.serialization.Serializable

private val logger = LoggingUtil.getThreemaLogger("OutgoingGroupSetProfilePictureTask")

class OutgoingGroupSetProfilePictureTask(
    override val groupId: GroupId,
    override val creatorIdentity: Identity,
    override val recipientIdentities: Set<Identity>,
    private val groupPhoto: Bitmap,
    messageId: MessageId?,
    serviceManager: ServiceManager,
) : OutgoingCspGroupControlMessageTask(serviceManager) {
    private val groupPhotoUploadResult by lazy {
        tryUploadingGroupPhoto(groupPhoto, serviceManager.apiService)
    }

    override val type = "OutgoingGroupSetProfilePictureTask"

    override val messageId = messageId ?: MessageId.random()

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
}

/**
 * Upload the group photo. If it fails due to invalid onprem auth token, it is renewed and tried
 * again.
 *
 * @throws FileNotFoundException if upload failed twice
 */
fun tryUploadingGroupPhoto(picture: Bitmap, apiService: ApiService): GroupPhotoUploadResult =
    tryUploadingGroupPhoto(BitmapUtil.bitmapToJpegByteArray(picture), apiService)

/**
 * Upload the group photo. If it fails due to invalid onprem auth token, it is renewed and tried
 * again.
 *
 * @throws FileNotFoundException if upload failed twice
 */
fun tryUploadingGroupPhoto(picture: ByteArray, apiService: ApiService): GroupPhotoUploadResult =
    try {
        // Upload group photo
        uploadGroupPhoto(picture, apiService)
    } catch (e: FileNotFoundException) {
        // On onprem builds this exception may occur if the auth token is not valid anymore.
        // Therefore, we invalidate the auth token and retry it once more.
        if (ConfigUtils.isOnPremBuild()) {
            logger.info("Invalidating auth token")
            apiService.invalidateAuthToken()
            logger.info("Retrying upload")
            uploadGroupPhoto(picture, apiService)
        } else {
            throw e
        }
    }

private fun uploadGroupPhoto(picture: ByteArray, apiService: ApiService): GroupPhotoUploadResult {
    val rnd = SecureRandom()
    val encryptionKey = ByteArray(NaCl.SYMM_KEY_BYTES)
    rnd.nextBytes(encryptionKey)

    val encryptedData = NaCl.symmetricEncryptData(
        data = picture,
        key = encryptionKey,
        nonce = ProtocolDefines.GROUP_PHOTO_NONCE,
    )
    val blobUploader = apiService.createUploader(
        /* data = */
        encryptedData,
        /* shouldPersist = */
        true,
        /* scope = */
        BlobScope.Public,
    )
    val blobId: ByteArray? = blobUploader.upload()
    val size = encryptedData.size

    if (blobId == null) {
        // This should never happen because the blob uploader only returns null when
        // it's explicitly cancelled. If the upload request fails for any other reason,
        // the exception is thrown directly from the uploader
        throw IOException("failed to upload blob")
    }

    return GroupPhotoUploadResult(blobId, encryptionKey, size)
}

@Serializable
data class GroupPhotoUploadResult(
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
