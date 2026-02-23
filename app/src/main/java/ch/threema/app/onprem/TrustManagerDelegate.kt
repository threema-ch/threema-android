package ch.threema.app.onprem

import android.net.http.X509TrustManagerExtensions
import ch.threema.app.utils.SSLUtil
import java.security.cert.X509Certificate

class TrustManagerDelegate {

    private val trustManager = SSLUtil.defaultTrustManager
    private val trustManagerExtensions = X509TrustManagerExtensions(trustManager)

    fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, hostname: String) {
        trustManagerExtensions.checkServerTrusted(chain, authType, hostname)
    }

    fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        trustManager.checkClientTrusted(chain, authType)
    }

    fun getAcceptedIssuers(): Array<X509Certificate> =
        trustManager.acceptedIssuers
}
