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

import ch.threema.app.systemupdates.SystemUpdateState
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.DelegateStateFlow
import ch.threema.common.await
import ch.threema.common.combineStates
import ch.threema.common.stateFlowOf
import ch.threema.storage.DatabaseState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

private val logger = LoggingUtil.getThreemaLogger("AppStartupMonitorImpl")

class AppStartupMonitorImpl : AppStartupMonitor {
    private val databaseStateFlow = DelegateStateFlow(stateFlowOf(DatabaseState.INIT))
    private val systemUpdateStateFlow = DelegateStateFlow(stateFlowOf(SystemUpdateState.INIT))
    private val pendingSystems = DelegateStateFlow(stateFlowOf(allSystems()))

    private val errors = MutableStateFlow(emptySet<AppStartupMonitor.AppStartupError>())

    fun init(databaseStateFlow: StateFlow<DatabaseState>, systemUpdateStateFlow: StateFlow<SystemUpdateState>) {
        this.databaseStateFlow.delegate = databaseStateFlow
        this.systemUpdateStateFlow.delegate = systemUpdateStateFlow

        pendingSystems.delegate = combineStates(
            databaseStateFlow,
            systemUpdateStateFlow,
        ) { databaseState, systemUpdateState ->
            buildSet {
                if (databaseState != DatabaseState.READY) {
                    add(AppStartupMonitor.AppSystem.DATABASE_UPDATES)
                }
                if (systemUpdateState != SystemUpdateState.READY) {
                    add(AppStartupMonitor.AppSystem.SYSTEM_UPDATES)
                }
            }
        }
    }

    fun reset() {
        pendingSystems.delegate = stateFlowOf(allSystems())
        databaseStateFlow.delegate = stateFlowOf(DatabaseState.INIT)
        systemUpdateStateFlow.delegate = stateFlowOf(SystemUpdateState.INIT)
    }

    override fun observePendingSystems() = pendingSystems

    override fun isReady(): Boolean =
        databaseStateFlow.value == DatabaseState.READY &&
            systemUpdateStateFlow.value == SystemUpdateState.READY &&
            errors.value.isEmpty()

    override suspend fun awaitSystem(system: AppStartupMonitor.AppSystem) {
        when (system) {
            AppStartupMonitor.AppSystem.DATABASE_UPDATES -> databaseStateFlow.await(DatabaseState.READY)
            AppStartupMonitor.AppSystem.SYSTEM_UPDATES -> systemUpdateStateFlow.await(SystemUpdateState.READY)
        }
    }

    /**
     * Waits for all systems to be ready.
     * If an app startup error is reported, this will never return, as the app is never considered "ready" in that case.
     */
    override suspend fun awaitAll() {
        AppStartupMonitor.AppSystem.entries.forEach { system ->
            awaitSystem(system)
        }
        errors.first { it.isEmpty() }
    }

    override fun observeErrors(): StateFlow<Set<AppStartupMonitor.AppStartupError>> = errors

    override fun hasErrors() = errors.value.isNotEmpty()

    /**
     * Report an error that occurred during the startup sequence of the app, which prevents the app from being used.
     */
    fun reportAppStartupError(code: AppStartupMonitor.AppStartupError) {
        logger.warn("Startup error reported, code {}", code)
        errors.update { it + code }
    }

    companion object {
        private fun allSystems() = AppStartupMonitor.AppSystem.entries.toSet()
    }
}
