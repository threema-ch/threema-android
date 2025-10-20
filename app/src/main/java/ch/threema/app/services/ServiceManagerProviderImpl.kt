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

import ch.threema.app.managers.ServiceManager
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.common.awaitNonNull
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

class ServiceManagerProviderImpl(
    private val appStartupMonitor: AppStartupMonitor,
) : ServiceManagerProvider {
    private val _serviceManagerFlow = MutableStateFlow<ServiceManager?>(null)
    override val serviceManagerFlow = _serviceManagerFlow.asStateFlow()

    fun setServiceManager(serviceManager: ServiceManager?) {
        _serviceManagerFlow.value = serviceManager
    }

    override fun getServiceManager(): ServiceManager =
        getServiceManagerOrNull() ?: error("ServiceManager not available, master key still locked?")

    override fun getServiceManagerOrNull(): ServiceManager? =
        serviceManagerFlow.value

    override suspend fun awaitServiceManager(): ServiceManager {
        appStartupMonitor.awaitAll()
        return serviceManagerFlow.awaitNonNull()
    }

    override suspend fun awaitServiceManagerWithTimeout(timeout: Duration): ServiceManager? = withTimeoutOrNull(timeout) {
        appStartupMonitor.awaitAll()
        serviceManagerFlow.awaitNonNull()
    }
}
