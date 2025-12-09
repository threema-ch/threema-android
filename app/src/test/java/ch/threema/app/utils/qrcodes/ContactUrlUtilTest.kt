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

package ch.threema.app.utils.qrcodes

import ch.threema.app.qrcodes.ContactUrlResult
import ch.threema.app.qrcodes.ContactUrlUtil
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactUrlUtilTest {

    private val contactUrlUtil = ContactUrlUtil()

    @Test
    fun `generate contact url`() {
        val result = contactUrlUtil.generate(
            identity = "ABCD1234",
            publicKey = PUBLIC_KEY,
        )
        assertEquals(
            "3mid:ABCD1234,00020406080a0c0e10121416181a1c1e20222426282a2c2e30323436383a3c3e",
            result,
        )
    }

    @Test
    fun `parse contact url without expiration`() {
        val input = "3mid:ABCD1234,00020406080a0c0e10121416181a1c1e20222426282a2c2e30323436383a3c3e"
        val result = contactUrlUtil.parse(input)
        assertEquals(
            ContactUrlResult(
                identity = "ABCD1234",
                publicKey = PUBLIC_KEY,
                expiration = null,
            ),
            result,
        )
        assertFalse(result!!.isExpired(Instant.MIN))
    }

    @Test
    fun `parse contact url with expiration date`() {
        val input = "3mid:ABCD1234,00020406080a0c0e10121416181a1c1e20222426282a2c2e30323436383a3c3e,1766599200"
        val result = contactUrlUtil.parse(input)
        assertNotNull(result)
        assertEquals(
            ContactUrlResult(
                identity = "ABCD1234",
                publicKey = PUBLIC_KEY,
                expiration = Instant.ofEpochMilli(1_766_599_200_000L),
            ),
            result,
        )
        assertFalse(result.isExpired(Instant.parse("2025-12-23T18:00:00Z")))
        assertTrue(result.isExpired(Instant.parse("2025-12-25T18:00:00Z")))
    }

    @Test
    fun `parse with invalid input`() {
        val input = "invalid"
        assertNull(contactUrlUtil.parse(input))
    }

    @Test
    fun `parse with invalid identity`() {
        val input = "3mid:ABCD,00020406080a0c0e10121416181a1c1e20222426282a2c2e30323436383a3c3e"
        assertNull(contactUrlUtil.parse(input))
    }

    @Test
    fun `parse with invalid public key`() {
        val input = "3mid:ABCD1234,0g020406080a0c0e10121416181a1c1e20222426282a2c2e30323436383a3c3e"
        assertNull(contactUrlUtil.parse(input))
    }

    @Test
    fun `parse with invalid expiration date`() {
        val input = "3mid:ABCD1234,00020406080a0c0e10121416181a1c1e20222426282a2c2e30323436383a3c3e,invalid"
        val result = contactUrlUtil.parse(input)
        assertEquals(
            ContactUrlResult(
                identity = "ABCD1234",
                publicKey = PUBLIC_KEY,
                expiration = null,
            ),
            result,
        )
    }

    companion object {
        private val PUBLIC_KEY = ByteArray(32) { (it * 2).toByte() }
    }
}
