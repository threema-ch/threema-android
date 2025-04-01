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
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.internal.closeQuietly
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import okio.source
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.concurrent.Volatile

private val logger: Logger = LoggingUtil.getThreemaLogger("BlobUploader")

private const val PROGRESS_UPDATE_STEP_SIZE = 10

// TODO (ANDR-2869): Rework exception handling (and maybe return types)
/**
 * Helper class that uploads a blob (image, video) to the blob server and returns the assigned blob
 * ID. No processing is done on the data; any encryption must happen separately.
 *
 * It can target both the default blob server and the mirror blob server for multi-device sessions.
 */
class BlobUploader private constructor(
    private val baseOkhttpClient: OkHttpClient,
    private val authToken: String?,
    private val blobInputStream: InputStream,
    private val blobLength: Int,
    private val version: Version,
    private val useMirror: Boolean,
    private val shouldLogHttp: Boolean,
    private val serverAddressProvider: ServerAddressProvider,
    @JvmField var progressListener: ProgressListener?,
    // used for usual blob server request:
    private val useIpv6: Boolean?,
    private val shouldPersist: Boolean?,
    // used for mirror blob server request:
    private val multiDevicePropertyProvider: MultiDevicePropertyProvider?,
    private val blobScope: BlobScope?
) {

    @Volatile
    private var isCancelled = false

    companion object {

        private const val MULTIPART_BOUNDARY = "---------------------------Boundary_Line"

        /**
         * Use this constructor when multi-device is currently **not** active on the device. <br></br>
         * Use `BlobUploader.mirror()` otherwise.
         */
        @JvmStatic
        fun usual(
            baseOkhttpClient: OkHttpClient,
            authToken: String?,
            blobData: ByteArray,
            version: Version,
            shouldLogHttp: Boolean,
            serverAddressProvider: ServerAddressProvider,
            progressListener: ProgressListener?,
            useIpv6: Boolean,
            shouldPersist: Boolean
        ): BlobUploader = BlobUploader(
            baseOkhttpClient = baseOkhttpClient,
            authToken = authToken,
            blobInputStream = ByteArrayInputStream(blobData),
            blobLength = blobData.size,
            version = version,
            useMirror = false,
            shouldLogHttp = shouldLogHttp,
            serverAddressProvider = serverAddressProvider,
            progressListener = progressListener,
            useIpv6 = useIpv6,
            shouldPersist = shouldPersist,
            multiDevicePropertyProvider = null,
            blobScope = null
        )

        /**
         * Use this constructor when multi-device is currently active on the device. <br></br>
         * Use `BlobUploader.usual()` otherwise.
         */
        @JvmStatic
        fun mirror(
            baseOkhttpClient: OkHttpClient,
            authToken: String?,
            blobData: ByteArray,
            version: Version,
            shouldLogHttp: Boolean,
            serverAddressProvider: ServerAddressProvider,
            progressListener: ProgressListener?,
            multiDevicePropertyProvider: MultiDevicePropertyProvider,
            blobScope: BlobScope
        ): BlobUploader = BlobUploader(
            baseOkhttpClient = baseOkhttpClient,
            authToken = authToken,
            blobInputStream = ByteArrayInputStream(blobData),
            blobLength = blobData.size,
            version = version,
            shouldLogHttp = shouldLogHttp,
            useMirror = true,
            serverAddressProvider = serverAddressProvider,
            progressListener = progressListener,
            useIpv6 = null,
            shouldPersist = null,
            multiDevicePropertyProvider = multiDevicePropertyProvider,
            blobScope = blobScope
        )
    }

    /**
     * Upload the given blob and return the blob ID on success.
     *
     * @return blob ID as a byte array or `null` if cancelled via `cancel()`
     */
    @Throws(IOException::class, ThreemaException::class)
    fun upload(): ByteArray? {
        isCancelled = false

        val blobUploadUrl = getBlobUploadUrl()

        val okHttpClientUpload: OkHttpClient = baseOkhttpClient.newBuilder().apply {
            connectTimeout(ProtocolDefines.BLOB_CONNECT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            readTimeout(ProtocolDefines.BLOB_LOAD_TIMEOUT.toLong(), TimeUnit.SECONDS)
            if (shouldLogHttp) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
            }
        }.build()

        val uploadRequest: Request = Request.Builder().apply {
            url(blobUploadUrl)
            addHeader("Content-Type", "multipart/form-data; boundary=$MULTIPART_BOUNDARY")
            addHeader("User-Agent", "${ProtocolStrings.USER_AGENT}/${version.versionString}")
            if (authToken != null) {
                addHeader("Authorization", "Token $authToken")
            }
            post(buildRequestBody())
        }.build()

        logger.info("Uploading blob ({} bytes) in scope {}", blobLength, blobScope)

        try {
            okHttpClientUpload.newCall(uploadRequest).execute().use { response ->
                if (isCancelled) {
                    progressListener?.onFinished(false)
                    return null
                }
                if (!response.isSuccessful) {
                    logger.error("Blob upload failed. HTTP response code not in range 200..299")
                    throw IOException("upload request failed with code ${response.code}")
                }

                val responseBodyStream: InputStream = response.body?.byteStream() ?: run {
                    logger.error("Blob upload failed. Empty successful response body")
                    throw ThreemaException("TB001") // Invalid blob ID received from server
                }

                val blobIdHex = IOUtils.toString(responseBodyStream, StandardCharsets.UTF_8)

                progressListener?.onFinished(blobIdHex != null)

                if (blobIdHex != null) {
                    logger.info("Blob upload completed. ID = $blobIdHex")
                    return Utils.hexStringToByteArray(blobIdHex)
                } else {
                    logger.error("Blob upload failed. Could not read ID from successful response")
                    throw ThreemaException("TB001") // Invalid blob ID received from server
                }
            }
        } catch (ioException: IOException) {
            // Mutable field `isCancelled` will be mutated by calls to progressListener.onFinished
            val isCancelledAtTimeOfException = isCancelled
            progressListener?.onFinished(false)
            // If the "UploadBlobRequestBody" stops writing bytes due to cancellation, it result
            // in an IOException. But only catch it, if we cancelled this uploader on our own
            if (isCancelledAtTimeOfException) {
                logger.info("Blob upload cancelled manually")
                return null
            } else {
                logger.error("Blob upload failed (isCancelled: false)", ioException)
                throw ioException
            }
        } finally {
            blobInputStream.closeQuietly()
        }
    }

    @Throws(ThreemaException::class)
    private fun getBlobUploadUrl(): URL {
        if (useMirror) {
            if (multiDevicePropertyProvider == null) {
                throw ThreemaException("Missing parameter multiDevicePropertyProvider")
            }
            if (blobScope == null) {
                throw ThreemaException("Missing parameter blobScope")
            }
            val blobMirrorServerUploadUrl: String =
                serverAddressProvider.getBlobMirrorServerUploadUrl(multiDevicePropertyProvider)
            return URL(
                appendQueryParametersForMirrorServer(
                    blobMirrorServerUploadUrl,
                    multiDevicePropertyProvider,
                    blobScope
                )
            )
        } else {
            if (useIpv6 == null) {
                throw ThreemaException("Missing parameter useIpv6")
            }
            val blobServerUploadUrl: String = serverAddressProvider.getBlobServerUploadUrl(useIpv6)
            return URL(appendQueryParametersForUsualServer(blobServerUploadUrl))
        }
    }

    /**
     * @param rawUrl An url string **without** any query parameters. The value of this will not be mutated.
     */
    private fun appendQueryParametersForUsualServer(rawUrl: String): String {
        if (shouldPersist != null && shouldPersist) {
            return "$rawUrl?persist=1"
        }
        return rawUrl
    }

    /**
     * @param rawUrl An url string **without** any query parameters. The value of this will not be mutated.
     */
    @Throws(ThreemaException::class)
    private fun appendQueryParametersForMirrorServer(
        rawUrl: String,
        multiDevicePropertyProvider: MultiDevicePropertyProvider,
        scope: BlobScope
    ): String {
        val deviceGroupIdHex: String =
            Utils.byteArrayToHexString(multiDevicePropertyProvider.get().keys.dgid)
                ?: throw ThreemaException("Could not read device group id")
        val deviceIdHex: String =
            multiDevicePropertyProvider.get().mediatorDeviceId.leBytes().toHexString()
        return "$rawUrl?deviceId=${deviceIdHex}&deviceGroupId=${deviceGroupIdHex}&scope=${scope.name}"
    }

    private fun buildRequestBody(): RequestBody {
        val header =
            "--$MULTIPART_BOUNDARY\r\nContent-Disposition: form-data; name=\"blob\"; filename=\"blob.bin\"\r\nContent-Type: application/octet-stream\r\n\r\n"
        val footer = "\r\n--$MULTIPART_BOUNDARY--\r\n"
        return UploadBlobRequestBody(
            blobInputStream = blobInputStream,
            blobLength = blobLength.toLong(),
            bodyHeaderBytes = header.toByteArray(),
            bodyFooterBytes = footer.toByteArray()
        )
    }

    /**
     * Send a cancel signal. If an upload is currently in progress, it will stop and `upload()` will return `null` immediately.
     */
    fun cancel() {
        this.isCancelled = true
    }

    private inner class UploadBlobRequestBody(
        private val blobInputStream: InputStream,
        private val blobLength: Long,
        private val bodyHeaderBytes: ByteArray,
        private val bodyFooterBytes: ByteArray
    ) : RequestBody() {

        override fun contentType(): MediaType =
            ("multipart/form-data; boundary=$MULTIPART_BOUNDARY").toMediaType()

        override fun contentLength(): Long =
            bodyHeaderBytes.size + blobLength + bodyFooterBytes.size

        override fun isOneShot(): Boolean = true

        @Throws(IOException::class)
        override fun writeTo(sink: BufferedSink) {
            if (!isCancelled) {
                sink.write(bodyHeaderBytes)
            }
            blobInputStream.source().use { source ->
                // The rounded progress that is used for triggering the listeners
                var roundedProgress = 0
                // Actual progress
                var progress = 0L
                var read: Long = -1L
                while (
                    !isCancelled &&
                    (source.read(sink.buffer, 2048L).also { read = it }) != -1L
                ) {
                    progress += read
                    sink.flush()
                    // Compute rounded progress in percent
                    val newRoundedProgress = (100 * (progress.toDouble() / blobLength)).toInt()
                    // Only trigger listener if there is an update
                    if (
                        newRoundedProgress - roundedProgress >= PROGRESS_UPDATE_STEP_SIZE ||
                        newRoundedProgress == 100
                    ) {
                        progressListener?.updateProgress(newRoundedProgress)
                        roundedProgress = newRoundedProgress
                    }
                }
            }
            if (!isCancelled) {
                sink.write(bodyFooterBytes)
            }
        }
    }
}
