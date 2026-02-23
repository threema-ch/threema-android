package ch.threema.app.utils

import ch.threema.app.utils.LocaleUtil.mapLocaleToPredefinedLocales
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleUtilTest {
    private val predefinedLocales = arrayOf(
        "bg",
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
        assertLocaleMap("bg")
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
            mapLocaleToPredefinedLocales(Locale("zh", "TW"), predefinedLocales),
        )
    }

    private fun assertLocaleMap(from: String, to: String = from) {
        assertEquals(
            to,
            mapLocaleToPredefinedLocales(Locale.forLanguageTag(from), predefinedLocales),
            "The locale code '$from' was mapped to the wrong locale.",
        )
    }
}
