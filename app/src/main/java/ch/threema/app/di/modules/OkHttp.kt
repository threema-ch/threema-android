package ch.threema.app.di.modules

import ch.threema.app.dev.hasDevFeatures
import ch.threema.app.di.Qualifiers
import ch.threema.app.onprem.OnPremCertPinning
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import kotlin.time.Duration.Companion.seconds
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module

private val logger = getThreemaLogger("OkHttp")

val okHttpClientsModule = module {
    single<OkHttpClient>(qualifier = Qualifiers.okHttpBase) {
        buildBaseOkHttpClient()
    }
    single<OkHttpClient> {
        val baseClient = get<OkHttpClient>(qualifier = Qualifiers.okHttpBase)
        if (ConfigUtils.isOnPremBuild()) {
            OnPremCertPinning.createDefaultOkHttpClient(baseClient)
        } else {
            baseClient
        }
    }
}

private fun buildBaseOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .apply {
            connectTimeout(ProtocolDefines.CONNECT_TIMEOUT.seconds)
            writeTimeout(ProtocolDefines.WRITE_TIMEOUT.seconds)
            readTimeout(ProtocolDefines.READ_TIMEOUT.seconds)
            if (hasDevFeatures()) {
                val interceptor = HttpLoggingInterceptor(logger::debug)
                interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
                addNetworkInterceptor(interceptor)
            }
        }
        .build()
