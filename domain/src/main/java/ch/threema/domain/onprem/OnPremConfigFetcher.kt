package ch.threema.domain.onprem

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.Http
import ch.threema.common.TimeProvider
import ch.threema.common.buildRequest
import ch.threema.common.execute
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.common.minus
import ch.threema.domain.protocol.getUserAgent
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import okhttp3.Credentials
import okhttp3.OkHttpClient

private val logger = getThreemaLogger("OnPremConfigFetcher")

class OnPremConfigFetcher(
    private val pinnedOkHttpClient: OkHttpClient,
    private val unpinnedOkHttpClient: OkHttpClient,
    private val onPremConfigVerifier: OnPremConfigVerifier,
    private val onPremConfigParser: OnPremConfigParser,
    private val onPremConfigStore: OnPremConfigStore,
    private val serverParameters: OnPremServerConfigParameters,
    private val timeProvider: TimeProvider,
) {
    private var inMemoryCache: OnPremConfig? = null

    /**
     * Tracks the time when fetching the OPPF last failed with an authorization error
     */
    private var lastUnauthorized: Instant? = null

    /**
     * Tracks the time when the OPPF was last successfully fetched from the fallback URL
     */
    private var lastFallback: Instant? = null

    /**
     * @return the OnPremConfig from the in-memory cache if it exists and is not expired, false otherwise.
     */
    @AnyThread
    fun getCached(): OnPremConfig? =
        inMemoryCache?.takeIf { cachedConfig ->
            cachedConfig.validUntil > timeProvider.get()
        }

    /**
     * Returns the OnPremConfig from the in-memory cache if it exists and is not expired,
     * otherwise fetches the OPPF from the server and caches it.
     *
     * // TODO(ANDR-4438): Fetching should only happen on a worker/IO thread, but currently we cannot guarantee that
     *
     * @throws ThreemaException if fetching, parsing or validating the OPPF fails
     */
    @Throws(ThreemaException::class)
    fun getOrFetch(): OnPremConfig = synchronized(this) {
        getCached()?.let { cachedConfig ->
            return cachedConfig
        }
        logger.info("Fetching OPPF")
        return fetch(
            okHttpClient = pinnedOkHttpClient,
            oppfUrl = serverParameters.oppfUrl,
            username = serverParameters.username,
            password = serverParameters.password,
        )
    }

    /**
     * Fetches the OPPF from the fallback URL and caches it.
     * This should only be called if a certificate pinning error is detected.
     *
     * @throws ThreemaException if fetching, parsing or validating the OPPF fails
     */
    @Throws(ThreemaException::class)
    @WorkerThread
    fun fetchFallback(): OnPremConfig = try {
        synchronized(this) {
            // If the fallback has been fetched recently, we assume the cache holds an up-to-date value. This way we can avoid
            // accidentally fetching the fallback multiple times in short succession.
            if (lastFallback?.let { timeProvider.get() - it < fallbackDelay } == true) {
                getCached()?.let { cachedConfig ->
                    return cachedConfig
                }
            }

            logger.info("Fetching OPPF from fallback URL")
            val config = fetch(
                okHttpClient = unpinnedOkHttpClient,
                oppfUrl = serverParameters.oppfFallbackUrl,
                username = null,
                password = null,
            )
            lastFallback = timeProvider.get()
            return config
        }
    } finally {
        // After fetching the OPPF (or if fetching fails) we wait for a few seconds before we proceed.
        // This reduces the risk that we might be making too many requests in case of cascading failures.
        Thread.sleep(fallbackDelay.inWholeMilliseconds)
    }

    @Throws(ThreemaException::class)
    private fun fetch(
        okHttpClient: OkHttpClient,
        oppfUrl: String,
        username: String?,
        password: String?,
    ): OnPremConfig {
        lastUnauthorized?.let { lastUnauthorized ->
            if (lastUnauthorized > timeProvider.get() - unauthorizedMinRetryInterval) {
                throw UnauthorizedFetchException("Cannot fetch OnPrem config (check username/password) - retry delayed")
            }
        }

        val request = buildRequest {
            url(oppfUrl)
            header(Http.Header.USER_AGENT, getUserAgent())
            if (username != null && password != null) {
                header(Http.Header.AUTHORIZATION, Credentials.basic(username, password))
            }
        }

        val oppfString = try {
            okHttpClient.execute(request)
                .use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == Http.StatusCode.UNAUTHORIZED || response.code == Http.StatusCode.FORBIDDEN) {
                            lastUnauthorized = timeProvider.get()
                            throw UnauthorizedFetchException("Cannot fetch OnPrem config (check username/password)")
                        }
                        throw ThreemaException("Failed to fetch OnPrem config, unexpected response code ${response.code}")
                    }

                    response.getSuccessBodyOrThrow().string()
                }
        } catch (e: IOException) {
            throw ThreemaException("Failed to fetch OnPrem config", e)
        }

        val oppfJsonObject = onPremConfigVerifier.verify(oppfString)
        val onPremConfig = onPremConfigParser.parse(oppfJsonObject, createdAt = timeProvider.get())
        if (!onPremConfig.isLicenseStillValid()) {
            throw ThreemaException("OnPrem license has expired")
        }
        this.inMemoryCache = onPremConfig
        try {
            onPremConfigStore.store(oppfString)
        } catch (e: IOException) {
            throw ThreemaException("Failed to store OnPrem config in cache", e)
        }

        return onPremConfig
    }

    private fun OnPremConfig.isLicenseStillValid() =
        license.expires > timeProvider.get()

    companion object {
        private val unauthorizedMinRetryInterval = 3.minutes
        private val fallbackDelay = 10.seconds
    }
}
