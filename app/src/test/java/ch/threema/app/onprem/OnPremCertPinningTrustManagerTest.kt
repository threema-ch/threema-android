package ch.threema.app.onprem

import ch.threema.base.utils.Base64
import ch.threema.domain.onprem.OnPremConfigDomainRule
import ch.threema.domain.onprem.OnPremConfigDomainRuleMatchMode
import ch.threema.domain.onprem.OnPremConfigDomainRuleSpki
import ch.threema.domain.onprem.OnPremConfigDomainRuleSpkiAlgorithm
import ch.threema.domain.onprem.OnPremConfigDomains
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertFailsWith

class OnPremCertPinningTrustManagerTest {
    @Test
    fun `delegate is used for checkClientTrusted`() {
        val delegate = mockk<TrustManagerDelegate>(relaxed = true)
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(),
            hostnameProvider = { "3ma.ch" },
            delegate = delegate,
        )
        val chain = emptyArray<X509Certificate>()
        val authType = "RSA"

        trustManager.checkClientTrusted(chain, authType)

        verify { delegate.checkClientTrusted(chain, authType) }
    }

    @Test
    fun `delegate's exceptions are propagated for checkClientTrusted`() {
        val delegate = mockk<TrustManagerDelegate> {
            every { checkClientTrusted(any(), any()) } throws CertificateException()
        }
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(),
            hostnameProvider = { "3ma.ch" },
            delegate = delegate,
        )

        assertFailsWith<CertificateException> {
            trustManager.checkClientTrusted(emptyArray<X509Certificate>(), "RSA")
        }
    }

    @Test
    fun `checkServerTrusted fails if hostname is not set`() {
        val delegate = mockk<TrustManagerDelegate>(relaxed = true)
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(),
            hostnameProvider = { throw IllegalStateException("hostname not set") },
            delegate = delegate,
        )
        val chain = emptyArray<X509Certificate>()
        val authType = "RSA"

        assertFailsWith<CertificateException> {
            trustManager.checkServerTrusted(chain, authType)
        }
    }

    @Test
    fun `delegate is used for checkServerTrusted`() {
        val delegate = mockk<TrustManagerDelegate>(relaxed = true)
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(),
            hostnameProvider = { "3ma.ch" },
            delegate = delegate,
        )
        val chain = emptyArray<X509Certificate>()
        val authType = "RSA"

        trustManager.checkServerTrusted(chain, authType)

        verify { delegate.checkServerTrusted(chain, authType, "3ma.ch") }
    }

    @Test
    fun `delegate's exceptions are propagated for checkServerTrusted`() {
        val delegate = mockk<TrustManagerDelegate> {
            every { checkServerTrusted(any(), any(), any()) } throws CertificateException()
        }
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(),
            hostnameProvider = { "3ma.ch" },
            delegate = delegate,
        )

        assertFailsWith<CertificateException> {
            trustManager.checkServerTrusted(emptyArray<X509Certificate>(), "RSA")
        }
    }

    @Test
    fun `exact hostname match and valid fingerprint`() {
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(
                rules = listOf(
                    OnPremConfigDomainRule(
                        fqdn = "3ma.ch",
                        matchMode = OnPremConfigDomainRuleMatchMode.EXACT,
                        spkis = listOf(
                            OnPremConfigDomainRuleSpki(
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                                value = BASE64_ENCODED_SHA256_FINGERPRINT,
                            ),
                        ),
                    ),
                ),
            ),
            hostnameProvider = { "3ma.ch" },
            delegate = mockk(relaxed = true),
        )
        val certificate = mockCertificate(ENCODED_FINGERPRINT)

        trustManager.checkServerTrusted(arrayOf(certificate), null)
    }

    @Test
    fun `exact hostname match, but no valid fingerprint`() {
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(
                rules = listOf(
                    OnPremConfigDomainRule(
                        fqdn = "3ma.ch",
                        matchMode = OnPremConfigDomainRuleMatchMode.EXACT,
                        spkis = listOf(
                            OnPremConfigDomainRuleSpki(
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                                value = BASE64_ENCODED_SHA256_FINGERPRINT,
                            ),
                        ),
                    ),
                ),
            ),
            hostnameProvider = { "3ma.ch" },
            delegate = mockk(relaxed = true),
        )
        val certificate = mockCertificate(ENCODED_FINGERPRINT.replace("5", "6"))

        assertFailsWith<CertificateException> {
            trustManager.checkServerTrusted(arrayOf(certificate), null)
        }
    }

    @Test
    fun `subdomain hostname match and valid fingerprint`() {
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(
                rules = listOf(
                    OnPremConfigDomainRule(
                        fqdn = "3ma.ch",
                        matchMode = OnPremConfigDomainRuleMatchMode.INCLUDE_SUBDOMAINS,
                        spkis = listOf(
                            OnPremConfigDomainRuleSpki(
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                                value = BASE64_ENCODED_SHA256_FINGERPRINT,
                            ),
                        ),
                    ),
                ),
            ),
            hostnameProvider = { "some-subdomain.3ma.ch" },
            delegate = mockk(relaxed = true),
        )
        val certificate = mockCertificate(ENCODED_FINGERPRINT)

        trustManager.checkServerTrusted(arrayOf(certificate), null)
    }

    @Test
    fun `subdomain hostname match, but no valid fingerprint`() {
        val trustManager = OnPremCertPinningTrustManager(
            getOnPremConfigDomains = mockGetOnPremConfigDomains(
                rules = listOf(
                    OnPremConfigDomainRule(
                        fqdn = "3ma.ch",
                        matchMode = OnPremConfigDomainRuleMatchMode.INCLUDE_SUBDOMAINS,
                        spkis = listOf(
                            OnPremConfigDomainRuleSpki(
                                algorithm = OnPremConfigDomainRuleSpkiAlgorithm.SHA256,
                                value = BASE64_ENCODED_SHA256_FINGERPRINT,
                            ),
                        ),
                    ),
                ),
            ),
            hostnameProvider = { "some-subdomain.3ma.ch" },
            delegate = mockk(relaxed = true),
        )
        val certificate = mockCertificate(ENCODED_FINGERPRINT.replace("5", "6"))

        assertFailsWith<CertificateException> {
            trustManager.checkServerTrusted(arrayOf(certificate), null)
        }
    }

    private fun mockGetOnPremConfigDomains(rules: List<OnPremConfigDomainRule>? = null): () -> OnPremConfigDomains? =
        {
            rules?.let {
                OnPremConfigDomains(rules = rules)
            }
        }

    private fun mockCertificate(encodedFingerprint: String): X509Certificate =
        mockk<X509Certificate> {
            every { publicKey } returns mockk {
                every { encoded } returns Base64.decode(encodedFingerprint)
            }
        }

    companion object {
        const val BASE64_ENCODED_SHA256_FINGERPRINT = "USuc7kX1q51bhJNFmAGuHHRj1h0kIw7vv2F4I2vB1RM="
        const val ENCODED_FINGERPRINT = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEdDurK45zDIlEL2rnDQXnYnMIpxgatWGV7PW6VBmCmeM2ocHcOd3FyTcj" +
            "PqiQUsM6IkQAGRtWf06F/pWR3uYOjg=="
    }
}
