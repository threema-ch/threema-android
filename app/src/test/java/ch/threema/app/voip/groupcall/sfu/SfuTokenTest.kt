/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

import ch.threema.domain.protocol.api.SfuToken
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SFU_BASE_URL = "https://server-1-sfu.threema.ch/"
private val SFU_BASE_URL_ALLOWED_SUFFIXES: Set<String> = setOf(
    "sfu.threema.ch",
    "sfu.threema-work.ch",
)

internal class SfuTokenTest {
    @Test
    fun isAllowedBaseUrl_emptyBaseUrlIsInvalid() {
        assertFalse(isAllowedBaseUrl(""))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithoutProtocolIsInvalid() {
        assertFalse(isAllowedBaseUrl("sfu.threema.ch"))
        assertFalse(isAllowedBaseUrl("sfu.threema.ch/"))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithHttpProtocolIsInvalid() {
        assertFalse(isAllowedBaseUrl("http://sfu.threema.ch"))
        assertFalse(isAllowedBaseUrl("http://sfu.threema.ch/"))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithInvalidSuffixIsInvalid() {
        assertFalse(isAllowedBaseUrl("http://some-bad.url.ch"))
        assertFalse(isAllowedBaseUrl("http://server.threema.ch"))
        assertFalse(isAllowedBaseUrl("http://fu.threema.ch"))
        assertFalse(isAllowedBaseUrl("http://some-bad.url.ch/"))
        assertFalse(isAllowedBaseUrl("http://server.threema.ch/"))
        assertFalse(isAllowedBaseUrl("http://fu.threema.ch/"))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithHttpsProtocolAndValidHostSuffixIsValid() {
        assertTrue(isAllowedBaseUrl("https://sfu.threema.ch"))
        assertTrue(isAllowedBaseUrl("https://blasfu.threema.ch"))
        assertTrue(isAllowedBaseUrl("https://bla-sfu.threema.ch"))
        assertTrue(isAllowedBaseUrl("https://bla-1-sfu.threema.ch"))
        assertTrue(isAllowedBaseUrl("https://sfu.threema-work.ch"))
        assertTrue(isAllowedBaseUrl("https://blasfu.threema-work.ch"))
        assertTrue(isAllowedBaseUrl("https://bla-sfu.threema-work.ch"))
        assertTrue(isAllowedBaseUrl("https://bla-1-sfu.threema-work.ch"))
    }

    @Test
    fun isAllowedBaseUrl_baseUrlWithAdditionalPathInfoIsValid() {
        assertTrue(isAllowedBaseUrl("https://sfu.threema.ch/"))
        assertTrue(isAllowedBaseUrl("https://sfu.threema.ch/path"))
        assertTrue(isAllowedBaseUrl("https://sfu.threema.ch/path/to"))
        assertTrue(isAllowedBaseUrl("https://sfu.threema.ch/path/to/sfu"))
        assertTrue(isAllowedBaseUrl("https://sfu.threema.ch/path/to/sfu/"))
    }

    /**
     * If the base url contains a port number, the port number must also be present in the allowed suffix and vice versa.
     */
    @Test
    fun isAllowedBaseUrl_baseUrlWithNonDefaultPort() {
        // port not provided in allowed suffixes must fail
        assertFalse(isAllowedBaseUrl("https://sfu.threema.ch:4711"))
        assertFalse(isAllowedBaseUrl("https://sfu.threema.ch:4711/"))
        assertFalse(isAllowedBaseUrl("https://sfu.threema-work.ch:4711"))
        assertFalse(isAllowedBaseUrl("https://sfu.threema-work.ch:4711/"))

        // port provided in allowed suffixes must work
        val token = getToken(setOf("threema.ch:4711", "threema-work.ch:4711"))
        assertTrue(token.isAllowedBaseUrl("https://sfu.threema.ch:4711"))
        assertTrue(token.isAllowedBaseUrl("https://sfu.threema.ch:4711/"))
        assertTrue(token.isAllowedBaseUrl("https://sfu.threema-work.ch:4711"))
        assertTrue(token.isAllowedBaseUrl("https://sfu.threema-work.ch:4711/"))

        // port provided in alllowed suffixes but not in checked base url must fail
        assertFalse(token.isAllowedBaseUrl("https://sfu.threema.ch"))
        assertFalse(token.isAllowedBaseUrl("https://sfu.threema.ch/"))
        assertFalse(token.isAllowedBaseUrl("https://sfu.threema-work.ch"))
        assertFalse(token.isAllowedBaseUrl("https://sfu.threema-work.ch/"))
    }

    private fun isAllowedBaseUrl(baseUrl: String): Boolean {
        return getToken().isAllowedBaseUrl(baseUrl)
    }

    private fun getToken(allowedSuffixes: Set<String> = SFU_BASE_URL_ALLOWED_SUFFIXES): SfuToken {
        // Some date in the future. As long as it is not relevant for the tests any date can be used.
        val expirationDate = Date(Date().time + 86_400_000L)
        return SfuToken(
            SFU_BASE_URL,
            allowedSuffixes,
            "${expirationDate.time / 1000L}:ABCD1234:YvKFP429AWBkIsJ4upEm9b6OJ3tayu",
            expirationDate,
        )
    }
}
