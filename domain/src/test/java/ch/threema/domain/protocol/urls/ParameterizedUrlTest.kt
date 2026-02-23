package ch.threema.domain.protocol.urls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParameterizedUrlTest {
    @Test
    fun `parameterized url equality`() {
        assertEquals(BlobUrl("foo"), BlobUrl("foo"))
        assertNotEquals<ParameterizedUrl>(BlobUrl("foo"), MapPoiAroundUrl("foo"))
    }

    @Test
    fun `placeholder replacement in url template`() {
        val parameterizeUrl = object : ParameterizedUrl(
            template = "https://example.com/{placeholder1}/{placeholder2}/",
            requiredPlaceholders = emptyArray(),
        ) {
            fun get(placeholder1: String, placeholder2: String) =
                getUrl(
                    "placeholder1" to placeholder1,
                    "placeholder2" to placeholder2,
                )
        }

        val url = parameterizeUrl.get(placeholder1 = "foo", placeholder2 = "bar")

        assertEquals("https://example.com/foo/bar/", url)
    }
}
