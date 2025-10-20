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

package ch.threema.app.apptaskexecutor

import ch.threema.app.apptaskexecutor.tasks.AppTask
import ch.threema.app.apptaskexecutor.tasks.PersistableAppTask
import ch.threema.app.managers.ServiceManager
import ch.threema.app.test.testDispatcherProvider
import ch.threema.common.stateFlowOf
import ch.threema.testhelpers.assertSuspendsForever
import ch.threema.testhelpers.willThrow
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AppTaskExecutorTest {

    @Test
    fun `app task is being run when scheduled early`() = runTest {
        val (appTask, deferred) = getAwaitableAppTask()

        val appTaskExecutor = AppTaskExecutor(
            appStartupMonitor = mockk(),
            dispatcherProvider = testDispatcherProvider(),
            serviceManagerProvider = mockk {
                every { serviceManagerFlow } returns stateFlowOf(null)
            },
            appTaskPersistenceProvider = mockk(),
        )

        appTaskExecutor.scheduleTask(appTask)

        backgroundScope.launch {
            appTaskExecutor.start()
        }

        // Assert that the deferred is completed
        deferred.await()
    }

    @Test
    fun `app task is being run when scheduled late`() = runTest {
        val (appTask, deferred) = getAwaitableAppTask()

        val appTaskExecutor = AppTaskExecutor(
            appStartupMonitor = mockk(),
            dispatcherProvider = testDispatcherProvider(),
            serviceManagerProvider = mockk {
                every { serviceManagerFlow } returns stateFlowOf(null)
            },
            appTaskPersistenceProvider = mockk(),
        )

        backgroundScope.launch {
            appTaskExecutor.start()
        }

        appTaskExecutor.scheduleTask(appTask)

        // Assert that the deferred is completed
        deferred.await()
    }

    @Test
    fun `several app tasks are being run`() = runTest {
        val (appTasks, deferreds) = List(1000) { getAwaitableAppTask { delay(1000.milliseconds) } }.unzip()

        val appTaskExecutor = AppTaskExecutor(
            appStartupMonitor = mockk(),
            dispatcherProvider = testDispatcherProvider(),
            serviceManagerProvider = mockk {
                every { serviceManagerFlow } returns stateFlowOf(null)
            },
            appTaskPersistenceProvider = mockk(),
        )

        backgroundScope.launch {
            appTaskExecutor.start()
        }

        appTasks.map(appTaskExecutor::scheduleTask).forEach { it.await() }

        // Assert that all deferreds are awaited
        deferreds.awaitAll()
    }

    @Test
    fun `tasks can be run in parallel`() = runTest {
        val (appTaskA, deferredA) = getAwaitableAppTask {
            delay(2000)
        }
        // App task B cannot finish before A
        val (appTaskB, deferredB) = getAwaitableAppTask {
            deferredA.await()
        }

        val appTaskExecutor = AppTaskExecutor(
            appStartupMonitor = mockk(),
            dispatcherProvider = testDispatcherProvider(),
            serviceManagerProvider = mockk {
                every { serviceManagerFlow } returns stateFlowOf(null)
            },
            appTaskPersistenceProvider = mockk(),
        )

        backgroundScope.launch {
            appTaskExecutor.start()
        }

        // First schedule app task B and then A
        appTaskExecutor.scheduleTask(appTaskB)
        appTaskExecutor.scheduleTask(appTaskA)

        // Assert that app task B finishes which implies that app task A has finished before B despite being scheduled later
        deferredB.await()
    }

    @Test
    fun `persisted tasks are run when service manager becomes available`() = runTest {
        val persistedTask = mockk<PersistableAppTask>()
        val persistedTaskCompleted = CompletableDeferred<Unit>()
        coEvery { persistedTask.run() } answers {
            persistedTaskCompleted.complete(Unit)
        }
        val serviceManagerFlow = MutableStateFlow<ServiceManager?>(null)
        val mockedPersistence = mockk<AppTaskPersistence>()
        coEvery { mockedPersistence.loadAllPersistedTasks() } returns setOf(persistedTask)
        coEvery { mockedPersistence.removePersistedTask(persistedTask) } just Runs

        val appTaskExecutor = AppTaskExecutor(
            appStartupMonitor = mockk(),
            dispatcherProvider = testDispatcherProvider(),
            serviceManagerProvider = mockk {
                every { this@mockk.serviceManagerFlow } returns serviceManagerFlow
            },
            appTaskPersistenceProvider = { mockedPersistence },
        )

        backgroundScope.launch {
            appTaskExecutor.start()
        }

        advanceUntilIdle()

        // Assert that persisted task has not yet been executed (as service manager isn't available yet)
        coVerify(exactly = 0) { mockedPersistence.loadAllPersistedTasks() }

        // Provide service manager and assert that task have been loaded and run
        serviceManagerFlow.emit(mockk<ServiceManager>())
        persistedTaskCompleted.await()
        coVerify(exactly = 1) { mockedPersistence.loadAllPersistedTasks() }

        // Emit another service manager and ensure that tasks are not loaded again
        serviceManagerFlow.emit(null)
        serviceManagerFlow.emit(mockk<ServiceManager>())
        coVerify(exactly = 1) { mockedPersistence.loadAllPersistedTasks() }
    }

    @Test
    fun `persistable tasks are not scheduled when no service manager is available`() = runTest {
        val appTaskExecutor = AppTaskExecutor(
            appStartupMonitor = mockk(),
            dispatcherProvider = testDispatcherProvider(),
            serviceManagerProvider = mockk {
                every { serviceManagerFlow } returns stateFlowOf(null)
            },
            appTaskPersistenceProvider = mockk(),
        )

        backgroundScope.launch {
            appTaskExecutor.start()
        }

        assertSuspendsForever {
            appTaskExecutor.persistAndScheduleTask(mockk())
        }
    }

    @Test
    fun `failing task should not stop task executor`() = runTest {
        val appTaskExecutor = AppTaskExecutor(
            appStartupMonitor = mockk(),
            dispatcherProvider = testDispatcherProvider(),
            serviceManagerProvider = mockk {
                every { serviceManagerFlow } returns stateFlowOf(null)
            },
            appTaskPersistenceProvider = mockk(),
        )

        backgroundScope.launch {
            appTaskExecutor.start()
        }

        // Assert that an exception throwing task will result in an exceptionally completed deferred
        val failingAppTask = getAwaitableAppTask { throw IllegalStateException("This task throws an exception") }.first
        val scheduleAndAwaitAppTask = suspend { appTaskExecutor.scheduleTask(failingAppTask).await() }
        scheduleAndAwaitAppTask willThrow IllegalStateException::class

        // Assert that the task executor is still able to run other tasks
        val succeedingAppTask = getAwaitableAppTask().first
        appTaskExecutor.scheduleTask(succeedingAppTask).await()
    }

    /**
     * Note that these awaitable app tasks complete a completable deferred before they finish. This allows us checking that the tasks were really run.
     */
    private fun getAwaitableAppTask(body: suspend () -> Unit = { }): Pair<AppTask, Deferred<Unit>> {
        val completableDeferred = CompletableDeferred<Unit>()
        val appTask = object : AppTask {
            override suspend fun run() {
                body()
                completableDeferred.complete(Unit)
            }
        }
        return appTask to completableDeferred
    }
}
