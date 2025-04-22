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

package ch.threema.domain.protocol.api

import ch.threema.domain.protocol.ProtocolStrings
import ch.threema.domain.protocol.SSLSocketFactoryFactory
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.api.APIConnector.HttpConnectionException
import ch.threema.domain.protocol.csp.ProtocolDefines
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import org.apache.commons.io.IOUtils
import org.json.JSONObject

internal class HttpRequester(
    private val sslSocketFactoryFactory: SSLSocketFactoryFactory,
    private val language: String? = null,
) {
    var authenticator: APIAuthenticator? = null

    @Throws(HttpConnectionException::class, IOException::class)
    fun get(urlString: String, version: Version): String {
        val urlConnection = createConnection("GET", urlString, version)
        urlConnection.doOutput = false
        urlConnection.doInput = true
        try {
            return IOUtils.toString(urlConnection.inputStream, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            throw HttpConnectionException(urlConnection.responseCode, e)
        } finally {
            urlConnection.disconnect()
        }
    }

    @Throws(IOException::class)
    fun post(urlString: String, body: JSONObject, version: Version): HttpRequesterResult {
        val urlConnection = createConnection("POST", urlString, version)
        urlConnection.doOutput = true
        urlConnection.doInput = true

        try {
            OutputStreamWriter(urlConnection.outputStream, StandardCharsets.UTF_8).use { outputStreamWriter ->
                outputStreamWriter.write(body.toString())
            }
            return try {
                urlConnection.inputStream.use { inputStream ->
                    HttpRequesterResult.Success(IOUtils.toString(inputStream, StandardCharsets.UTF_8))
                }
            } catch (e: IOException) {
                HttpRequesterResult.Error(responseCode = urlConnection.responseCode)
            }
        } finally {
            urlConnection.disconnect()
        }
    }

    private fun createConnection(method: String, urlString: String, version: Version): HttpURLConnection {
        val url = URL(urlString)
        val urlConnection = url.openConnection() as HttpURLConnection
        if (urlConnection is HttpsURLConnection) {
            urlConnection.sslSocketFactory = sslSocketFactoryFactory.makeFactory(url.host)
        }
        urlConnection.requestMethod = method
        urlConnection.connectTimeout = ProtocolDefines.API_REQUEST_TIMEOUT * 1000
        urlConnection.readTimeout = ProtocolDefines.API_REQUEST_TIMEOUT * 1000
        urlConnection.setRequestProperty("Content-Type", "application/json")
        urlConnection.setRequestProperty("User-Agent", "${ProtocolStrings.USER_AGENT}/${version.versionString}")
        if (language != null) {
            urlConnection.setRequestProperty("Accept-Language", language)
        }
        this.authenticator?.addAuthenticationToConnection(urlConnection)
        return urlConnection
    }
}
