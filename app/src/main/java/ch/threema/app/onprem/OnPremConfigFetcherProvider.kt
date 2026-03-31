package ch.threema.app.onprem

import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.TimeProvider
import ch.threema.domain.onprem.OnPremConfigFetcher
import ch.threema.domain.onprem.OnPremConfigParser
import ch.threema.domain.onprem.OnPremConfigStore
import ch.threema.domain.onprem.OnPremConfigVerifier
import ch.threema.domain.onprem.OnPremServerConfigParameters
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import okhttp3.OkHttpClient

class OnPremConfigFetcherProvider(
    private val preferenceService: PreferenceService,
    private val onPremConfigParser: OnPremConfigParser,
    private val onPremConfigStore: OnPremConfigStore,
    private val onPremConfigVerifier: OnPremConfigVerifier,
    private val pinnedOkHttpClient: OkHttpClient,
    private val unpinnedOkHttpClient: OkHttpClient,
    private val timeProvider: TimeProvider,
) {

    private var previousServerConfigParameters: OnPremServerConfigParameters? = null
    private var configFetcher: OnPremConfigFetcher? = null

    /**
     * @throws IllegalStateException if no OPPF URL is available
     */
    fun getOnPremConfigFetcher(): OnPremConfigFetcher {
        val oppfUrl = preferenceService.getOppfUrl()
            ?: error("No OPPF URL found in preferences")
        // TODO(ANDR-4428): This error should no longer happen once [SystemUpdateToVersion119] has succeeded

        val serverConfigParameters = OnPremServerConfigParameters(
            oppfUrl = oppfUrl,
            username = try {
                preferenceService.getLicenseUsername()
            } catch (_: MasterKeyLockedException) {
                null
            },
            password = try {
                preferenceService.getLicensePassword()
            } catch (_: MasterKeyLockedException) {
                null
            },
        )
        val previousConfigFetcher = configFetcher
            ?.takeIf { serverConfigParameters == previousServerConfigParameters }
        if (previousConfigFetcher != null) {
            return previousConfigFetcher
        }

        val configFetcher = OnPremConfigFetcher(
            pinnedOkHttpClient = pinnedOkHttpClient,
            unpinnedOkHttpClient = unpinnedOkHttpClient,
            onPremConfigVerifier = onPremConfigVerifier,
            onPremConfigParser = onPremConfigParser,
            onPremConfigStore = onPremConfigStore,
            serverParameters = serverConfigParameters,
            timeProvider = timeProvider,
        )
        this.configFetcher = configFetcher
        this.previousServerConfigParameters = serverConfigParameters
        return configFetcher
    }
}
