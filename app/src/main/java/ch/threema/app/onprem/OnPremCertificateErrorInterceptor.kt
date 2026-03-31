package ch.threema.app.onprem

import ch.threema.base.utils.getThreemaLogger
import javax.net.ssl.SSLHandshakeException
import okhttp3.Interceptor
import okhttp3.Response

private val logger = getThreemaLogger("OnPremCertificateErrorInterceptor")

class OnPremCertificateErrorInterceptor(
    private val onCertificateError: (SSLHandshakeException) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        try {
            chain.proceed(chain.request())
        } catch (e: SSLHandshakeException) {
            if (e.cause is OnPremCertificateException) {
                logger.error("OPPF certificate pinning failed, possible MITM", e)
                onCertificateError(e)

                // If `onCertificateError` completed without an exception, we retry the original request
                chain.proceed(chain.request())
            } else {
                throw e
            }
        }
}
