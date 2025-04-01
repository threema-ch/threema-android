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

package ch.threema.domain.protocol.connection.csp.socket

import ch.threema.base.ThreemaException
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.connection.csp.CspConnectionConfiguration
import ch.threema.domain.stores.IdentityStoreInterface
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ChatServerAddressProviderImpl(
    configuration: CspConnectionConfiguration,
) : ChatServerAddressProvider {
    private val identityStore: IdentityStoreInterface = configuration.identityStore
    private val serverAddressProvider: ServerAddressProvider = configuration.serverAddressProvider
    private val hostResolver: HostResolver = configuration.hostResolver
    private val ipv6 = configuration.ipv6

    private val lock = ReentrantLock()

    private var socketAddressIndex: Int = 0
    private var serverSocketAddresses: List<InetSocketAddress> = listOf()

    /**
     * Move the internal pointer to the next available address.
     * If the last address is reached, the pointer will wrap around and start with the first address again.
     */
    override fun advance(): Unit = lock.withLock {
        socketAddressIndex++
        if (socketAddressIndex >= serverSocketAddresses.size) {
            socketAddressIndex = 0
        }
    }

    /**
     * Get the [InetSocketAddress] the internal pointer is currently pointing to.
     */
    override fun get(): InetSocketAddress? = lock.withLock {
        if (socketAddressIndex >= serverSocketAddresses.size) {
            null
        } else {
            serverSocketAddresses[socketAddressIndex]
        }
    }

    /**
     * Update the available [InetSocketAddress]es
     */
    @Throws(
        UnknownHostException::class,
        ExecutionException::class,
        InterruptedException::class,
        ThreemaException::class
    )
    override fun update(): Unit = lock.withLock {
        val serverHost = getServerHost()

        val addresses = if (ProxyAwareSocketFactory.shouldUseProxy(
                serverHost,
                serverAddressProvider.chatServerPorts[0]
            )
        ) {
            getAddressesWithProxy(serverHost)
        } else {
            getAddressesWithoutProxy(serverHost)
        }

        if (addresses.size != serverSocketAddresses.size || hasChangedAddresses(addresses)) {
            serverSocketAddresses = addresses
            socketAddressIndex = 0
        }
    }

    private fun hasChangedAddresses(addresses: List<InetSocketAddress>): Boolean =
        addresses.withIndex().any {
            val newAddress = it.value.address
            val previousAddress = serverSocketAddresses[it.index].address
            (newAddress == null && previousAddress != null)
                || (newAddress != null && previousAddress == null)
                || (newAddress != null && !newAddress.hostAddress.equals(previousAddress.hostAddress))
        }

    private fun getServerHost(): String {
        val serverNamePrefix = serverAddressProvider.getChatServerNamePrefix(ipv6)
        val serverHost = if (serverNamePrefix.isNotEmpty()) {
            val serverGroup =
                if (serverAddressProvider.chatServerUseServerGroups) identityStore.serverGroup else "."
            "$serverNamePrefix$serverGroup"
        } else {
            ""
        }
        return "$serverHost${serverAddressProvider.getChatServerNameSuffix(ipv6)}"
    }

    private fun getAddressesWithProxy(serverHost: String): List<InetSocketAddress> {
        return serverAddressProvider.chatServerPorts
            .map { InetSocketAddress.createUnresolved(serverHost, it) }
    }

    private fun getAddressesWithoutProxy(serverHost: String): List<InetSocketAddress> {
        val inetAddresses = hostResolver.getAllByName(serverHost)
        if (inetAddresses.isEmpty()) {
            throw UnknownHostException()
        }
        inetAddresses.sortWith { o1, o2 ->
            when {
                o1 is Inet6Address && o2 is Inet6Address -> o1.hostAddress.compareTo(o2.hostAddress)
                o1 is Inet6Address -> -1
                o2 is Inet4Address -> o1.hostAddress.compareTo(o2.hostAddress)
                else -> 1
            }
        }
        return inetAddresses.flatMap { address ->
            serverAddressProvider.chatServerPorts.map { port -> InetSocketAddress(address, port) }
        }
    }
}
