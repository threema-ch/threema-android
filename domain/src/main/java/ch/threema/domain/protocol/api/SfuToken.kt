package ch.threema.domain.protocol.api

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.net.MalformedURLException
import java.net.URL
import java.util.*

private val logger = getThreemaLogger("SfuToken")

data class SfuToken(
    val sfuBaseUrl: String,
    val allowedSfuHostnameSuffixes: Set<String>,
    val sfuToken: String,
    val expirationDate: Date,
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
        } catch (exception: MalformedURLException) {
            logger.warn("Could not extract hostname from url \"{}\"", url)
            null
        }
    }

    override fun toString() =
        """SfuToken(sfuBaseUrl='$sfuBaseUrl', allowedSfuHostnameSuffixes=$allowedSfuHostnameSuffixes, """ +
            """sfuToken='********', expirationDate=$expirationDate)"""
}
