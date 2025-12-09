/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.profilepicture

import ch.threema.app.services.ApiService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.ThreemaException
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.io.FileNotFoundException
import java.io.IOException
import java.security.SecureRandom

private val logger = getThreemaLogger("GroupProfilePictureUploader")

class GroupProfilePictureUploader(
    private val apiService: ApiService,
    private val secureRandom: SecureRandom,
) {
    /**
     * Upload the group profile picture. If it fails due to invalid onprem auth token, it is renewed and tried
     * again.
     */
    fun tryUploadingGroupProfilePicture(profilePicture: ProfilePicture): GroupProfilePictureUploadResult {
        val uploadGroupProfilePictureResult = uploadGroupProfilePicture(profilePicture)
        if (uploadGroupProfilePictureResult is GroupProfilePictureUploadResult.Failure.OnPremAuthTokenInvalid) {
            // On onprem builds this exception may occur if the auth token is not valid anymore.
            // Therefore, we invalidate the auth token and retry it once more.
            // TODO(ANDR-4201): Remove this explicit check and handle it centrally instead
            apiService.invalidateAuthToken()
            logger.info("Retrying upload")
            return uploadGroupProfilePicture(profilePicture)
        }
        return uploadGroupProfilePictureResult
    }

    private fun uploadGroupProfilePicture(profilePicture: ProfilePicture): GroupProfilePictureUploadResult =
        try {
            val encryptionKey = ByteArray(NaCl.SYMM_KEY_BYTES)
            secureRandom.nextBytes(encryptionKey)

            val encryptedData = NaCl.symmetricEncryptData(
                data = profilePicture.bytes,
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

            if (blobId == null) {
                // This should never happen because the blob uploader only returns null when
                // it's explicitly cancelled. If the upload request fails for any other reason,
                // the exception is thrown directly from the uploader
                throw IOException("failed to upload blob")
            }

            GroupProfilePictureUploadResult.Success(
                profilePicture = profilePicture,
                blobId = blobId,
                encryptionKey = encryptionKey,
                size = encryptedData.size,
            )
        } catch (e: FileNotFoundException) {
            if (ConfigUtils.isOnPremBuild()) {
                logger.info("Auth token invalid")
                GroupProfilePictureUploadResult.Failure.OnPremAuthTokenInvalid
            } else {
                logger.error("Could not upload blob", e)
                GroupProfilePictureUploadResult.Failure.UploadFailed
            }
        } catch (e: IOException) {
            logger.error("Could not upload blob", e)
            GroupProfilePictureUploadResult.Failure.UploadFailed
        } catch (e: ThreemaException) {
            logger.error("Failure while uploading blob", e)
            GroupProfilePictureUploadResult.Failure.UploadFailed
        }

    sealed interface GroupProfilePictureUploadResult {
        data class Success(
            val profilePicture: ProfilePicture,
            val blobId: ByteArray,
            val encryptionKey: ByteArray,
            val size: Int,
        ) : GroupProfilePictureUploadResult {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Success

                if (!profilePicture.bytes.contentEquals(other.profilePicture.bytes)) return false
                if (!blobId.contentEquals(other.blobId)) return false
                if (!encryptionKey.contentEquals(other.encryptionKey)) return false
                if (size != other.size) return false

                return true
            }

            override fun hashCode(): Int {
                var result = profilePicture.hashCode()
                result = 31 * result + blobId.contentHashCode()
                result = 31 * result + encryptionKey.contentHashCode()
                result = 31 * result + size
                return result
            }
        }

        sealed interface Failure : GroupProfilePictureUploadResult {
            object OnPremAuthTokenInvalid : Failure

            object UploadFailed : Failure
        }
    }
}
