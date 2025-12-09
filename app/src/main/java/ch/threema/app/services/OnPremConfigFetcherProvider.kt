/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
