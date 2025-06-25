/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.activities

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.tasks.TaskCreator
import ch.threema.common.TimeProvider
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.Rule

internal class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var serviceManagerMock: ServiceManager

    @MockK
    lateinit var preferenceServiceMock: PreferenceService

    @MockK
    lateinit var multiDeviceManagerMock: MultiDeviceManager

    @MockK
    lateinit var taskCreatorMock: TaskCreator

    private val testTimeProvider = TimeProvider { Instant.ofEpochMilli(1737121377325L) }

    private lateinit var viewModel: HomeViewModel

    @BeforeTest
    fun setup() {
        MockKAnnotations.init(this)

        every { serviceManagerMock.preferenceService } returns preferenceServiceMock
        every { serviceManagerMock.multiDeviceManager } returns multiDeviceManagerMock
        every { serviceManagerMock.taskCreator } returns taskCreatorMock

        every { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) } just Runs

        viewModel = HomeViewModel()
    }

    @AfterTest
    fun tearDown() {
        clearMocks(
            serviceManagerMock,
            preferenceServiceMock,
            multiDeviceManagerMock,
            taskCreatorMock,
        )
    }

    @Test
    fun `checkMultiDeviceGroup should save current timestamp if no timestamp is saved`() = runTest {
        // arrange
        every { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp } returns null
        every { multiDeviceManagerMock.isMultiDeviceActive } returns false

        // act
        viewModel.checkMultiDeviceGroup(
            serviceManager = serviceManagerMock,
            timeProvider = testTimeProvider,
        )

        // assert
        verify(exactly = 1) { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp }
        verify(exactly = 1) { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) }
        coVerify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        coVerify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        coVerify(exactly = 0) { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask() }
    }

    @Test
    fun `checkMultiDeviceGroup should end when minimum time difference interval is not met yet`() = runTest {
        // arrange
        val currentTime: Instant = testTimeProvider.get()
        val fiftyMinutesAgo: Instant = currentTime.minusSeconds(60 * 50)

        every { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp } returns fiftyMinutesAgo
        every { multiDeviceManagerMock.isMultiDeviceActive } returns false

        // act
        viewModel.checkMultiDeviceGroup(
            serviceManager = serviceManagerMock,
            timeProvider = testTimeProvider,
        )

        // assert
        verify(exactly = 1) { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp }
        verify(exactly = 0) { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) }
        verify(exactly = 0) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        coVerify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        coVerify(exactly = 0) { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask() }
    }

    @Test
    fun `checkMultiDeviceGroup should end when enough time passed but MD inactive`() = runTest {
        // arrange
        val currentTime: Instant = testTimeProvider.get()
        val sixtyMinutesAgo: Instant = currentTime.minusSeconds(60 * 60)

        every { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp } returns sixtyMinutesAgo
        every { multiDeviceManagerMock.isMultiDeviceActive } returns false

        // act
        viewModel.checkMultiDeviceGroup(
            serviceManager = serviceManagerMock,
            timeProvider = testTimeProvider,
        )

        // assert
        verify(exactly = 1) { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp }
        verify(exactly = 1) { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) }
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        coVerify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        coVerify(exactly = 0) { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask() }
    }

    @Test
    fun `checkMultiDeviceGroup should deactivate multi device when enough time passed`() = runTest {
        // arrange
        val currentTime: Instant = testTimeProvider.get()
        val sixtyMinutesAgo: Instant = currentTime.minusSeconds(60 * 60)

        every { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp } returns sixtyMinutesAgo
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        coEvery { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask().await() } just Runs

        // act
        viewModel.checkMultiDeviceGroup(
            serviceManager = serviceManagerMock,
            timeProvider = testTimeProvider,
        )

        // assert
        verify(exactly = 1) { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp }
        verify(exactly = 1) { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) }
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        coVerify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        coVerify(exactly = 1) { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask() }
    }
}
