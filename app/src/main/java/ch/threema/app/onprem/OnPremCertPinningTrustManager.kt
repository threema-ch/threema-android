package ch.threema.app.onprem

import android.annotation.SuppressLint
import ch.threema.base.utils.Base64
import ch.threema.common.secureContentEquals
import ch.threema.domain.onprem.OnPremConfigDomainRuleMatchMode
import ch.threema.domain.onprem.OnPremConfigDomainRuleSpkiAlgorithm
import ch.threema.domain.onprem.OnPremConfigDomains
import java.security.MessageDigest
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
                    throw CertificateException("Certificate did not match any cert pinning rules for $hostname")
                }
            }
        }
    }

    private fun X509Certificate.getPublicKeyFingerprintFor(algorithm: OnPremConfigDomainRuleSpkiAlgorithm): ByteArray =
        when (algorithm) {
            OnPremConfigDomainRuleSpkiAlgorithm.SHA256 -> getPublicKeyFingerprintFor("sha-256")
        }

    private fun X509Certificate.getPublicKeyFingerprintFor(algorithm: String): ByteArray =
        MessageDigest.getInstance(algorithm)
            .apply {
                update(publicKey.encoded)
            }
            .digest()

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        delegate.getAcceptedIssuers()
}
