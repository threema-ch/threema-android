package ch.threema.app.onprem

import java.security.cert.CertificateException

/**
 * Thrown when an API request is made to a domain for which certificate pinning rules exist in the OPPF but there was no match.
 */
class OnPremCertificateException(hostname: String) : CertificateException() {
    override val message = "Certificate did not match any cert pinning rules for $hostname"
}
