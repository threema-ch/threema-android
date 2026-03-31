package ch.threema.localcrypto

import android.os.Build
import ch.threema.app.BuildFlavor
import ch.threema.app.di.Qualifiers
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.onprem.OnPremCertPinning
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.utils.ConfigUtils
import ch.threema.domain.libthreema.LibthreemaHttpClient
import ch.threema.domain.models.WorkClientInfo
import ch.threema.domain.onprem.OnPremConfigStore
import java.util.Locale
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

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
    factoryOf(::KeyStoreSecretKeyManager)
    factoryOf(::KeyStoreCrypto)

    factory {
        MasterKeyFileProvider(
            directory = get<AppDirectoryProvider>().appDataDirectory,
        )
    }

    factory<MasterKeyStorageManager> {
        val masterKeyFileProvider = get<MasterKeyFileProvider>()
        MasterKeyStorageManager(
            version2KeyFileManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Version2MasterKeyFileManagerImpl(
                    keyFile = masterKeyFileProvider.getVersion2KeyStoreProtectedMasterKeyFile(),
                    unencryptedKeyFile = masterKeyFileProvider.getVersion2UnencryptedMasterKeyFile(),
                    encoder = get(),
                    decoder = get(),
                    keyStoreCrypto = get(),
                )
            } else {
                Version2MasterKeyFileManagerAndroid7Impl(
                    legacyKeyFile = masterKeyFileProvider.getVersion2UnencryptedMasterKeyFile(),
                    encoder = get(),
                    decoder = get(),
                )
            },
            version1KeyFileManager = Version1MasterKeyFileManager(
                keyFile = masterKeyFileProvider.getVersion1MasterKeyFile(),
            ),
            storageStateConverter = get(),
        )
    }

    factory<RemoteSecretManager> {
        if (ConfigUtils.isOnPremBuild()) {
            RemoteSecretManagerImpl(
                remoteSecretClient = get(),
                remoteSecretMonitor = get(),
                shouldUseRemoteSecretProtection = {
                    get<AppRestrictions>().isRemoteSecretEnabled()
                },
                getWorkServerBaseUrl = {
                    getWorkServerBaseUrl(get())
                },
            )
        } else {
            NoOpRemoteSecretManagerImpl()
        }
    }

    if (ConfigUtils.isOnPremBuild()) {
        factory<WorkClientInfo> { getWorkClientInfo() }
        factory {
            RemoteSecretClient(
                clientInfo = get(),
                httpClient = LibthreemaHttpClient(
                    // here we need to use the best-effort OkHttp client, as we might end up in a situation where
                    // the certificate pins of the stored OPPF are no longer valid, and we can only recover from this
                    // by fetching the OPPF from the fallback URL.
                    okHttpClient = OnPremCertPinning.createOkHttpClientWithoutRegularOppfFetching(
                        baseClient = get<OkHttpClient>(qualifier = Qualifiers.okHttpBase),
                    ),
                ),
            )
        }
    }
}

private fun getWorkServerBaseUrl(onPremConfigStore: OnPremConfigStore?): String =
    onPremConfigStore?.get()?.work?.url
        ?: error("cannot monitor remote secret, no stored OPPF found")

private fun getWorkClientInfo(): WorkClientInfo =
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
