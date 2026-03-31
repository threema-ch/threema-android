package ch.threema.app.onprem

import ch.threema.app.BuildConfig
import ch.threema.app.di.Qualifiers
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.domain.onprem.OnPremConfigParser
import ch.threema.domain.onprem.OnPremConfigStore
import ch.threema.domain.onprem.OnPremConfigVerifier
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val onPremFeatureModule = module {
    factory<OnPremConfigStore> {
        OnPremConfigStore(
            baseDirectory = get<AppDirectoryProvider>().appDataDirectory,
            timeProvider = get(),
            onPremConfigParser = get(),
            onPremConfigVerifier = get(),
        )
    }

    single<OnPremConfigFetcherProvider> {
        val okHttpBaseClient = get<OkHttpClient>(qualifier = Qualifiers.okHttpBase)
        OnPremConfigFetcherProvider(
            preferenceService = get(),
            onPremConfigParser = get(),
            onPremConfigStore = get(),
            onPremConfigVerifier = get(),
            timeProvider = get(),
            pinnedOkHttpClient = OnPremCertPinning.createOkHttpClientWithoutRegularOppfFetching(
                baseClient = okHttpBaseClient,
                requireCertificatePinning = false,
            ),
            unpinnedOkHttpClient = okHttpBaseClient,
        )
    }

    factoryOf(::OnPremConfigParser)

    single<OnPremConfigVerifier> {
        OnPremConfigVerifier(BuildConfig.ONPREM_CONFIG_TRUSTED_PUBLIC_KEYS)
    }
}
