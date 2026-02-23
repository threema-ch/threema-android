package ch.threema.app.onprem

import ch.threema.app.ThreemaApplication
import ch.threema.app.services.OnPremConfigFetcherProvider
import javax.net.ssl.SSLSocketFactory

class OnPremSSLSocketFactoryProvider(
    private val onPremConfigFetcherProvider: OnPremConfigFetcherProvider,
) {
    fun getSslSocketFactory(hostname: String): SSLSocketFactory {
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = {
                onPremConfigFetcherProvider.getOnPremConfigFetcher().fetch().domains
            },
            hostnameProvider = { hostname },
        )
        return OnPremCertPinning.createSocketFactory(trustManager)
    }

    companion object {
        @JvmStatic
        val instance: OnPremSSLSocketFactoryProvider by lazy {
            OnPremSSLSocketFactoryProvider(
                ThreemaApplication.requireServiceManager().onPremConfigFetcherProvider,
            )
        }
    }
}
