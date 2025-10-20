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

package ch.threema.common

import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okhttp3.Protocol
import okhttp3.Response

class OkHttpExtensionsTest {
    @Test
    fun `get expiration date from header`() {
        val response = buildResponse {
            header("Expires", "Mon, 22 Sep 2025 13:37:00 GMT")
        }

        assertEquals(
            Instant.parse("2025-09-22T13:37:00Z"),
            response.getExpiration(),
        )
    }

    @Test
    fun `expiration date is null when no header is available`() {
        val response = buildResponse()

        assertNull(response.getExpiration())
    }

    @Test
    fun `expiration date is null when no header contains invalid value`() {
        val response = buildResponse {
            header("Expires", "Mon, 32 Sep 2025 13:37:00 GMT")
        }

        assertNull(response.getExpiration())
    }

    private fun buildResponse(block: Response.Builder.() -> Unit = {}) =
        Response.Builder()
            .protocol(Protocol.HTTP_2)
            .request(mockk())
            .code(200)
            .message("")
            .apply(block)
            .build()
}
