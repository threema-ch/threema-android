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

package ch.threema.app.home

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.test.MainDispatcherRule
import ch.threema.common.minus
import ch.threema.testhelpers.TestTimeProvider
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var preferenceServiceMock: PreferenceService

    @MockK
    lateinit var multiDeviceManagerMock: MultiDeviceManager

    @MockK
    lateinit var taskCreatorMock: TaskCreator

    private val testTimeProvider = TestTimeProvider(1737121377325L)

    private lateinit var viewModel: HomeViewModel

    @BeforeTest
    fun setup() {
        MockKAnnotations.init(this)

        every { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) } just Runs

        viewModel = HomeViewModel(
            preferenceService = preferenceServiceMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskCreator = taskCreatorMock,
            timeProvider = testTimeProvider,
        )
    }

    @AfterTest
    fun tearDown() {
        clearMocks(
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
        viewModel.checkMultiDeviceGroup()

        // assert
        verify(exactly = 1) { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp }
        verify(exactly = 1) { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) }
        coVerify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(any()) }
        coVerify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(any()) }
        coVerify(exactly = 0) { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask() }
    }

    @Test
    fun `checkMultiDeviceGroup should end when minimum time difference interval is not met yet`() = runTest {
        // arrange
        val currentTime = testTimeProvider.get()
        val fiftyMinutesAgo = currentTime - 50.minutes

        every { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp } returns fiftyMinutesAgo
        every { multiDeviceManagerMock.isMultiDeviceActive } returns false

        // act
        viewModel.checkMultiDeviceGroup()

        // assert
        verify(exactly = 1) { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp }
        verify(exactly = 0) { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) }
        verify(exactly = 0) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(any()) }
        coVerify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(any()) }
        coVerify(exactly = 0) { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask() }
    }

    @Test
    fun `checkMultiDeviceGroup should end when enough time passed but MD inactive`() = runTest {
        // arrange
        val currentTime = testTimeProvider.get()
        val sixtyMinutesAgo = currentTime - 60.minutes

        every { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp } returns sixtyMinutesAgo
        every { multiDeviceManagerMock.isMultiDeviceActive } returns false

        // act
        viewModel.checkMultiDeviceGroup()

        // assert
        verify(exactly = 1) { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp }
        verify(exactly = 1) { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) }
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(any()) }
        coVerify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(any()) }
        coVerify(exactly = 0) { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask() }
    }

    @Test
    fun `checkMultiDeviceGroup should deactivate multi device when enough time passed`() = runTest {
        // arrange
        val currentTime = testTimeProvider.get()
        val sixtyMinutesAgo = currentTime - 60.minutes

        every { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp } returns sixtyMinutesAgo
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        coEvery { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask().await() } just Runs

        // act
        viewModel.checkMultiDeviceGroup()

        // assert
        verify(exactly = 1) { preferenceServiceMock.lastMultiDeviceGroupCheckTimestamp }
        verify(exactly = 1) { preferenceServiceMock.setLastMultiDeviceGroupCheckTimestamp(testTimeProvider.get()) }
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(any()) }
        coVerify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(any()) }
        coVerify(exactly = 1) { taskCreatorMock.scheduleDeactivateMultiDeviceIfAloneTask() }
    }
}
