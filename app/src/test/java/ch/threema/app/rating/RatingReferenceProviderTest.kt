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
                every { ratingReference } returns "0404040404040404040404040404040404040404040404040404040404040404"
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
            every { ratingReference } returns null
        }
        val ratingReferenceProvider = RatingReferenceProvider(
            preferenceService = preferenceServiceMock,
            secureRandom = randomMock,
        )

        val reference = ratingReferenceProvider.getOrCreateRatingReference()

        assertEquals("a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0", reference)
        verify { preferenceServiceMock.ratingReference = "a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0a0" }
    }
}
