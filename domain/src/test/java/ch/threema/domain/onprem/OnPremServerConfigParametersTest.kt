package ch.threema.domain.onprem

import kotlin.test.Test
import kotlin.test.assertEquals

class OnPremServerConfigParametersTest {

    @Test
    fun `get oppf fallback url`() {
        val parameters = OnPremServerConfigParameters(
            oppfUrl = "https://example.com/path.to.oppf/config.oppf",
            username = null,
            password = null,
        )

        assertEquals(
            "https://example.com/path.to.oppf/config.fallback.oppf",
            parameters.oppfFallbackUrl,
        )
    }
}
