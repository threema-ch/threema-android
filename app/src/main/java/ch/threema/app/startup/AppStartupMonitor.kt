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

import kotlinx.coroutines.flow.StateFlow

/**
 * Allows to check and wait for the app to be ready for normal operation.
 */
interface AppStartupMonitor {
    fun isReady(): Boolean

    fun observePendingSystems(): StateFlow<Set<AppSystem>>

    suspend fun awaitSystem(system: AppSystem)

    suspend fun awaitAll()

    fun hasErrors(): Boolean

    fun observeErrors(): StateFlow<Set<AppStartupError>>

    enum class AppSystem {
        DATABASE_UPDATES,
        SYSTEM_UPDATES,
    }

    /**
     * A short error code, which will be shown on the error screen.
     * Meaningless to the user but can be useful for support and devs to identify the cause of the error.
     */
    @JvmInline
    value class AppStartupError(val code: String)
}
