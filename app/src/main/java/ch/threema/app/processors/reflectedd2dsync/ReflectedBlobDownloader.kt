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

package ch.threema.app.processors.reflectedd2dsync

import ch.threema.base.ThreemaException
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.domain.models.AppVersion
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.blob.BlobLoader
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.protobuf.Common.Blob
import com.google.protobuf.kotlin.isNotEmpty
import java.net.SocketTimeoutException
import okhttp3.OkHttpClient
import okio.IOException

class ReflectedBlobDownloader(
    okHttpClient: OkHttpClient,
    version: AppVersion,
    serverAddressProvider: ServerAddressProvider,
    multiDevicePropertiesProvider: MultiDevicePropertyProvider,
    private val symmetricEncryptionService: SymmetricEncryptionService,
    private val blobId: ByteArray,
    private val key: ByteArray,
    private val nonce: ByteArray,
    private val downloadBlobScope: BlobScope,
    private val markAsDoneBlobScope: BlobScope,
) {
    private val blobLoader: BlobLoader = BlobLoader.mirror(
        baseOkHttpClient = okHttpClient,
        blobId = blobId,
        version = version,
        serverAddressProvider = serverAddressProvider,
        progressListener = null,
        multiDevicePropertyProvider = multiDevicePropertiesProvider,
    )

    fun loadAndMarkAsDone(): BlobLoadingResult {
        val encryptedBlob = try {
            blobLoader.load(downloadBlobScope)
        } catch (e: SocketTimeoutException) {
            // TODO(ANDR-2869): Improve blob server faults
            // When catching this exception, we assume that the blob server is currently not available and therefore trigger a connection restart
            return BlobLoadingResult.BlobMirrorNotAvailable(e)
        } catch (e: IOException) {
            // TODO(ANDR-2869): Improve blob server faults
            // When catching this exception, we assume that the blob cannot be found
            return BlobLoadingResult.BlobNotFound
        } catch (e: ThreemaException) {
            // TODO(ANDR-2869): Improve blob server faults
            // Catching a ThreemaException is unexpected and should never happen.
            return BlobLoadingResult.Other(e)
        }

        if (encryptedBlob == null) {
            return BlobLoadingResult.BlobDownloadCancelled
        }

        val blobLoadingResult: BlobLoadingResult = try {
            val decryptedBytes = symmetricEncryptionService.decrypt(
                encryptedBlob,
                key,
                nonce,
            )
            if (decryptedBytes != null) {
                BlobLoadingResult.Success(
                    blobBytes = decryptedBytes,
                    blobSize = encryptedBlob.size,
                )
            } else {
                BlobLoadingResult.DecryptionFailed(null)
            }
        } catch (e: Exception) {
            BlobLoadingResult.DecryptionFailed(e)
        }

        blobLoader.markAsDone(blobId, markAsDoneBlobScope)

        return blobLoadingResult
    }

    sealed interface BlobLoadingResult {
        class Success(val blobBytes: ByteArray, val blobSize: Int) : BlobLoadingResult
        data object BlobNotFound : BlobLoadingResult
        data class DecryptionFailed(val exception: Exception?) : BlobLoadingResult
        data class BlobMirrorNotAvailable(val exception: Exception) : BlobLoadingResult
        data object BlobDownloadCancelled : BlobLoadingResult
        data class Other(val exception: ThreemaException) : BlobLoadingResult
    }
}

fun Blob.loadAndMarkAsDone(
    okHttpClient: OkHttpClient,
    version: AppVersion,
    serverAddressProvider: ServerAddressProvider,
    multiDevicePropertyProvider: MultiDevicePropertyProvider,
    symmetricEncryptionService: SymmetricEncryptionService,
    fallbackNonce: ByteArray,
    downloadBlobScope: BlobScope,
    markAsDoneBlobScope: BlobScope,
): ReflectedBlobDownloader.BlobLoadingResult =
    ReflectedBlobDownloader(
        okHttpClient = okHttpClient,
        version = version,
        serverAddressProvider = serverAddressProvider,
        multiDevicePropertiesProvider = multiDevicePropertyProvider,
        symmetricEncryptionService = symmetricEncryptionService,
        blobId = id.toByteArray(),
        key = key.toByteArray(),
        nonce = if (nonce.isNotEmpty()) nonce.toByteArray() else fallbackNonce,
        downloadBlobScope = downloadBlobScope,
        markAsDoneBlobScope = markAsDoneBlobScope,
    ).loadAndMarkAsDone()
