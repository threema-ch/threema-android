/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu

import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.ProtocolStrings
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.SfuToken
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.protobuf.groupcall.SfuHttpRequest
import com.google.protobuf.ByteString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*

private val logger = LoggingUtil.getThreemaLogger("SfuConnectionImpl")

private const val SFU_VERSION = "v1"
private const val SFU_PEEK_PATH_SEGMENT = "peek"
private const val SFU_JOIN_PATH_SEGMENT = "join"

@WorkerThread
internal class SfuConnectionImpl(
    private val apiConnector: APIConnector,
    private val identityStore: IdentityStoreInterface,
    private val version: Version
) : SfuConnection {
    private var cachedSfuToken: SfuToken? = null

    @AnyThread
    override suspend fun obtainSfuToken(forceRefresh: Boolean): SfuToken {
        return withContext(Dispatchers.IO) {
            logger.debug(
                "Obtain sfu token forceRefresh={}, cached={}",
                forceRefresh,
                cachedSfuToken
            )
            cachedSfuToken.let { cachedToken ->
                if (forceRefresh || cachedToken == null || isTokenExpired(cachedToken)) {
                    fetchAndCacheToken()
                } else {
                    cachedToken
                }
            }
        }
    }

    @AnyThread
    override suspend fun peek(
        token: SfuToken,
        sfuBaseUrl: String,
        callId: CallId,
    ): PeekResponse {
        logger.debug("Peek call {}", callId)
        return withContext(Dispatchers.IO) {
            try {
                postPeek(token, sfuBaseUrl, callId)
            } catch (e: Exception) {
                logger.warn("Could not peek call {} with baseUrl='{}'", callId, sfuBaseUrl)
                throw SfuException("Exception during peek", e)
            }
        }
    }

    @AnyThread
    override suspend fun join(
        token: SfuToken,
        sfuBaseUrl: String,
        callDescription: GroupCallDescription,
        dtlsFingerprint: ByteArray,
    ): JoinResponse {
        return withContext(Dispatchers.IO) {
            try {
                postJoin(token, sfuBaseUrl, callDescription, dtlsFingerprint)
            } catch (e: Exception) {
                throw SfuException("Exception during join", e, callDescription)
            }
        }
    }

    @WorkerThread
    private fun postPeek(token: SfuToken, sfuBaseUrl: String, callId: CallId): PeekResponse {
        val url = createURL(sfuBaseUrl, SFU_VERSION, SFU_PEEK_PATH_SEGMENT, callId.hex)
        logger.info("Peeking call {} via URL {}", callId, url)
        val request = SfuHttpRequest.Peek.newBuilder()
            .setCallId(ByteString.copyFrom(callId.bytes))
            .build()
        val byteResponse =
            post(token, url, request.toByteArray(), ProtocolDefines.GC_PEEK_TIMEOUT_MILLIS)
        val body = byteResponse.body?.let { PeekResponseBody.fromSfuResponseBytes(it) }
        logger.info("Peek status for {}: {}", callId, byteResponse.statusCode)
        return PeekResponse(byteResponse.statusCode, body)
    }

    @WorkerThread
    private fun postJoin(
        token: SfuToken,
        sfuBaseUrl: String,
        callDescription: GroupCallDescription,
        dtlsFingerprint: ByteArray
    ): JoinResponse {
        val url =
            createURL(sfuBaseUrl, SFU_VERSION, SFU_JOIN_PATH_SEGMENT, callDescription.callId.hex)
        logger.info("Joining call {} via URL {}", callDescription.callId, url)
        val request = SfuHttpRequest.Join.newBuilder()
            .setProtocolVersion(callDescription.protocolVersion.toInt())
            .setCallId(ByteString.copyFrom(callDescription.callId.bytes))
            .setDtlsFingerprint(ByteString.copyFrom(dtlsFingerprint))
            .build()
        val byteResponse =
            post(token, url, request.toByteArray(), ProtocolDefines.GC_JOIN_TIMEOUT_MILLIS)
        val body = byteResponse.body?.let { JoinResponseBody.fromSfuResponseBytes(it) }
        logger.info("JoinResponse with HTTP-status={}: {}", byteResponse.statusCode, body)
        return JoinResponse(byteResponse.statusCode, body)
    }

    @WorkerThread
    private fun post(
        token: SfuToken,
        url: URL,
        body: ByteArray,
        timeoutMillis: Int,
    ): ByteResponse {

        val connection = (url.openConnection() as HttpURLConnection).also {
            it.connectTimeout = timeoutMillis
            it.readTimeout = timeoutMillis
            it.requestMethod = "POST"
            it.setRequestProperty("Content-Type", "application/octet-stream")
            it.setRequestProperty("User-Agent", getUserAgent())
            it.setRequestProperty("Authorization", "ThreemaSfuToken ${token.sfuToken}")
            it.doInput = true
            it.doOutput = true
        }

        try {
            connection.outputStream.use {
                it.write(body)
            }

            val statusCode = connection.responseCode
            val bytes = when (statusCode) {
                HTTP_STATUS_OK -> connection.inputStream.use { it.readBytes() }
                else -> null
            }

            return ByteResponse(statusCode, bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun createURL(baseUrl: String, vararg pathParts: String): URL {
        val builder = Uri.parse(baseUrl).buildUpon()
        pathParts.forEach { builder.appendPath(it) }
        return try {
            URL(builder.build().toString())
        } catch (e: MalformedURLException) {
            throw SfuException("Cannot parse URL", e)
        }
    }

    private fun getUserAgent(): String {
        return "${ProtocolStrings.USER_AGENT}/${version.versionString}"
    }

    @WorkerThread
    private fun fetchAndCacheToken(): SfuToken {
        logger.debug("Fetch sfu token")
        try {
            return apiConnector.obtainSfuToken(identityStore).also {
                logger.debug("Got sfu token: {}", it)
                cachedSfuToken = it
            }
        } catch (e: Exception) {
            throw SfuException("Error obtaining sfu token", e)
        }
    }

    private fun isTokenExpired(token: SfuToken): Boolean {
        return token.expirationDate.before(Date())
    }
}

private class ByteResponse(val statusCode: Int, val body: ByteArray?)
