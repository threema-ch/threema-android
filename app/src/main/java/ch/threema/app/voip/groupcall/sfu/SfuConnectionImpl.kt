package ch.threema.app.voip.groupcall.sfu

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.Http
import ch.threema.common.buildNew
import ch.threema.common.buildRequest
import ch.threema.common.execute
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.SfuToken
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.getUserAgent
import ch.threema.domain.stores.IdentityStore
import ch.threema.protobuf.groupcall.SfuHttpRequest
import com.google.protobuf.kotlin.toByteString
import java.lang.Exception
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

private val logger = getThreemaLogger("SfuConnectionImpl")

private const val SFU_VERSION = "v1"
private const val SFU_PEEK_PATH_SEGMENT = "peek"
private const val SFU_JOIN_PATH_SEGMENT = "join"

@WorkerThread
internal class SfuConnectionImpl
@JvmOverloads
constructor(
    private val apiConnector: APIConnector,
    private val identityStore: IdentityStore,
    private val okHttpClient: OkHttpClient,
    private val version: Version,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider.default,
) : SfuConnection {
    private var cachedSfuToken: SfuToken? = null

    @AnyThread
    override suspend fun obtainSfuToken(forceRefresh: Boolean): SfuToken {
        return withContext(dispatcherProvider.io) {
            logger.debug(
                "Obtain sfu token forceRefresh={}, cached={}",
                forceRefresh,
                cachedSfuToken,
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
        return withContext(dispatcherProvider.io) {
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
        return withContext(dispatcherProvider.io) {
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
            .setCallId(callId.bytes.toByteString())
            .build()
        val byteResponse = post(
            token = token,
            url = url,
            body = request.toByteArray(),
            timeout = ProtocolDefines.GC_PEEK_TIMEOUT_MILLIS.milliseconds,
        )
        val body = byteResponse.body?.let { PeekResponseBody.fromSfuResponseBytes(it) }
        logger.info("Peek status for {}: {}", callId, byteResponse.statusCode)
        return PeekResponse(byteResponse.statusCode, body)
    }

    @WorkerThread
    private fun postJoin(
        token: SfuToken,
        sfuBaseUrl: String,
        callDescription: GroupCallDescription,
        dtlsFingerprint: ByteArray,
    ): JoinResponse {
        val url =
            createURL(sfuBaseUrl, SFU_VERSION, SFU_JOIN_PATH_SEGMENT, callDescription.callId.hex)
        logger.info("Joining call {} via URL {}", callDescription.callId, url)
        val request = SfuHttpRequest.Join.newBuilder()
            .setProtocolVersion(callDescription.protocolVersion.toInt())
            .setCallId(callDescription.callId.bytes.toByteString())
            .setDtlsFingerprint(dtlsFingerprint.toByteString())
            .build()
        val byteResponse = post(
            token = token,
            url = url,
            body = request.toByteArray(),
            timeout = ProtocolDefines.GC_JOIN_TIMEOUT_MILLIS.milliseconds,
        )
        val body = byteResponse.body?.let { JoinResponseBody.fromSfuResponseBytes(it) }
        logger.info("JoinResponse with HTTP-status={}: {}", byteResponse.statusCode, body)
        return JoinResponse(byteResponse.statusCode, body)
    }

    @WorkerThread
    private fun post(
        token: SfuToken,
        url: URL,
        body: ByteArray,
        timeout: Duration,
    ): ByteResponse {
        val request = buildRequest {
            url(url)
            post(body.toRequestBody(contentType = "application/octet-stream".toMediaType()))
            header(Http.Header.USER_AGENT, getUserAgent(version))
            header(Http.Header.AUTHORIZATION, "ThreemaSfuToken ${token.sfuToken}")
        }
        return okHttpClient
            .buildNew {
                connectTimeout(timeout)
                readTimeout(timeout)
            }
            .execute(request)
            .use { response ->
                ByteResponse(
                    statusCode = response.code,
                    body = if (response.code == HTTP_STATUS_OK) {
                        response.getSuccessBodyOrThrow().bytes()
                    } else {
                        null
                    },
                )
            }
    }

    private fun createURL(baseUrl: String, vararg pathParts: String): URL {
        val url = baseUrl.toUri().buildUpon()
            .apply {
                pathParts.forEach { appendPath(it) }
            }
            .build()
            .toString()
        return try {
            URL(url)
        } catch (e: MalformedURLException) {
            throw SfuException("Cannot parse URL", e)
        }
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
