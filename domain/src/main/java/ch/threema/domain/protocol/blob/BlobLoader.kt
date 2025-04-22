/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.domain.protocol.blob

import ch.threema.base.ProgressListener
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.base.utils.toHexString
import ch.threema.domain.protocol.ProtocolStrings
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.data.leBytes
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("BlobLoader")

/**
 * Helper class that loads blobs (images, videos etc.) from the blob server given a blob ID. No
 * processing is done on the loaded data; any decryption etc. must be done separately.
 *
 * It can target both the default blob server and the mirror blob server for multi-device sessions.
 *
 * TODO(ANDR-2869): Rework exception handling (and maybe return types)
 */
class BlobLoader private constructor(
    private val baseOkHttpClient: OkHttpClient,
    private val blobId: ByteArray,
    private val version: Version,
    private val useMirror: Boolean,
    private val serverAddressProvider: ServerAddressProvider,
    @JvmField var progressListener: ProgressListener?,
    // used only for non-mirror blob server
    private val useIpv6: Boolean?,
    // used only for mirror blob sever requests:
    private val multiDevicePropertyProvider: MultiDevicePropertyProvider?,
) {
    @Volatile
    private var isCancelled = false

    companion object {
        private const val BUFFER_SIZE = 8192

        @JvmStatic
        fun usual(
            baseOkHttpClient: OkHttpClient,
            blobId: ByteArray,
            version: Version,
            serverAddressProvider: ServerAddressProvider,
            progressListener: ProgressListener?,
            useIpv6: Boolean,
        ) = BlobLoader(
            baseOkHttpClient = baseOkHttpClient,
            blobId = blobId,
            version = version,
            useMirror = false,
            serverAddressProvider = serverAddressProvider,
            progressListener = progressListener,
            useIpv6 = useIpv6,
            multiDevicePropertyProvider = null,
        )

        @JvmStatic
        fun mirror(
            baseOkHttpClient: OkHttpClient,
            blobId: ByteArray,
            version: Version,
            serverAddressProvider: ServerAddressProvider,
            progressListener: ProgressListener?,
            multiDevicePropertyProvider: MultiDevicePropertyProvider,
        ) = BlobLoader(
            baseOkHttpClient = baseOkHttpClient,
            blobId = blobId,
            version = version,
            useMirror = true,
            serverAddressProvider = serverAddressProvider,
            progressListener = progressListener,
            useIpv6 = null,
            multiDevicePropertyProvider = multiDevicePropertyProvider,
        )
    }

    /**
     * Attempt to load the given blob.
     *
     * @param scope Sets the passed scope when downloading the data from the multi-device blob mirror server.
     * Will have no effect if multi-device is not active.
     *
     * @return blob data or null if download was cancelled
     */
    @Throws(IOException::class, ThreemaException::class)
    fun load(scope: BlobScope): ByteArray? {
        isCancelled = false

        val blobResult: BufferedSourceWithLength = requestBlob(scope)

        var read: Int = -1
        val blobData: ByteArray
        val buffer = ByteArray(BUFFER_SIZE)
        val bos = ByteArrayOutputStream()

        if (blobResult.containsContent()) {
            logger.debug("Blob content length is {}", blobResult.length)

            var offset = 0
            while (!isCancelled && (blobResult.source.read(buffer).also { read = it }) != -1) {
                offset += read

                try {
                    bos.write(buffer, 0, read)
                } catch (outOfMemoryError: OutOfMemoryError) {
                    throw IOException("Out of memory on write")
                }
                progressListener?.updateProgress((100f * offset / blobResult.length).toInt())
            }

            if (isCancelled) {
                logger.info("Blob load cancelled")
                progressListener?.onFinished(false)
                return null
            }

            if (offset.toLong() != blobResult.length) {
                progressListener?.onFinished(false)
                throw IOException("Unexpected read size. current: " + offset + ", excepted: " + blobResult.length)
            }

            blobData = bos.toByteArray()
        } else {
            /* Content length is unknown - need to read until EOF */

            logger.debug("Blob content length is unknown")

            progressListener?.noProgressAvailable()

            while (!isCancelled && (blobResult.source.read(buffer).also { read = it }) != -1) {
                bos.write(buffer, 0, read)
            }

            if (isCancelled) {
                logger.info("Blob load cancelled")
                progressListener?.onFinished(false)
                return null
            }

            blobData = bos.toByteArray()
        }

        logger.info("Blob load complete ({} bytes received)", blobData.size)

        progressListener?.onFinished(true)

        return blobData
    }

    /**
     * Hands through exceptions from [getBlobDownloadUrl]
     */
    @Throws(IOException::class, ThreemaException::class)
    private fun requestBlob(scope: BlobScope): BufferedSourceWithLength {
        val blobUrl: URL = getBlobDownloadUrl(blobId, scope)

        val okHttpClientLoad: OkHttpClient = baseOkHttpClient.newBuilder().apply {
            connectTimeout(ProtocolDefines.BLOB_CONNECT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            readTimeout(ProtocolDefines.BLOB_LOAD_TIMEOUT.toLong(), TimeUnit.SECONDS)
        }.build()

        val request: Request = Request.Builder()
            .get()
            .url(blobUrl)
            .addHeader("User-Agent", "${ProtocolStrings.USER_AGENT}/${version.versionString}")
            .build()

        logger.info("Loading blob from {}", blobUrl.host)

        val response: Response = okHttpClientLoad.newCall(request).execute()

        if (!response.isSuccessful) {
            logger.error("Blob download failed. HTTP response code not in range 200..299")
            throw IOException("download request failed with code ${response.code}")
        }

        val responseBody: ResponseBody = response.body ?: run {
            logger.error("Blob download failed. Empty successful response body")
            throw IOException("download request failed because of missing response body")
        }

        return BufferedSourceWithLength(
            responseBody.source(),
            responseBody.contentLength(),
        )
    }

    /**
     *  @param scope Sets the passed scope when marking the blob as done on the multi device blob mirror server.
     *  Will have no effect if multi-device is not active.
     */
    fun markAsDone(blobId: ByteArray, scope: BlobScope) {
        try {
            val blobDoneUrl: URL = getBlobDoneUrl(blobId, scope)

            val request: Request = Request.Builder()
                .post("".toRequestBody(null))
                .url(blobDoneUrl)
                .addHeader("User-Agent", "${ProtocolStrings.USER_AGENT}/${version.versionString}")
                .build()

            val response: Response = baseOkHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                logger.warn("Marking blob as done failed. HTTP response code: {}", response.code)
            }
        } catch (exception: IOException) {
            logger.warn("Marking blob as done failed", exception)
        } catch (exception: ThreemaException) {
            logger.warn("Marking blob as done failed", exception)
        }
    }

    /**
     * Cancel a download in progress. load() will return null.
     */
    fun cancelDownload() {
        isCancelled = true
    }

    @Throws(ThreemaException::class)
    private fun getBlobDownloadUrl(blobId: ByteArray, scope: BlobScope): URL {
        if (useMirror) {
            if (multiDevicePropertyProvider == null) {
                throw ThreemaException("Missing argument")
            }
            val urlWithoutQueryParameters = serverAddressProvider.getBlobMirrorServerDownloadUrl(multiDevicePropertyProvider).get(blobId)
            return URL(
                appendMirrorQueryParameters(
                    urlWithoutQueryParameters,
                    multiDevicePropertyProvider,
                    scope,
                ),
            )
        } else {
            if (useIpv6 == null) {
                throw ThreemaException("Missing argument")
            }
            return URL(serverAddressProvider.getBlobServerDownloadUrl(useIpv6).get(blobId))
        }
    }

    @Throws(ThreemaException::class)
    private fun getBlobDoneUrl(blobId: ByteArray, scope: BlobScope): URL {
        if (useMirror) {
            if (multiDevicePropertyProvider == null) {
                throw ThreemaException("Missing argument")
            }
            val urlWithoutQueryParameters = serverAddressProvider.getBlobMirrorServerDoneUrl(multiDevicePropertyProvider).get(blobId)
            return URL(
                appendMirrorQueryParameters(
                    urlWithoutQueryParameters,
                    multiDevicePropertyProvider,
                    scope,
                ),
            )
        } else {
            if (useIpv6 == null) {
                throw ThreemaException("Missing argument")
            }
            return URL(serverAddressProvider.getBlobServerDoneUrl(useIpv6).get(blobId))
        }
    }

    /**
     * @param rawUrl An url string **without** any query parameters. The value of this will not be mutated.
     */
    @Throws(ThreemaException::class)
    private fun appendMirrorQueryParameters(
        rawUrl: String,
        multiDevicePropertyProvider: MultiDevicePropertyProvider,
        scope: BlobScope,
    ): String {
        val deviceIdHex: String =
            multiDevicePropertyProvider.get().mediatorDeviceId.leBytes().toHexString()
        val deviceGroupIdHex: String =
            Utils.byteArrayToHexString(multiDevicePropertyProvider.get().keys.dgid)
                ?: throw ThreemaException("Could not read device group id")
        return "$rawUrl?deviceId=$deviceIdHex&deviceGroupId=$deviceGroupIdHex&scope=${scope.name}"
    }

    private data class BufferedSourceWithLength(
        val source: BufferedSource,
        val length: Long,
    ) {
        fun containsContent(): Boolean = length != -1L
    }
}
