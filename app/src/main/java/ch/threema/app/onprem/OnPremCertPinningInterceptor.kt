package ch.threema.app.onprem

import okhttp3.Interceptor
import okhttp3.Response

class OnPremCertPinningInterceptor(
    private val hostnameProvider: ThreadLocalHostnameProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        hostnameProvider.setHostname(request.url.host)
        return chain.proceed(request)
    }
}
