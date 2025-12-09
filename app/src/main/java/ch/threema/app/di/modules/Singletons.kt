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

package ch.threema.app.di.modules

import android.annotation.SuppressLint
import ch.threema.app.di.MasterKeyLockStateChangeHandler
import ch.threema.app.di.Qualifiers
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.listeners.PreferenceListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.managers.ListenerProvider
import ch.threema.app.services.NotificationPreferenceService
import ch.threema.app.services.NotificationPreferenceServiceImpl
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.RingtoneServiceImpl
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.services.ServiceManagerProviderImpl
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
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.MasterKeyProvider
import kotlin.time.Duration.Companion.seconds
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
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

    singleOf(::IdentityProviderImpl) binds arrayOf(IdentityProvider::class, MutableIdentityProvider::class)
    singleOf(::NotificationPreferenceServiceImpl) bind NotificationPreferenceService::class
    singleOf(::RingtoneServiceImpl) bind RingtoneService::class

    single<OkHttpClient>(qualifier = Qualifiers.okHttpBase) {
        buildBaseOkHttpClient()
    }

    factory<ThreemaSafeMDMConfig> { ThreemaSafeMDMConfig.getInstance() }
    factory<MasterKeyProvider> { get<MasterKeyManager>().masterKeyProvider }

    singleOf(::ListenerProvider)
}

private fun buildBaseOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .apply {
            connectTimeout(ProtocolDefines.CONNECT_TIMEOUT.seconds)
            writeTimeout(ProtocolDefines.WRITE_TIMEOUT.seconds)
            readTimeout(ProtocolDefines.READ_TIMEOUT.seconds)
            if (ConfigUtils.isDevBuild()) {
                @SuppressLint("LoggerName")
                val okHttpLogger = getThreemaLogger("OkHttp")
                val interceptor = HttpLoggingInterceptor(okHttpLogger::debug)
                interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
                addNetworkInterceptor(interceptor)
            }
        }
        .build()
