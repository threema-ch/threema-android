package ch.threema.app.di.modules

import ch.threema.app.di.MasterKeyLockStateChangeHandler
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.listeners.PreferenceListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ListenerProvider
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceServiceImpl
import ch.threema.app.services.FileService
import ch.threema.app.services.FileServiceImpl
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.services.NotificationPreferenceServiceImpl
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.RingtoneServiceImpl
import ch.threema.app.services.ServerMessageService
import ch.threema.app.services.ServerMessageServiceImpl
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.services.ServiceManagerProviderImpl
import ch.threema.app.services.avatarcache.AvatarCacheService
import ch.threema.app.services.avatarcache.AvatarCacheServiceImpl
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.EncryptedPreferenceStoreImpl
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.stores.IdentityProviderImpl
import ch.threema.app.stores.MutableIdentityProvider
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.stores.PreferenceStoreImpl
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.MasterKeyProvider
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Provides access to all the singleton components, i.e., the ones that exist throughout the app's entire lifecycle.
 * These components may hold global state and as such it is important that they are treated as singletons.
 */
val singletonsModule = module {
    singleOf(::ServiceManagerProviderImpl) bind ServiceManagerProvider::class

    singleOf(::AppStartupMonitorImpl) bind AppStartupMonitor::class

    singleOf(::MasterKeyLockStateChangeHandler)

    single<PreferenceStore> {
        PreferenceStoreImpl(
            sharedPreferences = get(),
            onChanged = { key, value ->
                ListenerManager.preferenceListeners.handle { listener: PreferenceListener ->
                    listener.onChanged(key, value)
                }
            },
            commit = false,
        )
    }
    single<EncryptedPreferenceStore> {
        EncryptedPreferenceStoreImpl(
            directory = get<AppDirectoryProvider>().appDataDirectory,
            masterKeyProvider = get(),
            onChanged = { key, value ->
                ListenerManager.preferenceListeners.handle { listener: PreferenceListener ->
                    listener.onChanged(key, value)
                }
            },
        )
    }
    singleOf(::AvatarCacheServiceImpl) bind AvatarCacheService::class
    singleOf(::FileServiceImpl) bind FileService::class
    singleOf(::IdentityProviderImpl) binds arrayOf(IdentityProvider::class, MutableIdentityProvider::class)
    singleOf(::NotificationPreferenceServiceImpl) bind NotificationPreferenceService::class
    singleOf(::PreferenceServiceImpl) bind PreferenceService::class
    singleOf(::RingtoneServiceImpl) bind RingtoneService::class
    singleOf(::ServerMessageServiceImpl) bind ServerMessageService::class

    factory<ThreemaSafeMDMConfig> { ThreemaSafeMDMConfig.getInstance() }
    factory<MasterKeyProvider> { get<MasterKeyManager>().masterKeyProvider }

    singleOf(::ListenerProvider)

    includes(okHttpClientsModule)
}
