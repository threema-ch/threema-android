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

import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.urls.AppRatingUrl
import ch.threema.testhelpers.getBodyAsUtf8String
import ch.threema.testhelpers.mockOkHttpClient
import ch.threema.testhelpers.respondWith
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RatingServiceTest {
    @Test
    fun `send rating`() {
        val ratingService = RatingService(
            ratingReferenceProvider = mockRatingReferenceProvider(),
            okHttpClient = mockOkHttpClient { request ->
                assertEquals("POST", request.method)
                assertEquals("https://test.threema.ch/rating/4", request.url.toString())
                assertEquals(
                    "ref=my-ref&feedback=Hello%20world%0A%0A---%0A1.2.3%0A",
                    request.getBodyAsUtf8String(),
                )

                request.respondWith("")
            },
            serverAddressProvider = mockServerAddressProvider(),
        )

        ratingService.sendRating(
            rating = 4,
            text = "Hello world",
            version = "1.2.3",
        )
    }

    @Test
    fun `IOExceptions during sending are handled`() {
        val ratingService = RatingService(
            ratingReferenceProvider = mockRatingReferenceProvider(),
            okHttpClient = mockOkHttpClient {
                throw IOException()
            },
            serverAddressProvider = mockServerAddressProvider(),
        )

        assertFailsWith<ThreemaException> {
            ratingService.sendRating(
                rating = 4,
                text = "Hello world",
                version = "1.2.3",
            )
        }
    }

    @Test
    fun `error responses are handled`() {
        val ratingService = RatingService(
            ratingReferenceProvider = mockRatingReferenceProvider(),
            okHttpClient = mockOkHttpClient { request ->
                request.respondWith(code = 500)
            },
            serverAddressProvider = mockServerAddressProvider(),
        )

        assertFailsWith<ThreemaException> {
            ratingService.sendRating(
                rating = 4,
                text = "Hello world",
                version = "1.2.3",
            )
        }
    }

    private fun mockRatingReferenceProvider(): RatingReferenceProvider =
        mockk {
            every { getOrCreateRatingReference() } returns "my-ref"
        }

    private fun mockServerAddressProvider(): ServerAddressProvider =
        mockk {
            every { getAppRatingUrl() } returns AppRatingUrl("https://test.threema.ch/rating/{rating}")
        }
}
