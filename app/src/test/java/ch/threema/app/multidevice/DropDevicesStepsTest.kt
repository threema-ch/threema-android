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

import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.unlinking.DropDeviceResult
import ch.threema.app.multidevice.unlinking.DropDevicesIntent
import ch.threema.app.multidevice.unlinking.runDropDevicesSteps
import ch.threema.domain.helpers.DevicesInfoTaskCodec
import ch.threema.domain.helpers.UnusedTaskCodec
import ch.threema.domain.protocol.D2mProtocolDefines
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.D2mProtocolVersion
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.protocol.connection.data.DeviceSlotExpirationPolicy
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DropDevicesStepsTest {

    @MockK
    lateinit var serviceManagerMock: ServiceManager

    @MockK
    lateinit var multiDeviceManagerMock: MultiDeviceManager

    @MockK
    lateinit var multiDevicePropertyProviderMock: MultiDevicePropertyProvider

    @MockK
    lateinit var multiDeviceKeysMock: MultiDeviceKeys

    private val thisDeviceId = DeviceId(42u)

    private lateinit var multiDeviceProperties: MultiDeviceProperties

    @BeforeTest
    fun setup() {
        MockKAnnotations.init(this)

        multiDeviceProperties = MultiDeviceProperties(
            registrationTime = 0u,
            mediatorDeviceId = thisDeviceId,
            cspDeviceId = thisDeviceId,
            keys = multiDeviceKeysMock,
            deviceInfo = D2dMessage.DeviceInfo(
                platformDetails = "Test",
                platform = D2dMessage.DeviceInfo.Platform.UNSPECIFIED,
                appVersion = "TestVersion",
                label = "",
            ),
            protocolVersion = D2mProtocolVersion(
                D2mProtocolDefines.D2M_PROTOCOL_VERSION_MIN,
                D2mProtocolDefines.D2M_PROTOCOL_VERSION_MAX,
            ),
            serverInfoListener = { },
        )

        every { serviceManagerMock.multiDeviceManager } returns multiDeviceManagerMock
        every { multiDeviceManagerMock.propertiesProvider } returns multiDevicePropertyProviderMock
        every { multiDevicePropertyProviderMock.get() } returns multiDeviceProperties
        every { multiDeviceKeysMock.encryptTransactionScope(any()) } returns byteArrayOf()
    }

    @Test
    fun `FS should be enabled if MD already deactivated`() = runTest {
        // arrange
        every { multiDeviceManagerMock.isMultiDeviceActive } returns false
        every { multiDeviceManagerMock.enableForwardSecurity(any()) } just Runs
        every { multiDeviceManagerMock.reconnect() } just Runs

        val intents = listOf(
            DropDevicesIntent.Deactivate,
            DropDevicesIntent.DropDevices(setOf(DeviceId(0u)), thisDeviceId),
            DropDevicesIntent.DeactivateIfAlone,
        )

        // act
        val results = intents.map { intent ->
            runDropDevicesSteps(
                intent = intent,
                serviceManager = serviceManagerMock,
                handle = UnusedTaskCodec(),
            )
        }

        // assert
        verify(exactly = intents.size) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        verify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        verify(exactly = 0) { multiDeviceManagerMock.reconnect() }
        results.forEach { result -> assertTrue { result is DropDeviceResult.Failure.Internal } }
    }

    @Test
    fun `MD should be deactivated`() = runTest {
        // arrange
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        every { multiDeviceManagerMock.removeMultiDeviceLocally(any()) } just Runs
        every { multiDeviceManagerMock.enableForwardSecurity(any()) } just Runs
        every { multiDeviceManagerMock.reconnect() } just Runs

        val deviceIdsInDeviceGroup = listOf(
            thisDeviceId,
            DeviceId(0u),
            DeviceId(1u),
        )
        val devicesInfoMock = mockk<InboundD2mMessage.DevicesInfo>()
        every { devicesInfoMock.augmentedDeviceInfo } returns deviceIdsInDeviceGroup.associateWith { createAugmentedDeviceInfo() }

        val handle = DevicesInfoTaskCodec(mutableListOf(devicesInfoMock))

        // act
        runDropDevicesSteps(
            intent = DropDevicesIntent.Deactivate,
            serviceManager = serviceManagerMock,
            handle = handle,
        )

        // assert
        verify(exactly = 1) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        verify(exactly = 1) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        verify(exactly = 1) { multiDeviceManagerMock.reconnect() }
        assertTrue { handle.droppedDevices.size == deviceIdsInDeviceGroup.size }
        deviceIdsInDeviceGroup.forEach { deviceIdInDeviceGroup ->
            assertTrue { handle.droppedDevices.contains(deviceIdInDeviceGroup) }
        }
        assertTrue { handle.droppedDevices.last() == thisDeviceId }
    }

    @Test
    fun `One device should be dropped`() = runTest {
        // arrange
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        every { multiDeviceManagerMock.removeMultiDeviceLocally(any()) } just Runs
        every { multiDeviceManagerMock.enableForwardSecurity(any()) } just Runs
        every { multiDeviceManagerMock.reconnect() } just Runs

        val deviceIdToDrop = DeviceId(0u)

        val deviceIdsInDeviceGroup = listOf(
            thisDeviceId,
            deviceIdToDrop,
            DeviceId(1u),
        )
        val devicesInfoMock = mockk<InboundD2mMessage.DevicesInfo>()
        every { devicesInfoMock.augmentedDeviceInfo } returns deviceIdsInDeviceGroup.associateWith { createAugmentedDeviceInfo() }

        val handle = DevicesInfoTaskCodec(mutableListOf(devicesInfoMock))

        // act
        runDropDevicesSteps(
            intent = DropDevicesIntent.DropDevices(setOf(deviceIdToDrop), thisDeviceId),
            serviceManager = serviceManagerMock,
            handle = handle,
        )

        // assert
        verify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        verify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        verify(exactly = 0) { multiDeviceManagerMock.reconnect() }
        assertTrue { handle.droppedDevices.size == 1 }
        assertContains(handle.droppedDevices, deviceIdToDrop)
    }

    @Test
    fun `Last remaining devices should be dropped`() = runTest {
        // arrange
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        every { multiDeviceManagerMock.removeMultiDeviceLocally(any()) } just Runs
        every { multiDeviceManagerMock.enableForwardSecurity(any()) } just Runs
        every { multiDeviceManagerMock.reconnect() } just Runs

        val deviceIdsToDrop = setOf(DeviceId(0u), DeviceId(1u))

        val deviceIdsInDeviceGroup = deviceIdsToDrop + thisDeviceId
        val devicesInfoMock = mockk<InboundD2mMessage.DevicesInfo>()
        every { devicesInfoMock.augmentedDeviceInfo } returns deviceIdsInDeviceGroup.associateWith { createAugmentedDeviceInfo() }

        val handle = DevicesInfoTaskCodec(mutableListOf(devicesInfoMock))

        // act
        runDropDevicesSteps(
            intent = DropDevicesIntent.DropDevices(deviceIdsToDrop, thisDeviceId),
            serviceManager = serviceManagerMock,
            handle = handle,
        )

        // assert
        verify(exactly = 1) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        verify(exactly = 1) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        verify(exactly = 1) { multiDeviceManagerMock.reconnect() }
        assertTrue { handle.droppedDevices.size == deviceIdsInDeviceGroup.size }
        deviceIdsInDeviceGroup.forEach { deviceIdInDeviceGroup ->
            assertTrue { handle.droppedDevices.contains(deviceIdInDeviceGroup) }
        }
        assertTrue { handle.droppedDevices.last() == thisDeviceId }
    }

    @Test
    fun `MD should be deactivated if alone`() = runTest {
        // arrange
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        every { multiDeviceManagerMock.removeMultiDeviceLocally(any()) } just Runs
        every { multiDeviceManagerMock.enableForwardSecurity(any()) } just Runs
        every { multiDeviceManagerMock.reconnect() } just Runs

        val deviceIdsInDeviceGroup = setOf(thisDeviceId)
        val devicesInfoMock = mockk<InboundD2mMessage.DevicesInfo>()
        every { devicesInfoMock.augmentedDeviceInfo } returns deviceIdsInDeviceGroup.associateWith { createAugmentedDeviceInfo() }

        val handle = DevicesInfoTaskCodec(mutableListOf(devicesInfoMock))

        // act
        runDropDevicesSteps(
            intent = DropDevicesIntent.DeactivateIfAlone,
            serviceManager = serviceManagerMock,
            handle = handle,
        )

        // assert
        verify(exactly = 1) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        verify(exactly = 1) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        verify(exactly = 1) { multiDeviceManagerMock.reconnect() }
        assertTrue { handle.droppedDevices.size == deviceIdsInDeviceGroup.size }
        deviceIdsInDeviceGroup.forEach { deviceIdInDeviceGroup ->
            assertTrue { handle.droppedDevices.contains(deviceIdInDeviceGroup) }
        }
        assertTrue { handle.droppedDevices.last() == thisDeviceId }
    }

    @Test
    fun `MD should not be deactivated if not alone`() = runTest {
        // arrange
        every { multiDeviceManagerMock.isMultiDeviceActive } returns true
        every { multiDeviceManagerMock.removeMultiDeviceLocally(any()) } just Runs
        every { multiDeviceManagerMock.enableForwardSecurity(any()) } just Runs

        val deviceIdsInDeviceGroup = setOf(DeviceId(0u), thisDeviceId)
        val devicesInfoMock = mockk<InboundD2mMessage.DevicesInfo>()
        every { devicesInfoMock.augmentedDeviceInfo } returns deviceIdsInDeviceGroup.associateWith { createAugmentedDeviceInfo() }

        val handle = DevicesInfoTaskCodec(mutableListOf(devicesInfoMock))

        // act
        runDropDevicesSteps(
            intent = DropDevicesIntent.DeactivateIfAlone,
            serviceManager = serviceManagerMock,
            handle = handle,
        )

        // assert
        verify(exactly = 0) { multiDeviceManagerMock.removeMultiDeviceLocally(serviceManagerMock) }
        verify(exactly = 0) { multiDeviceManagerMock.enableForwardSecurity(serviceManagerMock) }
        verify(exactly = 0) { multiDeviceManagerMock.reconnect() }
        assertTrue { handle.droppedDevices.isEmpty() }
    }

    private fun createAugmentedDeviceInfo(): InboundD2mMessage.DevicesInfo.AugmentedDeviceInfo {
        return InboundD2mMessage.DevicesInfo.AugmentedDeviceInfo(
            encryptedDeviceInfo = byteArrayOf(),
            connectedSince = 0u,
            lastDisconnectAt = null,
            deviceSlotExpirationPolicy = DeviceSlotExpirationPolicy.PERSISTENT,
        )
    }
}
