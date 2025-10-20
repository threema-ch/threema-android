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

package ch.threema.app.threemasafe

import ch.threema.common.Http
import ch.threema.common.buildNew
import ch.threema.common.buildRequest
import ch.threema.common.execute
import ch.threema.common.getSuccessBodyOrThrow
import ch.threema.common.throwIfNotSuccessful
import ch.threema.common.withUserAgent
import ch.threema.domain.protocol.getUserAgent
import kotlin.time.Duration.Companion.seconds
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONException
import org.json.JSONObject

class ThreemaSafeApiClient(
    okHttpClient: OkHttpClient,
) {
    private val okHttpClient = okHttpClient.buildNew {
        connectTimeout(15.seconds)
        readTimeout(30.seconds)
        withUserAgent(getUserAgent())
    }

    @Throws(IOException::class, JSONException::class)
    fun testServer(serverInfo: ThreemaSafeServerInfo, id: ThreemaSafeBackupId?): ThreemaSafeServerTestResponse {
        val request = buildRequest {
            url(serverInfo.getConfigUrl(id))
            get()
            header(Http.Header.ACCEPT, "application/json")
            authorization(serverInfo)
        }
        okHttpClient.execute(request).use { response ->
            val body = response.getSuccessBodyOrThrow()
            val jsonObject = JSONObject(body.string())
            return ThreemaSafeServerTestResponse(
                maxBackupBytes = jsonObject.getLong("maxBackupBytes"),
                retentionDays = jsonObject.getInt("retentionDays"),
            )
        }
    }

    @Throws(IOException::class)
    fun downloadEncryptedBackup(serverInfo: ThreemaSafeServerInfo, id: ThreemaSafeBackupId?): ByteArray {
        val request = buildRequest {
            url(serverInfo.getBackupUrl(id))
            get()
            header(Http.Header.ACCEPT, "application/octet-stream")
            authorization(serverInfo)
        }
        return okHttpClient.execute(request).use { response ->
            response.getSuccessBodyOrThrow().bytes()
        }
    }

    @Throws(IOException::class)
    fun uploadEncryptedBackup(serverInfo: ThreemaSafeServerInfo, id: ThreemaSafeBackupId?, backupData: ByteArray) {
        val request = buildRequest {
            url(serverInfo.getBackupUrl(id))
            put(backupData.toRequestBody("application/octet-stream".toMediaType()))
            authorization(serverInfo)
        }
        okHttpClient.execute(request).use { response ->
            response.throwIfNotSuccessful()
        }
    }

    @Throws(IOException::class)
    fun deleteBackup(serverInfo: ThreemaSafeServerInfo, id: ThreemaSafeBackupId?) {
        val request = buildRequest {
            url(serverInfo.getBackupUrl(id))
            delete()
            authorization(serverInfo)
        }
        okHttpClient.execute(request).use { response ->
            response.throwIfNotSuccessful()
        }
    }

    private fun Request.Builder.authorization(serverInfo: ThreemaSafeServerInfo) {
        serverInfo.authorization?.let {
            header(Http.Header.AUTHORIZATION, it)
        }
    }
}

typealias ThreemaSafeBackupId = ByteArray
