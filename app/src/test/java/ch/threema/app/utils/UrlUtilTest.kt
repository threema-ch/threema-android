package ch.threema.app.utils

import android.net.Uri
import ch.threema.app.utils.UrlUtil.isSafeUri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlUtilTest {
    @Test
    fun emptyUris() {
        assertIllegalUri("")

        assertIllegalUri(".")

        assertIllegalUri("..")
    }

    @Test
    fun simpleUris() {
        assertLegalUri("threema.com")

        assertIllegalUri("threemа.com") // cyrillic a

        assertLegalUri("人人贷.公司")

        assertLegalUri("gfrör.li")

        assertLegalUri("wikipedia.org")
    }

    @Test
    fun nonIdentifierChars() {
        // Must contain a non-ascii character, otherwise the hyphen-minus is accepted anyway (ascii)
        assertLegalUri("a-ü.ch")

        // Accepted as only ascii characters are involved
        assertLegalUri("-.ch")

        // Don't allow mixing cyrillic a with latin a
        assertIllegalUri("a-а.ch")
    }

    @Test
    fun mixedScriptUris() {
        assertLegalUri("$GREEK$GREEK.com")

        assertIllegalUri("$LATIN$GREEK.ch")

        assertLegalUri("$LATIN$HAN$HIRAGANA$KATAKANA.香港")

        assertLegalUri("$HAN$BOPOMOFO.香港")

        assertLegalUri("$LATIN$HAN$BOPOMOFO.香港")

        assertLegalUri("$LATIN$HAN$HANGUL.com")

        assertIllegalUri("$HIRAGANA$BOPOMOFO.com")

        assertIllegalUri("$LATIN$HAN$HANGUL$BOPOMOFO.com")

        assertIllegalUri("$KATAKANA$HANGUL.ch")

        assertIllegalUri("$HAN$CYRILLIC.com")

        assertLegalUri("$A_LATIN$LATIN.ch")

        assertLegalUri("$A_INHERITED$A_LATIN$LATIN.ch")
    }

    @Test
    fun mixedScriptComponentUris() {
        assertLegalUri("$GREEK.$LATIN.$HANGUL.рф")

        assertLegalUri("$GREEK.$LATIN$HANGUL.$HANGUL.рф")

        assertIllegalUri("$GREEK$LATIN.$LATIN.$HANGUL.рф")
    }

    private fun assertLegalUri(host: String) {
        assertUri(legalExpected = true, host)
    }

    private fun assertIllegalUri(host: String) {
        assertUri(legalExpected = false, host)
    }

    private fun assertUri(legalExpected: Boolean, host: String) {
        val uri = mockk<Uri>()
        every { uri.host } returns host
        assertEquals(legalExpected, isSafeUri(uri))
        verify(exactly = 1) { uri.host }
    }

    companion object {
        private const val LATIN = "a"
        private const val CYRILLIC = "а"
        private const val GREEK = "Ͱ"
        private const val HAN = "繁"
        private const val BOPOMOFO = "ㄅ"
        private const val HIRAGANA = "ぁ"
        private const val KATAKANA = "ァ"
        private const val HANGUL = "ᄀ"
        private const val A_INHERITED = "á"
        private const val A_LATIN = "á"
    }
}
