package ch.threema.app.onprem

import ch.threema.app.preference.service.PreferenceService
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
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
            onPremConfigParser = mockk(),
            onPremConfigStore = mockk(),
            onPremConfigVerifier = mockk(),
            pinnedOkHttpClient = mockk(),
            unpinnedOkHttpClient = mockk(),
            timeProvider = mockk(),
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
            onPremConfigParser = mockk(),
            onPremConfigStore = mockk(),
            onPremConfigVerifier = mockk(),
            pinnedOkHttpClient = mockk(),
            unpinnedOkHttpClient = mockk(),
            timeProvider = mockk(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        every { preferenceServiceMock.getOppfUrl() } returns "https://example.com/2"
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertNotSame(fetcher1, fetcher2)
    }

    @Test
    fun `in-memory cache is invalidated when username changes`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            onPremConfigParser = mockk(),
            onPremConfigStore = mockk(),
            onPremConfigVerifier = mockk(),
            pinnedOkHttpClient = mockk(),
            unpinnedOkHttpClient = mockk(),
            timeProvider = mockk(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        every { preferenceServiceMock.getLicenseUsername() } returns "username2"
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertNotSame(fetcher1, fetcher2)
    }

    @Test
    fun `in-memory cache is invalidated when password changes`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            onPremConfigParser = mockk(),
            onPremConfigStore = mockk(),
            onPremConfigVerifier = mockk(),
            pinnedOkHttpClient = mockk(),
            unpinnedOkHttpClient = mockk(),
            timeProvider = mockk(),
        )

        val fetcher1 = provider.getOnPremConfigFetcher()
        every { preferenceServiceMock.getLicensePassword() } returns "password2"
        val fetcher2 = provider.getOnPremConfigFetcher()

        assertNotSame(fetcher1, fetcher2)
    }

    @Test
    fun `fetcher is created even when credentials cannot be accessed`() {
        val preferenceServiceMock = mockPreferenceService()
        val provider = OnPremConfigFetcherProvider(
            preferenceService = preferenceServiceMock,
            onPremConfigParser = mockk(),
            onPremConfigStore = mockk(),
            onPremConfigVerifier = mockk(),
            pinnedOkHttpClient = mockk(),
            unpinnedOkHttpClient = mockk(),
            timeProvider = mockk(),
        )

        every { preferenceServiceMock.getLicenseUsername() } answers { throw MasterKeyLockedException() }
        every { preferenceServiceMock.getLicensePassword() } answers { throw MasterKeyLockedException() }

        provider.getOnPremConfigFetcher()
    }

    @Test
    fun `fetcher not created when server url not available`() {
        val provider = OnPremConfigFetcherProvider(
            preferenceService = mockk {
                every { getOppfUrl() } returns null
            },
            onPremConfigParser = mockk(),
            onPremConfigStore = mockk(),
            onPremConfigVerifier = mockk(),
            pinnedOkHttpClient = mockk(),
            unpinnedOkHttpClient = mockk(),
            timeProvider = mockk(),
        )

        assertFailsWith<IllegalStateException> {
            provider.getOnPremConfigFetcher()
        }
    }

    private fun mockPreferenceService() = mockk<PreferenceService> {
        every { getOppfUrl() } returns "https://example.com"
        every { getLicenseUsername() } returns "username"
        every { getLicensePassword() } returns "password"
    }
}
