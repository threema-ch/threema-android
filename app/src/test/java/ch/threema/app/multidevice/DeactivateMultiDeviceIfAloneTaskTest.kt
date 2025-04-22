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

package ch.threema.app.multidevice

import ch.threema.app.activities.MainDispatcherRule
import ch.threema.app.managers.ServiceManager
import ch.threema.app.tasks.DeactivateMultiDeviceIfAloneTask
import ch.threema.app.tasks.GetLinkedDevicesTask
import ch.threema.app.tasks.TaskCreator
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.DeviceSlotExpirationPolicy
import ch.threema.domain.taskmanager.ActiveTaskCodec
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.verify
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeactivateMultiDeviceIfAloneTaskTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    lateinit var serviceManagerMock: ServiceManager

    @MockK
    lateinit var mockHandle: ActiveTaskCodec

    @MockK
    lateinit var multiDeviceManagerMock: MultiDeviceManager

    @MockK
    lateinit var taskCreatorMock: TaskCreator

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { serviceManagerMock.multiDeviceManager } returns multiDeviceManagerMock
        every { serviceManagerMock.taskCreator } returns taskCreatorMock
    }

    @Test
    fun `DeactivateMultiDeviceIfAloneTask should end when MD inactive`() = runTest {
        // arrange
        every { multiDeviceManagerMock.isMultiDeviceActive } returns false

        // act
        DeactivateMultiDeviceIfAloneTask(serviceManagerMock).invoke(mockHandle)

        // assert
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 0) { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) }
        coVerify(exactly = 0) { multiDeviceManagerMock.deactivate(serviceManagerMock, mockHandle) }
    }

    @Test
    fun `DeactivateMultiDeviceIfAloneTask should end when fetching linked devices fails`() = runTest {
        // arrange
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        mockkConstructor(GetLinkedDevicesTask::class)
        coEvery { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) } throws (IllegalStateException("MD is not active anymore"))

        // act
        val exitedExceptionally = try {
            DeactivateMultiDeviceIfAloneTask(serviceManagerMock).invoke(mockHandle)
            false
        } catch (e: Exception) {
            true
        }

        // assert
        assertTrue { exitedExceptionally }
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 1) { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) }
        coVerify(exactly = 0) { multiDeviceManagerMock.deactivate(serviceManagerMock, mockHandle) }
    }

    @Test
    fun `DeactivateMultiDeviceIfAloneTask should not deactivate MD when multiple devices exist`() = runTest {
        // arrange
        val linkedDevices: List<LinkedDevice> = listOf(
            createLinkedDevice(0L),
            createLinkedDevice(1L),
            createLinkedDevice(2L),
        )

        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        mockkConstructor(GetLinkedDevicesTask::class)
        coEvery { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) } returns GetLinkedDevicesTask.LinkedDevicesResult.Success(linkedDevices)

        // act
        DeactivateMultiDeviceIfAloneTask(serviceManagerMock).invoke(mockHandle)

        // assert
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 1) { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) }
        coVerify(exactly = 0) { multiDeviceManagerMock.deactivate(serviceManagerMock, mockHandle) }
    }

    @Test
    fun `DeactivateMultiDeviceIfAloneTask should not deactivate MD when one device exists`() = runTest {
        // arrange
        val linkedDevices: List<LinkedDevice> = listOf(
            createLinkedDevice(0L),
        )

        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        mockkConstructor(GetLinkedDevicesTask::class)
        coEvery { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) } returns GetLinkedDevicesTask.LinkedDevicesResult.Success(linkedDevices)

        // act
        DeactivateMultiDeviceIfAloneTask(serviceManagerMock).invoke(mockHandle)

        // assert
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 1) { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) }
        coVerify(exactly = 0) { multiDeviceManagerMock.deactivate(serviceManagerMock, mockHandle) }
    }

    @Test
    fun `DeactivateMultiDeviceIfAloneTask should deactivate MD when no device exists`() = runTest {
        // arrange
        val linkedDevices: List<LinkedDevice> = emptyList()

        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        mockkConstructor(GetLinkedDevicesTask::class)
        coEvery { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) } returns GetLinkedDevicesTask.LinkedDevicesResult.Success(linkedDevices)
        coEvery { multiDeviceManagerMock.deactivate(serviceManagerMock, mockHandle) } just Runs

        // act
        DeactivateMultiDeviceIfAloneTask(serviceManagerMock).invoke(mockHandle)

        // assert
        verify(exactly = 1) { multiDeviceManagerMock.isMultiDeviceActive }
        coVerify(exactly = 1) { anyConstructed<GetLinkedDevicesTask>().invoke(mockHandle) }
        coVerify(exactly = 1) { multiDeviceManagerMock.deactivate(serviceManagerMock, mockHandle) }
    }

    private companion object {
        fun createLinkedDevice(deviceId: Long) = LinkedDevice(
            deviceId = DeviceId(deviceId.toULong()),
            platform = D2dMessage.DeviceInfo.Platform.UNSPECIFIED,
            platformDetails = "platformDetails",
            appVersion = "appVersion",
            label = "label",
            connectedSince = null,
            lastDisconnectAt = null,
            deviceSlotExpirationPolicy = DeviceSlotExpirationPolicy.PERSISTENT,
        )
    }
}
