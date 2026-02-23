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
    fun `fetcher is stored in in-memory cache`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            onPremConfigStore = mockk(),
            okHttpClient = mockk(),
            trustedPublicKeys = emptyArray(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertSame(fetcher1, fetcher2)
    }

    @Test
    fun `in-memory cache is invalidated when server url changes`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            onPremConfigStore = mockk(),
            okHttpClient = mockk(),
            trustedPublicKeys = emptyArray(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        every { preferenceServiceMock.onPremServer } returns "https://example.com/2"
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertNotSame(fetcher1, fetcher2)
    }

    @Test
    fun `in-memory cache is invalidated when username changes`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            onPremConfigStore = mockk(),
            okHttpClient = mockk(),
            trustedPublicKeys = emptyArray(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        every { preferenceServiceMock.licenseUsername } returns "username2"
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertNotSame(fetcher1, fetcher2)
    }

    @Test
    fun `in-memory cache is invalidated when password changes`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            onPremConfigStore = mockk(),
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
            onPremConfigStore = mockk(),
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
