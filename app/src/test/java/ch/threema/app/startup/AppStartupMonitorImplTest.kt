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
import ch.threema.app.startup.models.AppSystem
import ch.threema.app.startup.models.SystemStatus
import ch.threema.app.systemupdates.SystemUpdateState
import ch.threema.common.stateFlowOf
import ch.threema.storage.DatabaseState
import ch.threema.testhelpers.expectItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class AppStartupMonitorImplTest {
    @Test
    fun `not ready before onServiceManagerReady is called`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.UNKNOWN,
                AppSystem.SERVICE_MANAGER to SystemStatus.PENDING,
                AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
            ),
            appStartupMonitor.observeSystems().first(),
        )
        assertEquals(
            setOf(
                AppSystem.SERVICE_MANAGER,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `service manager ready after onServiceManagerReady is called, database and system updates not ready`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onServiceManagerReady(
            databaseStateFlow = stateFlowOf(DatabaseState.INIT),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.INIT),
        )

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.READY,
                AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                AppSystem.SYSTEM_UPDATES to SystemStatus.PENDING,
                AppSystem.DATABASE_UPDATES to SystemStatus.PENDING,
            ),
            appStartupMonitor.observeSystems().first(),
        )
        assertEquals(
            setOf(
                AppSystem.SYSTEM_UPDATES,
                AppSystem.DATABASE_UPDATES,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `database and service manager ready but system updates not done`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onServiceManagerReady(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.INIT),
        )

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.READY,
                AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                AppSystem.SYSTEM_UPDATES to SystemStatus.PENDING,
                AppSystem.DATABASE_UPDATES to SystemStatus.READY,
            ),
            appStartupMonitor.observeSystems().first(),
        )
        assertEquals(
            setOf(
                AppSystem.SYSTEM_UPDATES,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `database not ready but system updates done`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onServiceManagerReady(
            databaseStateFlow = stateFlowOf(DatabaseState.PREPARING),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.READY,
                AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                AppSystem.SYSTEM_UPDATES to SystemStatus.READY,
                AppSystem.DATABASE_UPDATES to SystemStatus.PENDING,
            ),
            appStartupMonitor.observeSystems().first(),
        )
        assertEquals(
            setOf(
                AppSystem.DATABASE_UPDATES,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `everything is ready`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onServiceManagerReady(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.READY,
                AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                AppSystem.SYSTEM_UPDATES to SystemStatus.READY,
                AppSystem.DATABASE_UPDATES to SystemStatus.READY,
            ),
            appStartupMonitor.observeSystems().first(),
        )
        assertEquals(
            emptySet(),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertTrue(appStartupMonitor.isReady())
    }

    @Test
    fun `not ready after onServiceManagerDestroyed is called`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onServiceManagerReady(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )
        appStartupMonitor.onServiceManagerDestroyed()

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.UNKNOWN,
                AppSystem.SERVICE_MANAGER to SystemStatus.PENDING,
                AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
            ),
            appStartupMonitor.observeSystems().first(),
        )
        assertEquals(
            setOf(
                AppSystem.SERVICE_MANAGER,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
    }

    @Test
    fun `observing systems`() = runTest {
        val databaseStateFlow = MutableStateFlow(DatabaseState.INIT)
        val systemUpdateState = MutableStateFlow(SystemUpdateState.INIT)
        val appStartupMonitor = AppStartupMonitorImpl()

        appStartupMonitor.observeSystems().test {
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.UNKNOWN,
                    AppSystem.SERVICE_MANAGER to SystemStatus.PENDING,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                    AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                ),
            )

            launch {
                appStartupMonitor.whileFetchingRemoteSecret {
                    delay(1.seconds)
                }
            }
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.PENDING,
                    AppSystem.SERVICE_MANAGER to SystemStatus.PENDING,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                    AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                ),
            )
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.SERVICE_MANAGER to SystemStatus.PENDING,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                    AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                ),
            )

            appStartupMonitor.onServiceManagerReady(
                databaseStateFlow = databaseStateFlow,
                systemUpdateStateFlow = systemUpdateState,
            )
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.PENDING,
                    AppSystem.DATABASE_UPDATES to SystemStatus.PENDING,
                ),
            )

            databaseStateFlow.value = DatabaseState.READY
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.PENDING,
                    AppSystem.DATABASE_UPDATES to SystemStatus.READY,
                ),
            )

            systemUpdateState.value = SystemUpdateState.READY
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.READY,
                    AppSystem.DATABASE_UPDATES to SystemStatus.READY,
                ),
            )

            appStartupMonitor.onServiceManagerDestroyed()
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.UNKNOWN,
                    AppSystem.SERVICE_MANAGER to SystemStatus.PENDING,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                    AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                ),
            )

            val databaseStateFlow2 = MutableStateFlow(DatabaseState.PREPARING)
            val systemUpdateState2 = MutableStateFlow(SystemUpdateState.READY)
            appStartupMonitor.onServiceManagerReady(
                databaseStateFlow = databaseStateFlow2,
                systemUpdateStateFlow = systemUpdateState2,
            )
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.READY,
                    AppSystem.DATABASE_UPDATES to SystemStatus.PENDING,
                ),
            )

            databaseStateFlow2.value = DatabaseState.READY
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.SERVICE_MANAGER to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.READY,
                    AppSystem.DATABASE_UPDATES to SystemStatus.READY,
                ),
            )
        }
    }

    @Test
    fun `observing pending systems`() = runTest {
        val databaseStateFlow = MutableStateFlow(DatabaseState.INIT)
        val systemUpdateState = MutableStateFlow(SystemUpdateState.INIT)
        val appStartupMonitor = AppStartupMonitorImpl()

        appStartupMonitor.observePendingSystems().test {
            expectItem(
                setOf(
                    AppSystem.SERVICE_MANAGER,
                ),
            )

            launch {
                appStartupMonitor.whileFetchingRemoteSecret {
                    delay(1.seconds)
                }
            }
            expectItem(
                setOf(
                    AppSystem.REMOTE_SECRET,
                    AppSystem.SERVICE_MANAGER,
                ),
            )
            expectItem(
                setOf(
                    AppSystem.SERVICE_MANAGER,
                ),
            )

            appStartupMonitor.onServiceManagerReady(
                databaseStateFlow = databaseStateFlow,
                systemUpdateStateFlow = systemUpdateState,
            )
            expectItem(
                setOf(
                    AppSystem.SYSTEM_UPDATES,
                    AppSystem.DATABASE_UPDATES,
                ),
            )

            databaseStateFlow.value = DatabaseState.READY
            expectItem(
                setOf(
                    AppSystem.SYSTEM_UPDATES,
                ),
            )

            systemUpdateState.value = SystemUpdateState.READY
            expectItem(
                emptySet(),
            )

            appStartupMonitor.onServiceManagerDestroyed()
            expectItem(
                setOf(
                    AppSystem.SERVICE_MANAGER,
                ),
            )

            val databaseStateFlow2 = MutableStateFlow(DatabaseState.PREPARING)
            val systemUpdateState2 = MutableStateFlow(SystemUpdateState.READY)
            appStartupMonitor.onServiceManagerReady(
                databaseStateFlow = databaseStateFlow2,
                systemUpdateStateFlow = systemUpdateState2,
            )
            expectItem(
                setOf(
                    AppSystem.DATABASE_UPDATES,
                ),
            )

            databaseStateFlow2.value = DatabaseState.READY
            expectItem(
                emptySet(),
            )
        }
    }

    @Test
    fun `error reporting`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onServiceManagerReady(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )
        assertFalse(appStartupMonitor.hasErrors())

        appStartupMonitor.reportUnexpectedAppStartupError("TEST1")
        appStartupMonitor.reportAppStartupError(AppStartupError.FailedToFetchRemoteSecret)
        appStartupMonitor.reportUnexpectedAppStartupError("TEST2")

        assertTrue(appStartupMonitor.hasErrors())
        assertEquals(
            setOf(
                AppStartupError.Unexpected("TEST1"),
                AppStartupError.FailedToFetchRemoteSecret,
                AppStartupError.Unexpected("TEST2"),
            ),
            appStartupMonitor.observeErrors().first(),
        )
        assertFalse(appStartupMonitor.isReady())

        appStartupMonitor.clearTemporaryStartupErrors()
        assertEquals(
            setOf(
                AppStartupError.Unexpected("TEST1"),
                AppStartupError.Unexpected("TEST2"),
            ),
            appStartupMonitor.observeErrors().first(),
        )
    }
}
