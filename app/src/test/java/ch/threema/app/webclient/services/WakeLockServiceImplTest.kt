package ch.threema.app.webclient.services

import android.content.Context
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import ch.threema.app.services.LifetimeService
import ch.threema.app.utils.ConfigUtils
import ch.threema.storage.models.WebClientSessionModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertTrue

class WakeLockServiceImplTest {

    @Test
    fun acquire() {
        mockkStatic(ConfigUtils::class)
        every { ConfigUtils.isNokiaDevice() } returns false

        val mockPowerManager = mockk<PowerManager>()
        val mockLifetimeService = mockk<LifetimeService>(relaxed = true)
        val mockContext = mockk<Context> {
            every { getSystemService(Context.POWER_SERVICE) } returns mockPowerManager
        }

        val service = WakeLockServiceImpl(mockContext, mockLifetimeService)

        val wakeLockMock = mockk<WakeLock>(relaxed = true)

        every { mockPowerManager.newWakeLock(any(), any()) } returns wakeLockMock
        every { wakeLockMock.isHeld } returns true

        val sessionModel1 = mockk<WebClientSessionModel> {
            every { id } returns 1
        }

        val sessionModel2 = mockk<WebClientSessionModel> {
            every { id } returns 2
        }

        every { wakeLockMock.isHeld } returns false

        assertTrue(service.acquire(sessionModel1))

        every { wakeLockMock.isHeld } returns true
        assertTrue(service.acquire(sessionModel1))
        assertTrue(service.acquire(sessionModel2))

        verify(exactly = 1) { mockPowerManager.newWakeLock(any(), any()) }
        verify(exactly = 1) { wakeLockMock.acquire() }

        unmockkStatic(ConfigUtils::class)
    }
}
