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

import ch.threema.app.managers.ServiceManager
import ch.threema.app.services.ServiceManagerProvider
import ch.threema.data.repositories.ModelRepositories
import kotlin.time.Duration
import org.koin.core.component.KoinComponent
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.get
import org.koin.core.module.Module
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

inline fun <reified T : Any> Module.service(noinline bind: ServiceManager.() -> T) {
    factory { get<ServiceManager>().bind() }
}

inline fun <reified T : Any> Module.repository(noinline bind: ModelRepositories.() -> T) {
    factory { get<ServiceManager>().modelRepositories.bind() }
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

suspend inline fun KoinComponent.awaitServiceManagerWithTimeout(timeout: Duration): ServiceManager? =
    get<ServiceManagerProvider>().awaitServiceManagerWithTimeout(timeout)
