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

package ch.threema.app.services

import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.ThreemaException
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class OnPremConfigFetcherProviderTest {
    @Test
    fun `fetcher is stored in cache`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            okHttpClient = mockk(),
            trustedPublicKeys = emptyArray(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertSame(fetcher1, fetcher2)
    }

    @Test
    fun `cache is invalidated when server url changes`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            okHttpClient = mockk(),
            trustedPublicKeys = emptyArray(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        every { preferenceServiceMock.onPremServer } returns "https://example.com/2"
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertNotSame(fetcher1, fetcher2)
    }

    @Test
    fun `cache is invalidated when username changes`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            okHttpClient = mockk(),
            trustedPublicKeys = emptyArray(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        every { preferenceServiceMock.licenseUsername } returns "username2"
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertNotSame(fetcher1, fetcher2)
    }

    @Test
    fun `cache is invalidated when password changes`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            okHttpClient = mockk(),
            trustedPublicKeys = emptyArray(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        every { preferenceServiceMock.licensePassword } returns "password2"
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertNotSame(fetcher1, fetcher2)
    }

    @Test
    fun `fetcher not created when server url not available`() {
        val provider = OnPremConfigFetcherProvider(
            preferenceService = mockk {
                every { onPremServer } returns null
            },
            okHttpClient = mockk(),
            trustedPublicKeys = emptyArray(),
        )

        assertFailsWith<ThreemaException> {
            provider.getOnPremConfigFetcher()
        }
    }

    private fun mockPreferenceService() = mockk<PreferenceService> {
        every { onPremServer } returns "https://example.com"
        every { licenseUsername } returns "username"
        every { licensePassword } returns "password"
    }
}
