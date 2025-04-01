/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.utils

import ch.threema.app.utils.LocaleUtil.mapLocaleToPredefinedLocales
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LocaleUtilTest {

    private val predefinedLocales = arrayOf(
        "ca",
        "cs",
        "de",
        "en",
        "es",
        "fr",
        "it",
        "hu",
        "nl",
        "no",
        "pl",
        "pt",
        "rm",
        "sk",
        "tr",
        "be-BY",
        "ru",
        "uk",
        "zh-hans-CN",
        "zh-hant-TW",
        "ja",
    )

    @Test
    fun testDefaultLocale() {
        assertLocaleMap("")
        assertEquals("", mapLocaleToPredefinedLocales(null, predefinedLocales))
    }

    @Test
    fun testExactLocales() {
        assertLocaleMap("ca")
        assertLocaleMap("en")
        assertLocaleMap("de")
        assertLocaleMap("nl")
        assertLocaleMap("be-BY")
        assertLocaleMap("zh-hans-CN")
    }

    @Test
    fun testSpecificRegion() {
        assertLocaleMap("de-CH", "de")
        assertLocaleMap("de-CH", "de")
        assertLocaleMap("en-US", "en")
    }

    @Test
    fun testNoRegion() {
        assertLocaleMap("zh", "zh-hans-CN")
        assertLocaleMap("be", "be-BY")
    }

    @Test
    fun testOtherFormattedRegion() {
        assertLocaleMap("be-BY", "be-BY")
        assertLocaleMap("nl-BE", "nl")
    }

    @Test
    fun testScript() {
        // Always fall back to simplified chinese if there is no explicit script set
        assertEquals("zh-hans-CN", mapLocaleToPredefinedLocales(Locale("zh"), predefinedLocales))
        assertEquals(
            "zh-hans-CN",
            mapLocaleToPredefinedLocales(Locale("zh", "TW"), predefinedLocales)
        )
    }

    private fun assertLocaleMap(from: String, to: String = from) {
        assertEquals(
            "The locale code '$from' was mapped to the wrong locale.",
            to,
            mapLocaleToPredefinedLocales(Locale.forLanguageTag(from), predefinedLocales)
        )
    }

}
