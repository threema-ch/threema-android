/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

package ch.threema.app.connection

import android.os.PowerManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.ServerAddressProviderService
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.connection.csp.CspConnection
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.d2m.D2mConnection
import ch.threema.domain.protocol.connection.d2m.MultiDevicePropertyProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.stores.IdentityStoreInterface
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.TaskManager
import io.mockk.every
import io.mockk.mockk
import java8.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okhttp3.OkHttpClient

class CspD2mDualConnectionSupplierTest {
    @Test
    fun testMdInactive() {
        val connectionSupplier = createSupplier(MdActiveHandle())

        val connection = connectionSupplier.get()
        assertTrue(connection is CspConnection)
        // subsequent call must return the same instance
        assertSame(connection, connectionSupplier.get())
    }

    @Test
    fun testMdActive() {
        val connectionSupplier = createSupplier(MdActiveHandle(true))

        val connection = connectionSupplier.get()
        assertTrue(connection is D2mConnection)
        // subsequent call must return the same instance
        assertSame(connection, connectionSupplier.get())
    }

    @Test
    fun testMdInactiveToggleActive() {
        val handle = MdActiveHandle(false)
        val connectionSupplier = createSupplier(handle)

        assertTrue(connectionSupplier.get() is CspConnection)
        handle.isMdActive = true
        assertTrue(connectionSupplier.get() is D2mConnection)
        handle.isMdActive = false
        assertTrue(connectionSupplier.get() is CspConnection)
    }

    @Test
    fun testMdActiveToggleInactive() {
        val handle = MdActiveHandle(true)
        val connectionSupplier = createSupplier(handle)

        assertTrue(connectionSupplier.get() is D2mConnection)
        handle.isMdActive = false
        assertTrue(connectionSupplier.get() is CspConnection)
        handle.isMdActive = true
        assertTrue(connectionSupplier.get() is D2mConnection)
    }

    private fun createSupplier(mdActiveHandle: MdActiveHandle): Supplier<ServerConnection> {
        val powerManager = mockk<PowerManager>()
        val multiDeviceManager = mockk<MultiDeviceManager>()
        val propertiesProvider = mockk<MultiDevicePropertyProvider>()
        val socketCloseListener = mockk<D2mSocketCloseListener>()
        every { multiDeviceManager.isMultiDeviceActive } answers { mdActiveHandle.isMdActive }
        every { multiDeviceManager.propertiesProvider } returns propertiesProvider
        every { multiDeviceManager.socketCloseListener } returns socketCloseListener
        val incomingMessageProcessor = mockk<IncomingMessageProcessor>()
        val taskManager = mockk<TaskManager>()
        val deviceCookieManager = mockk<DeviceCookieManager>()
        val serverAddressProviderService = mockk<ServerAddressProviderService>()
        val serverAddressProvider = mockk<ServerAddressProvider>()
        every { serverAddressProviderService.serverAddressProvider } returns serverAddressProvider
        val identityStore = mockk<IdentityStoreInterface>()
        val version = Version()
        val isIpv6Preferred = true
        val okhttpClientSupplier = Supplier { mockk<OkHttpClient>() }
        val isTestBuild = false

        return CspD2mDualConnectionSupplier(
            powerManager,
            multiDeviceManager,
            incomingMessageProcessor,
            taskManager,
            deviceCookieManager,
            serverAddressProviderService,
            identityStore,
            version,
            isIpv6Preferred,
            okhttpClientSupplier,
            isTestBuild,
        )
    }

    class MdActiveHandle(var isMdActive: Boolean = false)
}
