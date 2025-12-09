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
import kotlinx.coroutines.flow.StateFlow

/**
 * Allows to check and wait for the app to be ready for normal operation.
 */
interface AppStartupMonitor {
    /**
     * Check whether the app is considered ready, i.e., all systems are considered ready (as opposed to unknown or pending) AND there are no errors.
     */
    fun isReady(): Boolean

    /**
     * Check whether a specific system is currently considered ready AND there are no errors.
     */
    fun isReady(system: AppSystem): Boolean

    /**
     * Returns a flow that indicates all the systems and what status they are currently in.
     */
    fun observeSystems(): StateFlow<Map<AppSystem, SystemStatus>>

    /**
     * Returns a flow that indicates which system are currently pending.
     *
     * A system may initially not be considered pending and then become pending later, e.g., when it depends on another system.
     *
     * If an empty set is emitted, it is guaranteed that the app has reached a full ready state and can be used normally. At this point,
     * systems will only enter the pending status again if the master key is locked.
     */
    fun observePendingSystems(): StateFlow<Set<AppSystem>>

    suspend fun awaitSystem(system: AppSystem)

    /**
     * Wait for the app to become ready.
     * This will suspend forever if there are errors.
     */
    suspend fun awaitAll()

    fun hasErrors(): Boolean

    fun observeErrors(): StateFlow<Set<AppStartupError>>
}
