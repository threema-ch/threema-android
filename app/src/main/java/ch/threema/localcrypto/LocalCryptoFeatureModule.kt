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

package ch.threema.localcrypto

import android.os.Build
import ch.threema.app.BuildFlavor
import ch.threema.app.di.Qualifiers
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.onprem.OnPremCertPinning
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.OnPremConfigFetcherProvider
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.domain.models.WorkClientInfo
import ch.threema.domain.onprem.OnPremConfigStore
import java.io.IOException
import java.util.Locale
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

private val logger = getThreemaLogger("LocalCryptoFeatureModule")

private val remoteSecretOkHttpClientQualifier = named("remote-secret")
private val withOnPremCertificatePinningQualifier = named("with-cert-pinning")
private val withoutOnPremCertificatePinningQualifier = named("without-cert-pinning")

val localCryptoFeatureModule = module {
    singleOf(::MasterKeyManagerImpl) bind MasterKeyManager::class

    factoryOf(::MasterKeyCrypto)
    factoryOf(::MasterKeyGenerator)
    factoryOf(::MasterKeyLockStateHolder)
    factoryOf(::MasterKeyStorageStateConverter)
    factoryOf(::MasterKeyStorageStateHolder)
    factoryOf(::PassphraseStore)
    factoryOf(::RemoteSecretMonitor)
    factoryOf(::Version1MasterKeyCrypto)
    factoryOf(::Version2MasterKeyCrypto)
    factoryOf(::Version2MasterKeyStorageDecoder)
    factoryOf(::Version2MasterKeyStorageEncoder)

    factory {
        MasterKeyFileProvider(
            directory = get<AppDirectoryProvider>().appDataDirectory,
        )
    }

    factory<WorkClientInfo> { getClientInfo() }

    factory<MasterKeyStorageManager> {
        val masterKeyFileProvider = get<MasterKeyFileProvider>()
        MasterKeyStorageManager(
            version2KeyFileManager = Version2MasterKeyFileManager(
                keyFile = masterKeyFileProvider.getVersion2MasterKeyFile(),
                encoder = get(),
                decoder = get(),
            ),
            version1KeyFileManager = Version1MasterKeyFileManager(
                keyFile = masterKeyFileProvider.getVersion1MasterKeyFile(),
            ),
            storageStateConverter = get(),
        )
    }

    factory<OkHttpClient>(remoteSecretOkHttpClientQualifier) {
        getOkHttpClientWithCertificatePinning(
            baseOkHttpClient = get(qualifier = Qualifiers.okHttpBase),
            onPremConfigStore = getOrNull(),
            getOnPremConfigFetcherProvider = {
                getOrNull<OnPremConfigFetcherProvider>()
            },
        )
    }
    factory<LibthreemaHttpClient>(withOnPremCertificatePinningQualifier) {
        LibthreemaHttpClient(
            okHttpClient = get(remoteSecretOkHttpClientQualifier),
        )
    }
    factory<LibthreemaHttpClient>(withoutOnPremCertificatePinningQualifier) {
        LibthreemaHttpClient(
            okHttpClient = get(qualifier = Qualifiers.okHttpBase),
        )
    }
    factory<RemoteSecretClient> {
        RemoteSecretClient(
            clientInfo = getClientInfo(),
            httpClientWithOnPremCertPinning = get(withOnPremCertificatePinningQualifier),
            httpClientWithoutOnPremCertPinning = get(withoutOnPremCertificatePinningQualifier),
        )
    }

    factory<RemoteSecretManager> {
        if (ConfigUtils.isOnPremBuild()) {
            RemoteSecretManagerImpl(
                remoteSecretClient = get(),
                remoteSecretMonitor = get(),
                shouldUseRemoteSecretProtection = {
                    AppRestrictionUtil.shouldEnableRemoteSecret(get())
                },
                getWorkServerBaseUrl = {
                    getWorkServerBaseUrl(get())
                },
            )
        } else {
            NoOpRemoteSecretManagerImpl()
        }
    }
}

// TODO(ANDR-4184): Refactor and re-think certificate pinning
private fun getOkHttpClientWithCertificatePinning(
    baseOkHttpClient: OkHttpClient,
    onPremConfigStore: OnPremConfigStore?,
    getOnPremConfigFetcherProvider: () -> OnPremConfigFetcherProvider?,
): OkHttpClient =
    if (onPremConfigStore != null) {
        OnPremCertPinning.createClientWithCertPinning(
            baseClient = baseOkHttpClient,
            getOnPremConfigDomains = {
                val config = onPremConfigStore.get()
                    ?: run {
                        logger.warn("No stored OPPF found, trying to fetch it")
                        val onPremConfigFetcherProvider = getOnPremConfigFetcherProvider()
                            ?: throw IOException("cannot enforce certificate pinning, no OnPremConfigFetcherProvider available to fetch OPPF")
                        try {
                            onPremConfigFetcherProvider.getOnPremConfigFetcher().fetch()
                        } catch (e: ThreemaException) {
                            throw IOException("cannot enforce certificate pinning, failed to fetch OPPF", e)
                        }
                    }
                config.domains
            },
        )
    } else {
        baseOkHttpClient
    }

private fun getWorkServerBaseUrl(onPremConfigStore: OnPremConfigStore?): String =
    onPremConfigStore?.get()?.work?.url
        ?: throw IOException("cannot monitor remote secret, no stored OPPF found")

private fun getClientInfo(): WorkClientInfo =
    WorkClientInfo(
        appVersion = ConfigUtils.getAppVersion(),
        appLocale = Locale.getDefault().toString(),
        deviceModel = Build.MODEL,
        osVersion = Build.VERSION.RELEASE,
        workFlavor = when {
            BuildFlavor.current.isOnPrem -> WorkClientInfo.WorkFlavor.ON_PREM
            BuildFlavor.current.isWork -> WorkClientInfo.WorkFlavor.WORK
            else -> error("Not a work build")
        },
    )
