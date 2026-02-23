package ch.threema.app.startup

import androidx.lifecycle.LifecycleOwner
import ch.threema.app.AppConstants
import ch.threema.app.services.LifetimeService
import ch.threema.app.test.mockAppNotReady
import ch.threema.app.test.mockAppReady
import ch.threema.app.test.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.test.ClosingKoinTest
import org.koin.test.mock.declare

@OptIn(ExperimentalCoroutinesApi::class)
class AppProcessLifecycleObserverTest : ClosingKoinTest {

    @BeforeTest
    fun setUp() {
        startKoin { }
    }

    @Test
    fun `service manager is available immediately`() = runTest {
        // Arrange
        val (lifetimeServiceMock, connectionStateTracker) = createMocks()
        val lifecycleOwnerMock = mockk<LifecycleOwner>()
        declare { mockAppReady() }
        declare<LifetimeService> { lifetimeServiceMock }

        // Act
        val appProcessLifecycleObserver = AppProcessLifecycleObserver(
            reloadAppRestrictions = {},
            dispatcherProvider = testDispatcherProvider(),
        )
        appProcessLifecycleObserver.onCreate(lifecycleOwnerMock)
        appProcessLifecycleObserver.onStart(lifecycleOwnerMock)
        // Resume
        repeat(2) {
            appProcessLifecycleObserver.onResume(lifecycleOwnerMock)
            delay(100)
            appProcessLifecycleObserver.onPause(lifecycleOwnerMock)
            delay(100)
        }
        appProcessLifecycleObserver.onStop(lifecycleOwnerMock)
        appProcessLifecycleObserver.onDestroy(lifecycleOwnerMock)

        // Assert
        verify(exactly = 2) { lifetimeServiceMock.acquireConnection(AppConstants.ACTIVITY_CONNECTION_TAG) }
        verify(exactly = 2) {
            lifetimeServiceMock.releaseConnectionLinger(
                AppConstants.ACTIVITY_CONNECTION_TAG,
                AppConstants.ACTIVITY_CONNECTION_LIFETIME,
            )
        }
        assertFalse(connectionStateTracker.isAcquired)
    }

    @Test
    fun `service manager is not yet available`() = runTest {
        // Arrange
        val (lifetimeServiceMock, connectionStateTracker) = createMocks()
        val lifecycleOwnerMock = mockk<LifecycleOwner>()
        declare { mockAppNotReady() }

        // Act
        val appProcessLifecycleObserver = AppProcessLifecycleObserver(
            reloadAppRestrictions = {},
            dispatcherProvider = testDispatcherProvider(),
        )
        appProcessLifecycleObserver.onCreate(lifecycleOwnerMock)
        appProcessLifecycleObserver.onStart(lifecycleOwnerMock)
        repeat(2) {
            appProcessLifecycleObserver.onResume(lifecycleOwnerMock)
            delay(100)
            appProcessLifecycleObserver.onPause(lifecycleOwnerMock)
            delay(100)
        }
        appProcessLifecycleObserver.onStop(lifecycleOwnerMock)
        appProcessLifecycleObserver.onDestroy(lifecycleOwnerMock)

        // Assert
        verify(exactly = 0) { lifetimeServiceMock.acquireConnection(AppConstants.ACTIVITY_CONNECTION_TAG) }
        verify(exactly = 0) {
            lifetimeServiceMock.releaseConnectionLinger(
                AppConstants.ACTIVITY_CONNECTION_TAG,
                AppConstants.ACTIVITY_CONNECTION_LIFETIME,
            )
        }
        assertFalse(connectionStateTracker.isAcquired)
    }

    @Test
    fun `last call wins`() = runTest {
        // Arrange
        val (_, connectionStateTracker) = createMocks()
        val lifecycleOwnerMock = mockk<LifecycleOwner>()
        declare { mockAppNotReady() }

        // Act
        val appProcessLifecycleObserver = AppProcessLifecycleObserver(
            reloadAppRestrictions = {},
            dispatcherProvider = testDispatcherProvider(),
        )
        appProcessLifecycleObserver.onCreate(lifecycleOwnerMock)
        appProcessLifecycleObserver.onStart(lifecycleOwnerMock)
        // Repeat many times without delay
        repeat(10) {
            appProcessLifecycleObserver.onResume(lifecycleOwnerMock)
            appProcessLifecycleObserver.onPause(lifecycleOwnerMock)
        }
        appProcessLifecycleObserver.onStop(lifecycleOwnerMock)
        appProcessLifecycleObserver.onDestroy(lifecycleOwnerMock)
        advanceUntilIdle()

        // Assert
        assertFalse(connectionStateTracker.isAcquired)
    }

    private fun createMocks(): Pair<LifetimeService, ConnectionStateTracker> {
        val connectionStateTracker = MutableConnectionStateTracker(false)

        val lifetimeServiceMock = mockk<LifetimeService> {
            every { acquireConnection(AppConstants.ACTIVITY_CONNECTION_TAG) } answers {
                connectionStateTracker.isAcquired = true
            }
            every {
                releaseConnectionLinger(
                    AppConstants.ACTIVITY_CONNECTION_TAG,
                    AppConstants.ACTIVITY_CONNECTION_LIFETIME,
                )
            } answers {
                connectionStateTracker.isAcquired = false
            }
        }

        return lifetimeServiceMock to connectionStateTracker
    }

    private interface ConnectionStateTracker {
        val isAcquired: Boolean
    }

    private data class MutableConnectionStateTracker(override var isAcquired: Boolean) : ConnectionStateTracker
}
