package ch.threema.app.startup

import ch.threema.app.services.RemoteSecretMonitorService
import ch.threema.localcrypto.models.RemoteSecretProtectionState
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSecretProtectionStateMonitorTest {

    @Test
    fun `remote secret monitoring service is started if required initially`() = runTest {
        val (
            monitor,
            _,
            schedulerMock,
        ) = getRemoteSecretMonitor(RemoteSecretProtectionState.ACTIVE)

        val job = launch {
            monitor.monitorRemoteSecretProtectionState()
        }

        // Remote secret monitoring service is expected to be started if remote secret is active initially
        advanceUntilIdle()
        verify(exactly = 1) { schedulerMock.start() }
        verify(exactly = 0) { schedulerMock.stop() }

        job.cancel()
    }

    @Test
    fun `remote secret monitoring service is started and stopped when needed`() = runTest {
        val (
            monitor,
            remoteSecretProtectionStateFlow,
            schedulerMock,
        ) = getRemoteSecretMonitor(RemoteSecretProtectionState.INACTIVE)

        val job = launch {
            monitor.monitorRemoteSecretProtectionState()
        }

        var expectedStartCalls = 0
        var expectedStopCalls = 0

        // There must not be any interaction with the service when remote secret protection is inactive initially.
        advanceUntilIdle()
        verify(exactly = expectedStartCalls) { schedulerMock.start() }
        verify(exactly = expectedStopCalls) { schedulerMock.stop() }

        // When the protection state does not change, service should neither be started nor stopped
        remoteSecretProtectionStateFlow.value = RemoteSecretProtectionState.INACTIVE
        advanceUntilIdle()
        verify(exactly = expectedStartCalls) { schedulerMock.start() }
        verify(exactly = expectedStopCalls) { schedulerMock.stop() }

        // When the protection state is activated, the monitoring service is expected to be started
        expectedStartCalls++
        remoteSecretProtectionStateFlow.value = RemoteSecretProtectionState.ACTIVE
        advanceUntilIdle()
        verify(exactly = expectedStartCalls) { schedulerMock.start() }
        verify(exactly = expectedStopCalls) { schedulerMock.stop() }

        // When the protection state does not change, service should neither be started nor stopped
        remoteSecretProtectionStateFlow.value = RemoteSecretProtectionState.ACTIVE
        advanceUntilIdle()
        verify(exactly = expectedStartCalls) { schedulerMock.start() }
        verify(exactly = expectedStopCalls) { schedulerMock.stop() }

        // When the protection state is deactivated, the monitoring service is expected to be stopped
        expectedStopCalls++
        remoteSecretProtectionStateFlow.value = RemoteSecretProtectionState.INACTIVE
        advanceUntilIdle()
        verify(exactly = expectedStartCalls) { schedulerMock.start() }
        verify(exactly = expectedStopCalls) { schedulerMock.stop() }

        job.cancel()
    }

    private fun getRemoteSecretMonitor(initialState: RemoteSecretProtectionState): MonitorContext {
        val schedulerMock = mockk<RemoteSecretMonitorService.Scheduler>()
        every { schedulerMock.start() } just runs
        every { schedulerMock.stop() } just runs

        val remoteSecretProtectionStateFlow = MutableStateFlow(initialState)
        val monitor = RemoteSecretProtectionStateMonitorImpl(
            remoteSecretMonitorServiceScheduler = schedulerMock,
            masterKeyManager = mockk {
                every { remoteSecretProtectionState } returns remoteSecretProtectionStateFlow
            },
        )
        return MonitorContext(
            monitor = monitor,
            mutableProtectionState = remoteSecretProtectionStateFlow,
            scheduler = schedulerMock,
        )
    }

    private data class MonitorContext(
        val monitor: RemoteSecretProtectionStateMonitor,
        val mutableProtectionState: MutableStateFlow<RemoteSecretProtectionState>,
        val scheduler: RemoteSecretMonitorService.Scheduler,
    )
}
