package ch.threema.app.utils

import ch.threema.app.onprem.OnPremSSLSocketFactoryProvider
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SSLUtil {
    private val defaultSSLSocketFactory: SSLSocketFactory

    val defaultTrustManager: X509TrustManager

    init {
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        defaultTrustManager = trustManagers.filterIsInstance<X509TrustManager>().first()
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagers, null)
        defaultSSLSocketFactory = sslContext.socketFactory
    }

    /**
     * Get a Socket Factory for certificate pinning and forced TLS version upgrade.
     */
    @JvmStatic
    fun getSSLSocketFactory(host: String): SSLSocketFactory {
        val sslSocketFactory = if (ConfigUtils.isOnPremBuild()) {
            OnPremSSLSocketFactoryProvider.instance.getSslSocketFactory(host)
        } else {
            defaultSSLSocketFactory
        }
        return TLSUpgradeSocketFactoryWrapper(sslSocketFactory)
    }
}
