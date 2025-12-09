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
    fun `not ready before onMasterKeyUnlocked is called`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.UNKNOWN,
                AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.PENDING,
                AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
            ),
            appStartupMonitor.observeSystems().first(),
        )
        assertEquals(
            setOf(
                AppSystem.UNLOCKED_MASTER_KEY,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
        assertFalse(appStartupMonitor.isReady(AppSystem.REMOTE_SECRET))
        assertFalse(appStartupMonitor.isReady(AppSystem.UNLOCKED_MASTER_KEY))
        assertFalse(appStartupMonitor.isReady(AppSystem.SYSTEM_UPDATES))
        assertFalse(appStartupMonitor.isReady(AppSystem.DATABASE_UPDATES))
    }

    @Test
    fun `master key ready after onMasterKeyUnlocked is called, database and system updates not ready`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onMasterKeyUnlocked(
            databaseStateFlow = stateFlowOf(DatabaseState.INIT),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.INIT),
        )

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.READY,
                AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
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
        assertTrue(appStartupMonitor.isReady(AppSystem.REMOTE_SECRET))
        assertTrue(appStartupMonitor.isReady(AppSystem.UNLOCKED_MASTER_KEY))
        assertFalse(appStartupMonitor.isReady(AppSystem.SYSTEM_UPDATES))
        assertFalse(appStartupMonitor.isReady(AppSystem.DATABASE_UPDATES))
    }

    @Test
    fun `database and master key ready but system updates not done`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onMasterKeyUnlocked(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.INIT),
        )

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.READY,
                AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
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
        assertTrue(appStartupMonitor.isReady(AppSystem.REMOTE_SECRET))
        assertTrue(appStartupMonitor.isReady(AppSystem.UNLOCKED_MASTER_KEY))
        assertFalse(appStartupMonitor.isReady(AppSystem.SYSTEM_UPDATES))
        assertTrue(appStartupMonitor.isReady(AppSystem.DATABASE_UPDATES))
    }

    @Test
    fun `database not ready but system updates done`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onMasterKeyUnlocked(
            databaseStateFlow = stateFlowOf(DatabaseState.PREPARING),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.READY,
                AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
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
        assertTrue(appStartupMonitor.isReady(AppSystem.REMOTE_SECRET))
        assertTrue(appStartupMonitor.isReady(AppSystem.UNLOCKED_MASTER_KEY))
        assertTrue(appStartupMonitor.isReady(AppSystem.SYSTEM_UPDATES))
        assertFalse(appStartupMonitor.isReady(AppSystem.DATABASE_UPDATES))
    }

    @Test
    fun `everything is ready`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onMasterKeyUnlocked(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.READY,
                AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
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
        assertTrue(appStartupMonitor.isReady(AppSystem.REMOTE_SECRET))
        assertTrue(appStartupMonitor.isReady(AppSystem.UNLOCKED_MASTER_KEY))
        assertTrue(appStartupMonitor.isReady(AppSystem.SYSTEM_UPDATES))
        assertTrue(appStartupMonitor.isReady(AppSystem.DATABASE_UPDATES))
    }

    @Test
    fun `not ready after onMasterKeyLocked is called`() = runTest {
        val appStartupMonitor = AppStartupMonitorImpl()
        appStartupMonitor.onMasterKeyUnlocked(
            databaseStateFlow = stateFlowOf(DatabaseState.READY),
            systemUpdateStateFlow = stateFlowOf(SystemUpdateState.READY),
        )
        appStartupMonitor.onMasterKeyLocked()

        assertEquals(
            mapOf(
                AppSystem.REMOTE_SECRET to SystemStatus.UNKNOWN,
                AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.PENDING,
                AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
            ),
            appStartupMonitor.observeSystems().first(),
        )
        assertEquals(
            setOf(
                AppSystem.UNLOCKED_MASTER_KEY,
            ),
            appStartupMonitor.observePendingSystems().first(),
        )
        assertFalse(appStartupMonitor.isReady())
        assertFalse(appStartupMonitor.isReady(AppSystem.REMOTE_SECRET))
        assertFalse(appStartupMonitor.isReady(AppSystem.UNLOCKED_MASTER_KEY))
        assertFalse(appStartupMonitor.isReady(AppSystem.SYSTEM_UPDATES))
        assertFalse(appStartupMonitor.isReady(AppSystem.DATABASE_UPDATES))
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
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.PENDING,
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
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.PENDING,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                    AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                ),
            )
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.PENDING,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                    AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                ),
            )

            appStartupMonitor.onMasterKeyUnlocked(
                databaseStateFlow = databaseStateFlow,
                systemUpdateStateFlow = systemUpdateState,
            )
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.PENDING,
                    AppSystem.DATABASE_UPDATES to SystemStatus.PENDING,
                ),
            )

            databaseStateFlow.value = DatabaseState.READY
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.PENDING,
                    AppSystem.DATABASE_UPDATES to SystemStatus.READY,
                ),
            )

            systemUpdateState.value = SystemUpdateState.READY
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.READY,
                    AppSystem.DATABASE_UPDATES to SystemStatus.READY,
                ),
            )

            appStartupMonitor.onMasterKeyLocked()
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.UNKNOWN,
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.PENDING,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.UNKNOWN,
                    AppSystem.DATABASE_UPDATES to SystemStatus.UNKNOWN,
                ),
            )

            val databaseStateFlow2 = MutableStateFlow(DatabaseState.PREPARING)
            val systemUpdateState2 = MutableStateFlow(SystemUpdateState.READY)
            appStartupMonitor.onMasterKeyUnlocked(
                databaseStateFlow = databaseStateFlow2,
                systemUpdateStateFlow = systemUpdateState2,
            )
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
                    AppSystem.SYSTEM_UPDATES to SystemStatus.READY,
                    AppSystem.DATABASE_UPDATES to SystemStatus.PENDING,
                ),
            )

            databaseStateFlow2.value = DatabaseState.READY
            expectItem(
                mapOf(
                    AppSystem.REMOTE_SECRET to SystemStatus.READY,
                    AppSystem.UNLOCKED_MASTER_KEY to SystemStatus.READY,
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
                    AppSystem.UNLOCKED_MASTER_KEY,
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
                    AppSystem.UNLOCKED_MASTER_KEY,
                ),
            )
            expectItem(
                setOf(
                    AppSystem.UNLOCKED_MASTER_KEY,
                ),
            )

            appStartupMonitor.onMasterKeyUnlocked(
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

            appStartupMonitor.onMasterKeyLocked()
            expectItem(
                setOf(
                    AppSystem.UNLOCKED_MASTER_KEY,
                ),
            )

            val databaseStateFlow2 = MutableStateFlow(DatabaseState.PREPARING)
            val systemUpdateState2 = MutableStateFlow(SystemUpdateState.READY)
            appStartupMonitor.onMasterKeyUnlocked(
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
        appStartupMonitor.onMasterKeyUnlocked(
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
        assertFalse(appStartupMonitor.isReady(AppSystem.REMOTE_SECRET))
        assertFalse(appStartupMonitor.isReady(AppSystem.UNLOCKED_MASTER_KEY))
        assertFalse(appStartupMonitor.isReady(AppSystem.SYSTEM_UPDATES))
        assertFalse(appStartupMonitor.isReady(AppSystem.DATABASE_UPDATES))

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
