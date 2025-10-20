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

package ch.threema.app.startup

import ch.threema.app.startup.models.AppSystem
import ch.threema.app.startup.models.SystemStatus
import ch.threema.app.systemupdates.SystemUpdateState
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.DelegateStateFlow
import ch.threema.common.combineStates
import ch.threema.common.mapState
import ch.threema.common.stateFlowOf
import ch.threema.storage.DatabaseState
import kotlin.collections.component1
import kotlin.collections.component2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

private val logger = LoggingUtil.getThreemaLogger("AppStartupMonitorImpl")

class AppStartupMonitorImpl : AppStartupMonitor {
    private val serviceManagerStateFlow = MutableStateFlow(SystemStatus.PENDING)
    private val remoteSecretStateFlow = MutableStateFlow(SystemStatus.UNKNOWN)
    private val databaseStateFlow = DelegateStateFlow(stateFlowOf(SystemStatus.UNKNOWN))
    private val systemUpdateStateFlow = DelegateStateFlow(stateFlowOf(SystemStatus.UNKNOWN))
    private val systemStatuses = DelegateStateFlow(createInitialPendingSystemsFlow())

    private fun createInitialPendingSystemsFlow() =
        remoteSecretStateFlow.mapState { remoteSecretState ->
            mapOf(
                AppSystem.REMOTE_SECRET to remoteSecretState,
                AppSystem.SERVICE_MANAGER to SystemStatus.PENDING,
                AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
            )
        }

    private val errors = MutableStateFlow(emptySet<AppStartupError>())

    suspend fun whileFetchingRemoteSecret(block: suspend () -> Unit) {
        remoteSecretStateFlow.value = SystemStatus.PENDING
        block()
        remoteSecretStateFlow.value = SystemStatus.READY
    }

    fun onServiceManagerReady(
        databaseStateFlow: StateFlow<DatabaseState>,
        systemUpdateStateFlow: StateFlow<SystemUpdateState>,
    ) {
        this.serviceManagerStateFlow.value = SystemStatus.READY
        this.databaseStateFlow.delegate = databaseStateFlow.mapState { databaseState ->
            databaseState.toSystemStatus()
        }
        this.systemUpdateStateFlow.delegate = systemUpdateStateFlow.mapState { systemUpdateState ->
            systemUpdateState.toSystemStatus()
        }

        systemStatuses.delegate = combineStates(
            databaseStateFlow,
            systemUpdateStateFlow,
        ) { databaseState, systemUpdateState ->
            buildMap {
                put(AppSystem.SERVICE_MANAGER, SystemStatus.READY)
                put(AppSystem.REMOTE_SECRET, SystemStatus.READY)
                put(AppSystem.DATABASE_UPDATES, databaseState.toSystemStatus())
                put(AppSystem.SYSTEM_UPDATES, systemUpdateState.toSystemStatus())
            }
        }

        // the ServiceManager becoming ready implies that the Remote Secret is also ready
        this.remoteSecretStateFlow.value = SystemStatus.READY
    }

    private fun DatabaseState.toSystemStatus() =
        when (this) {
            DatabaseState.INIT,
            DatabaseState.PREPARING,
            -> SystemStatus.PENDING
            DatabaseState.READY -> SystemStatus.READY
        }

    private fun SystemUpdateState.toSystemStatus() =
        when (this) {
            SystemUpdateState.INIT,
            SystemUpdateState.PREPARING,
            -> SystemStatus.PENDING
            SystemUpdateState.READY -> SystemStatus.READY
        }

    fun onServiceManagerDestroyed() {
        // Reset to a static state flow first so that the following updates appear atomic from the outside
        systemStatuses.delegate = stateFlowOf(
            mapOf(
                AppSystem.SERVICE_MANAGER to SystemStatus.PENDING,
                AppSystem.REMOTE_SECRET to SystemStatus.UNKNOWN,
                AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
            ),
        )

        serviceManagerStateFlow.value = SystemStatus.PENDING
        remoteSecretStateFlow.value = SystemStatus.UNKNOWN
        databaseStateFlow.delegate = stateFlowOf(SystemStatus.UNKNOWN)
        systemUpdateStateFlow.delegate = stateFlowOf(SystemStatus.UNKNOWN)
        systemStatuses.delegate = createInitialPendingSystemsFlow()
    }

    override fun observeSystems() = systemStatuses

    override fun observePendingSystems(): StateFlow<Set<AppSystem>> =
        systemStatuses.mapState { statuses ->
            buildSet {
                statuses.forEach { (system, status) ->
                    if (status == SystemStatus.PENDING) {
                        add(system)
                    }
                }
            }
        }

    override fun isReady(): Boolean =
        systemStatuses.value.all { (_, status) -> status == SystemStatus.READY } && errors.value.isEmpty()

    override suspend fun awaitSystem(system: AppSystem) {
        systemStatuses.first { statuses ->
            statuses[system] == SystemStatus.READY
        }
    }

    /**
     * Waits for all systems to be ready.
     * If an app startup error is reported, this will never return, as the app is never considered "ready" in that case.
     */
    override suspend fun awaitAll() {
        systemStatuses.first { statuses ->
            statuses.all { (_, status) ->
                status == SystemStatus.READY
            }
        }
        errors.first { it.isEmpty() }
    }

    override fun observeErrors(): StateFlow<Set<AppStartupError>> = errors

    override fun hasErrors() = errors.value.isNotEmpty()

    /**
     * Report an error that occurred during the startup sequence of the app, which prevents the app from being used.
     */
    fun reportAppStartupError(error: AppStartupError) {
        logger.warn("Startup error reported, {}", error)
        errors.update { it + error }
    }

    fun reportUnexpectedAppStartupError(code: String) {
        reportAppStartupError(AppStartupError.Unexpected(code))
    }

    fun clearTemporaryStartupErrors() {
        errors.update { it.filterNot(AppStartupError::isTemporary).toSet() }
    }
}
