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
import java8.util.function.Supplier
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class CspD2mDualConnectionSupplierTest {

    @Test
    fun testMdInactive() {
        val connectionSupplier = createSupplier(MdActiveHandle())

        val connection = connectionSupplier.get()
        assertTrue(connection is CspConnection)
        // subsequent call must return the same instance
        assertTrue(connection === connectionSupplier.get())
    }

    @Test
    fun testMdActive() {
        val connectionSupplier = createSupplier(MdActiveHandle(true))

        val connection = connectionSupplier.get()
        assertTrue(connection is D2mConnection)
        // subsequent call must return the same instance
        assertTrue(connection === connectionSupplier.get())
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
        val multiDeviceManager = Mockito.mock(MultiDeviceManager::class.java)
        val propertiesProvider = Mockito.mock(MultiDevicePropertyProvider::class.java)
        val socketCloseListener = Mockito.mock(D2mSocketCloseListener::class.java)
        Mockito.`when`(multiDeviceManager.isMultiDeviceActive)
            .thenAnswer { mdActiveHandle.isMdActive }
        Mockito.`when`(multiDeviceManager.propertiesProvider).thenReturn(propertiesProvider)
        Mockito.`when`(multiDeviceManager.socketCloseListener).thenReturn(socketCloseListener)
        val incomingMessageProcessor = Mockito.mock(IncomingMessageProcessor::class.java)
        val taskManager = Mockito.mock(TaskManager::class.java)
        val deviceCookieManager = Mockito.mock(DeviceCookieManager::class.java)
        val serverAddressProviderService = Mockito.mock(ServerAddressProviderService::class.java)
        val serverAddressProvider = Mockito.mock(ServerAddressProvider::class.java)
        Mockito.`when`(serverAddressProviderService.serverAddressProvider)
            .thenReturn(serverAddressProvider)
        val identityStore = Mockito.mock(IdentityStoreInterface::class.java)
        val version = Version()
        val isIpv6Preferred = true
        val okhttpClientSupplier = Supplier { Mockito.mock(OkHttpClient::class.java) }
        val isTestBuild = false

        return CspD2mDualConnectionSupplier(
            multiDeviceManager,
            incomingMessageProcessor,
            taskManager,
            deviceCookieManager,
            serverAddressProviderService,
            identityStore,
            version,
            isIpv6Preferred,
            okhttpClientSupplier,
            isTestBuild
        )
    }

    class MdActiveHandle(var isMdActive: Boolean = false)
}
