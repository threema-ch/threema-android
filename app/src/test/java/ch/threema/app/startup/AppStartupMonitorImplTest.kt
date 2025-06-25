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

import app.cash.turbine.test
import ch.threema.app.systemupdates.SystemUpdateState
import ch.threema.common.stateFlowOf
import ch.threema.storage.DatabaseState
import ch.threema.testhelpers.expectItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class AppStartupMonitorImplTest {
    @Test
    fun `not ready if not initialized`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()

        assertEquals(
            setOf(
                AppStartupMonitor.AppSystem.SYSTEM_UPDATES,
                AppStartupMonitor.AppSystem.DATABASE_UPDATES,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `database not ready and system updates not done`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.init(
            databaseStateFlow = stateFlowOf(DatabaseState.INIT),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.INIT),
        )

        assertEquals(
            setOf(
                AppStartupMonitor.AppSystem.SYSTEM_UPDATES,
                AppStartupMonitor.AppSystem.DATABASE_UPDATES,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `database ready but system updates not done`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.init(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.INIT),
        )

        assertEquals(
            setOf(
                AppStartupMonitor.AppSystem.SYSTEM_UPDATES,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `database not ready but system updates done`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.init(
            databaseStateFlow = stateFlowOf(DatabaseState.PREPARING),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )

        assertEquals(
            setOf(
                AppStartupMonitor.AppSystem.DATABASE_UPDATES,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `database ready and system updates done`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.init(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )

        assertEquals(
            emptySet(),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertTrue(appStartupMonitor.isReady())
    }

    @Test
    fun `not ready after reset`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.init(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )
        appStartupMonitor.reset()

        assertEquals(
            setOf(
                AppStartupMonitor.AppSystem.SYSTEM_UPDATES,
                AppStartupMonitor.AppSystem.DATABASE_UPDATES,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `observing pending systems`() = runTest {
        val databaseStateFlow = MutableStateFlow(DatabaseState.INIT)
        val systemUpdateState = MutableStateFlow(SystemUpdateState.INIT)
        val appStartupMonitor = AppStartupMonitorImpl()

        appStartupMonitor.observePendingSystems().test {
            expectItem(
                setOf(
                    AppStartupMonitor.AppSystem.DATABASE_UPDATES,
                    AppStartupMonitor.AppSystem.SYSTEM_UPDATES,
                ),
            )

            appStartupMonitor.init(
                databaseStateFlow = databaseStateFlow,
                systemUpdateStateFlow = systemUpdateState,
            )

            databaseStateFlow.value = DatabaseState.READY
            expectItem(setOf(AppStartupMonitor.AppSystem.SYSTEM_UPDATES))

            systemUpdateState.value = SystemUpdateState.READY
            expectItem(emptySet())

            appStartupMonitor.reset()
            expectItem(
                setOf(
                    AppStartupMonitor.AppSystem.DATABASE_UPDATES,
                    AppStartupMonitor.AppSystem.SYSTEM_UPDATES,
                ),
            )

            val databaseStateFlow2 = MutableStateFlow(DatabaseState.PREPARING)
            val systemUpdateState2 = MutableStateFlow(SystemUpdateState.READY)
            appStartupMonitor.init(
                databaseStateFlow = databaseStateFlow2,
                systemUpdateStateFlow = systemUpdateState2,
            )
            expectItem(setOf(AppStartupMonitor.AppSystem.DATABASE_UPDATES))

            databaseStateFlow2.value = DatabaseState.READY
            expectItem(emptySet())
        }
    }

    @Test
    fun `error reporting`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.init(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )
        assertFalse(appStartupMonitor.hasErrors())

        appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("TEST1"))
        appStartupMonitor.reportAppStartupError(AppStartupMonitor.AppStartupError("TEST2"))

        assertTrue(appStartupMonitor.hasErrors())
        assertEquals(
            setOf(
                AppStartupMonitor.AppStartupError("TEST1"),
                AppStartupMonitor.AppStartupError("TEST2"),
            ),
            appStartupMonitor.observeErrors().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }
}
