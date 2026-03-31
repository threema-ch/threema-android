package ch.threema.app.onprem

import android.annotation.SuppressLint
import ch.threema.base.utils.Base64
import ch.threema.common.secureContentEquals
import ch.threema.domain.onprem.OnPremConfigDomainRuleMatchMode
import ch.threema.domain.onprem.OnPremConfigDomainRuleSpkiAlgorithm
import ch.threema.domain.onprem.OnPremConfigDomains
import ch.threema.libthreema.sha256
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

@SuppressLint("CustomX509TrustManager")
class OnPremCertPinningTrustManager(
    private val getOnPremConfigDomains: () -> OnPremConfigDomains?,
    private val hostnameProvider: HostnameProvider,
    private val delegate: TrustManagerDelegate = TrustManagerDelegate(),
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        delegate.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {
        val hostname = try {
            hostnameProvider.getHostname()
        } catch (e: IllegalStateException) {
            throw CertificateException(e)
        }
        delegate.checkServerTrusted(chain, authType, hostname)

        val rules = getOnPremConfigDomains()?.rules ?: return
        val certificate = chain?.firstOrNull()
            ?: throw CertificateException("No certificate found in trust chain")

        rules.forEach { rule ->
            val matchesHostname = when (rule.matchMode) {
                OnPremConfigDomainRuleMatchMode.EXACT -> hostname.equals(rule.fqdn, ignoreCase = true)
                OnPremConfigDomainRuleMatchMode.INCLUDE_SUBDOMAINS -> {
                    hostname.equals(rule.fqdn, ignoreCase = true) || hostname.endsWith(".${rule.fqdn}", ignoreCase = true)
                }
            }
            if (matchesHostname) {
                val matchesCertificate = rule.spkis?.any { spki ->
                    certificate.getPublicKeyFingerprintFor(spki.algorithm).secureContentEquals(Base64.decode(spki.value))
                }
                if (matchesCertificate == false) {
                    throw OnPremCertificateException(hostname)
                }
            }
        }
    }

    private fun X509Certificate.getPublicKeyFingerprintFor(algorithm: OnPremConfigDomainRuleSpkiAlgorithm): ByteArray =
        when (algorithm) {
            OnPremConfigDomainRuleSpkiAlgorithm.SHA256 -> sha256(publicKey.encoded)
        }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        delegate.getAcceptedIssuers()
}
