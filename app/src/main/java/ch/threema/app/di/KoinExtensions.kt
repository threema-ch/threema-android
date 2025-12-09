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

package ch.threema.app.di

import android.content.ComponentCallbacks
import ch.threema.app.managers.ServiceManager
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.models.AppSystem
import ch.threema.data.repositories.ModelRepositories
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.getKoin
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.koin.core.module.Module
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

/**
 * Returns true if the session scope is ready, meaning that the master key is unlocked and components that depend on it (directly or indirectly)
 * can be safely requested from Koin.
 *
 * Use [DIJavaCompat.isSessionScopeReady] if you need this check in Java.
 *
 * Note that the app may still not be fully ready for the user, e.g. due to pending database updates.
 * Use [awaitAppFullyReady] or [awaitAppFullyReadyWithTimeout] to wait for the app to become fully ready.
 */
fun KoinComponent.isSessionScopeReady(): Boolean =
    getKoin().isSessionScopeReady()

/**
 * See [KoinComponent.isSessionScopeReady]
 */
fun ComponentCallbacks.isSessionScopeReady(): Boolean =
    getKoin().isSessionScopeReady()

/**
 * See [KoinComponent.isSessionScopeReady]
 */
fun Koin.isSessionScopeReady(): Boolean =
    get<AppStartupMonitor>().isReady(AppSystem.UNLOCKED_MASTER_KEY)

/**
 * Waits until the session scope is ready.
 * See [KoinComponent.isSessionScopeReady] for more information.
 */
suspend fun KoinComponent.awaitSessionScopeReady() {
    get<AppStartupMonitor>().awaitSystem(AppSystem.UNLOCKED_MASTER_KEY)
}

/**
 * Waits until the app is fully ready for normal operations, meaning that all pending database updates, system updates etc. have completed
 * successfully.
 */
suspend inline fun KoinComponent.awaitAppFullyReady() {
    get<AppStartupMonitor>().awaitAll()
}

/**
 * See [awaitAppFullyReady]
 */
suspend inline fun ComponentCallbacks.awaitAppFullyReady() {
    getKoin().get<AppStartupMonitor>().awaitAll()
}

/**
 * See [awaitAppFullyReady] for details.
 *
 * Waits at most [timeout]. Returns null if the timeout is exceeded for convenient early returns using the ?: operator.
 */
suspend inline fun KoinComponent.awaitAppFullyReadyWithTimeout(timeout: Duration): Unit? =
    withTimeoutOrNull(timeout) {
        awaitAppFullyReady()
    }

inline fun <reified T : Any> Module.service(noinline bind: ServiceManager.() -> T) {
    factory<T?> { getOrNull<ServiceManager>()?.bind() }
}

inline fun <reified T : Any> Module.repository(noinline bind: ModelRepositories.() -> T) {
    factory<T?> { getOrNull<ServiceManager>()?.modelRepositories?.bind() }
}

inline fun <reified T : Any> KoinComponent.getOrNull(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null,
): T? =
    if (this is KoinScopeComponent) {
        scope.getOrNull(qualifier, parameters)
    } else {
        getKoin().getOrNull(qualifier, parameters)
    }

/**
 * Use this instead of [inject] in places where a dependency needs to be lazily evaluated but no reference should be kept to it, i.e.,
 * a new lookup should be made every time the dependency is accessed. This is mainly useful when accessing session-scoped components
 * from a component that might live longer than the session itself, i.e., one that might survive the locking of the master key,
 * such as a long-running background service.
 */
inline fun <reified T : Any> KoinComponent.injectNonBinding(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null,
) = Delegate {
    get<T>(qualifier, parameters)
}

class Delegate<T>(val getValue: () -> T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = getValue()
}
