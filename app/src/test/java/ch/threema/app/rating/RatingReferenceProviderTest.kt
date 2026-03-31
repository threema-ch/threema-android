package ch.threema.app.rating

import ch.threema.app.preference.service.PreferenceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals

class RatingReferenceProviderTest {
    @Test
    fun `get and return existing reference`() {
        val randomMock = mockk<SecureRandom>()
        val ratingReferenceProvider = RatingReferenceProvider(
            preferenceService = mockk {
                every { getRatingReference() } returns "0404040404040404040404040404040404040404040404040404040404040404"
            },
            secureRandom = randomMock,
        )

        val reference = ratingReferenceProvider.getOrCreateRatingReference()

        assertEquals("0404040404040404040404040404040404040404040404040404040404040404", reference)
        verify(exactly = 0) { randomMock.nextBytes(any()) }
    }

    @Test
    fun `create and store new reference when no reference exists yet`() {
        val randomMock = mockk<SecureRandom> {
            every { nextBytes(any()) } answers { firstArg<ByteArray>().fill(0xA0.toByte()) }
        }
        val preferenceServiceMock = mockk<PreferenceService>(relaxed = true) {
            every { getRatingReference() } returns null
        }
        val ratingReferenceProvider = RatingReferenceProvider(
            preferenceService = preferenceServiceMock,
            secureRandom = randomMock,
        )

        val reference = ratingReferenceProvider.getOrCreateRatingReference()

        assertEquals("a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0", reference)
        verify { preferenceServiceMock.setRatingReference("a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0") }
    }
}
