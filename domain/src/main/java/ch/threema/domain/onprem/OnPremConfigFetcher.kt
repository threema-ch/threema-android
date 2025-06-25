/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.domain.onprem

import ch.threema.base.ThreemaException
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import ch.threema.domain.protocol.ProtocolStrings
import java.io.IOException
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request

class OnPremConfigFetcher(
    private val okHttpClient: OkHttpClient,
    private val onPremConfigVerifier: OnPremConfigVerifier,
    private val onPremConfigParser: OnPremConfigParser,
    private val serverParameters: OnPremServerConfigParameters,
    private val timeProvider: TimeProvider = TimeProvider.default,
) {
    private var cachedConfig: OnPremConfig? = null
    private var lastUnauthorized: Instant? = null

    @Throws(ThreemaException::class)
    fun fetch(): OnPremConfig = synchronized(this) {
        cachedConfig?.let { cachedConfig ->
            if (cachedConfig.validUntil > timeProvider.get()) {
                return cachedConfig
            }
        }

        lastUnauthorized?.let { lastUnauthorized ->
            if (lastUnauthorized > timeProvider.get() - unauthorizedMinRetryInterval) {
                throw UnauthorizedFetchException("Cannot fetch OnPrem config (check username/password) - retry delayed")
            }
        }

        val requestBuilder = Request.Builder().apply {
            url(serverParameters.url)
            addHeader("User-Agent", ProtocolStrings.USER_AGENT)
            if (serverParameters.username != null && serverParameters.password != null) {
                addHeader("Authorization", Credentials.basic(serverParameters.username, serverParameters.password))
            }
        }

        val oppfString = try {
            val response = okHttpClient.newCall(requestBuilder.build())
                .execute()
            if (!response.isSuccessful) {
                if (response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN) {
                    lastUnauthorized = timeProvider.get()
                }
                throw ThreemaException("Failed to fetch OnPrem config, unexpected response")
            }

            response.body!!.string()
        } catch (e: IOException) {
            throw ThreemaException("Failed to fetch OnPrem config", e)
        }

        val oppfJsonObject = onPremConfigVerifier.verify(oppfString)
        val onPremConfig = onPremConfigParser.parse(oppfJsonObject)
        if (!onPremConfig.isLicenseStillValid()) {
            throw ThreemaException("OnPrem license has expired")
        }
        this.cachedConfig = onPremConfig

        return onPremConfig
    }

    private fun OnPremConfig.isLicenseStillValid() =
        license.expires > timeProvider.get()

    companion object {
        private val unauthorizedMinRetryInterval = 3.minutes
    }
}
