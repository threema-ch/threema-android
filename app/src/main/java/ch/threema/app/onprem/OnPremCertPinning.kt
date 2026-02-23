package ch.threema.app.onprem

import ch.threema.app.services.OnPremConfigFetcherProvider
import ch.threema.domain.onprem.OnPremConfigDomains
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

object OnPremCertPinning {
    fun createClientWithCertPinning(
        baseClient: OkHttpClient,
        onPremConfigFetcherProvider: OnPremConfigFetcherProvider,
    ): OkHttpClient =
        createClientWithCertPinning(
            baseClient,
            getOnPremConfigDomains = {
                onPremConfigFetcherProvider.getOnPremConfigFetcher().fetch().domains
            },
        )

    fun createClientWithCertPinning(
        baseClient: OkHttpClient,
        getOnPremConfigDomains: () -> OnPremConfigDomains?,
    ): OkHttpClient {
        val hostnameProvider = ThreadLocalHostnameProvider()
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = getOnPremConfigDomains,
            hostnameProvider = hostnameProvider,
        )
        return baseClient.newBuilder()
            .sslSocketFactory(createSocketFactory(trustManager), trustManager)
            .addInterceptor(OnPremCertPinningInterceptor(hostnameProvider))
            .build()
    }

    fun createSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext.socketFactory
    }
}
