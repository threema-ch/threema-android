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
import ch.threema.app.MasterKeyManagerFactory
import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.di.MasterKeyLockStateChangeHandler
import ch.threema.app.di.Qualifiers
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.app.services.ServiceManagerProviderImpl
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.stores.IdentityProviderImpl
import ch.threema.app.stores.MutableIdentityProvider
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.stores.PreferenceStoreImpl
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.localcrypto.MasterKeyManager
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

    single {
        MasterKeyManagerFactory.createMasterKeyManager(
            context = get(),
            baseOkHttpClient = get(qualifier = Qualifiers.okHttpBase),
            onPremConfigStore = getOrNull(),
            serviceManagerProvider = get(),
        )
    } bind MasterKeyManager::class

    singleOf(::MasterKeyLockStateChangeHandler)

    single {
        // TODO(ANDR-4220): Use singleOf here
        AppTaskExecutor(
            dispatcherProvider = get(),
            appStartupMonitor = get(),
            serviceManagerProvider = get(),
        )
    }

    single {
        PreferenceStoreImpl(
            sharedPreferences = get(),
        )
    } bind PreferenceStore::class

    singleOf(::IdentityProviderImpl) binds arrayOf(IdentityProvider::class, MutableIdentityProvider::class)

    single(qualifier = Qualifiers.okHttpBase) {
        buildBaseOkHttpClient()
    }

    factory { ThreemaSafeMDMConfig.getInstance() }
}

private fun buildBaseOkHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .apply {
            connectTimeout(ProtocolDefines.CONNECT_TIMEOUT.seconds)
            writeTimeout(ProtocolDefines.WRITE_TIMEOUT.seconds)
            readTimeout(ProtocolDefines.READ_TIMEOUT.seconds)
            if (ConfigUtils.isDevBuild()) {
                @SuppressLint("LoggerName")
                val okHttpLogger = LoggingUtil.getThreemaLogger("OkHttp")
                val interceptor = HttpLoggingInterceptor(okHttpLogger::debug)
                interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC)
                addNetworkInterceptor(interceptor)
            }
        }
        .build()
