/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.domain.protocol.api

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.net.MalformedURLException
import java.net.URL
import java.util.*

private val logger = LoggingUtil.getThreemaLogger("SfuToken")

data class SfuToken(
    val sfuBaseUrl: String,
    val allowedSfuHostnameSuffixes: Set<String>,
    val sfuToken: String,
    val expirationDate: Date
) {
    fun isAllowedBaseUrl(baseUrl: String): Boolean {
        return when (val url = parseUrl(baseUrl)) {
            null -> false
            else -> hasAllowedProtocol(url) && hasAllowedHostnameSuffix(url)
        }
    }

    private fun hasAllowedProtocol(url: URL): Boolean {
        return url.protocol == ProtocolDefines.GC_ALLOWED_BASE_URL_PROTOCOL
    }

    private fun hasAllowedHostnameSuffix(url: URL): Boolean {
        val host = when (url.port) {
            -1 -> url.host
            else -> "${url.host}:${url.port}"
        }
        return allowedSfuHostnameSuffixes.any {
            host.endsWith(it)
        }
    }

    private fun parseUrl(url: String): URL? {
        return try {
            URL(url)
        } catch(exception: MalformedURLException) {
            logger.warn("Could not extract hostname from url \"{}\"", url)
            null
        }
    }

    override fun toString(): String {
        return "SfuToken(sfuBaseUrl='$sfuBaseUrl', allowedSfuHostnameSuffixes=$allowedSfuHostnameSuffixes, sfuToken='********', expirationDate=$expirationDate)"
    }
}
