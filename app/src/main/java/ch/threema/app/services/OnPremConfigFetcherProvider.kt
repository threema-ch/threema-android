package ch.threema.app.services

import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.domain.onprem.OnPremConfigFetcher
import ch.threema.domain.onprem.OnPremConfigParser
import ch.threema.domain.onprem.OnPremConfigStore
import ch.threema.domain.onprem.OnPremConfigVerifier
import ch.threema.domain.onprem.OnPremServerConfigParameters
import okhttp3.OkHttpClient

@SessionScoped
class OnPremConfigFetcherProvider(
    private val preferenceService: PreferenceService,
    private val onPremConfigParser: OnPremConfigParser = OnPremConfigParser(),
    private val onPremConfigStore: OnPremConfigStore,
    private val okHttpClient: OkHttpClient,
    private val trustedPublicKeys: Array<String>,
) {
    private var previousServerConfigParameters: OnPremServerConfigParameters? = null
    private var configFetcher: OnPremConfigFetcher? = null

    @Throws(ThreemaException::class)
    fun getOnPremConfigFetcher(): OnPremConfigFetcher {
        val serverConfigParameters = OnPremServerConfigParameters(
            url = preferenceService.onPremServer ?: throw ThreemaException("No on prem server URL found in preferences"),
            username = preferenceService.licenseUsername,
            password = preferenceService.licensePassword,
        )
        val previousConfigFetcher = configFetcher
            ?.takeIf { serverConfigParameters == previousServerConfigParameters }
        if (previousConfigFetcher != null) {
            return previousConfigFetcher
        }

        val configFetcher = OnPremConfigFetcher(
            okHttpClient = okHttpClient,
            onPremConfigVerifier = OnPremConfigVerifier(trustedPublicKeys),
            onPremConfigParser = onPremConfigParser,
            onPremConfigStore = onPremConfigStore,
            serverParameters = serverConfigParameters,
        )
        this.configFetcher = configFetcher
        this.previousServerConfigParameters = serverConfigParameters
        return configFetcher
    }
}
