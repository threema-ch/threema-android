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
import ch.threema.app.BuildConfig
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.ServerAddressProviderService
import ch.threema.base.utils.AsyncResolver
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.BaseServerConnectionConfiguration
import ch.threema.domain.protocol.connection.BaseServerConnectionProvider
import ch.threema.domain.protocol.connection.ConnectionLock
import ch.threema.domain.protocol.connection.ConnectionLockProvider
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.connection.ServerConnectionException
import ch.threema.domain.protocol.connection.csp.CspConnectionConfiguration
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.csp.socket.ProxyAwareSocketFactory
import ch.threema.domain.protocol.connection.d2m.D2mConnectionConfiguration
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.TaskManager
import java.util.Locale
import java8.util.function.Supplier
import okhttp3.OkHttpClient

class WakeLockConnectionLockProvider(private val powerManager: PowerManager) : ConnectionLockProvider {
    private var nextLockNumber = 1

    override fun acquire(timeoutMillis: Long, tag: ConnectionLockProvider.ConnectionLogTag): ConnectionLock {
        val lockNumber = nextLockNumber++
        val logger = LoggingUtil.getThreemaLogger("ConnectionLock.$tag")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${BuildConfig.APPLICATION_ID}:ConnectionLock.$tag",
        )

        logger.info(
            "Acquiring lock #{} with {}s timeout",
            lockNumber,
            String.format(Locale.US, "%.2f", timeoutMillis.toFloat() / 1000f),
        )
        wakeLock.acquire(timeoutMillis)

        return object : ConnectionLock {
            override fun release() {
                if (!wakeLock.isHeld) {
                    return
                }
                logger.info("Releasing lock #{}", lockNumber)
                wakeLock.release()
            }

            override fun isHeld() = wakeLock.isHeld
        }
    }
}

private val logger = ConnectionLoggingUtil.getConnectionLogger("CspD2mDualConnectionSupplier")

/**
 * A connection [Supplier] that creates a new connection if the multi device activation state has
 * changed (CspConnection vs. D2mConnection). If the activation state has not changed since the last
 * call to [get], the same connection instance as in the previous call is returned.
 *
 * @param isTestBuild Set to `true` in test builds to run validations and checks during use to help find
 *                    problems. Failed checks will throw runtime exceptions.
 */
class CspD2mDualConnectionSupplier(
    private val powerManager: PowerManager,
    private val multiDeviceManager: MultiDeviceManager,
    private val incomingMessageProcessor: IncomingMessageProcessor,
    private val taskManager: TaskManager,
    private val deviceCookieManager: DeviceCookieManager,
    private val serverAddressProviderService: ServerAddressProviderService,
    private val identityStore: IdentityStore,
    private val version: Version,
    private val isIpv6Preferred: Boolean,
    private val okHttpClientSupplier: Supplier<OkHttpClient>,
    private val isTestBuild: Boolean,
) : Supplier<ServerConnection> {
    private val cspConnectionConfiguration by lazy { createCspConnectionConfiguration() }
    private val d2mConnectionConfiguration by lazy { createD2mConnectionConfiguration() }

    private lateinit var latestConfiguration: BaseServerConnectionConfiguration
    private lateinit var latestConnection: ServerConnection

    override fun get(): ServerConnection {
        return synchronized(this) {
            val configuration = if (multiDeviceManager.isMultiDeviceActive) {
                d2mConnectionConfiguration
            } else {
                cspConnectionConfiguration
            }
            if (!this::latestConfiguration.isInitialized || configuration != latestConfiguration) {
                logger.info("Create new connection")
                val connection = BaseServerConnectionProvider.createConnection(configuration, WakeLockConnectionLockProvider(powerManager))
                if (isTestBuild &&
                    this::latestConnection.isInitialized &&
                    connection::class == latestConnection::class
                ) {
                    throw ServerConnectionException("Unexpected new connection of same type")
                }
                connection.also {
                    latestConnection = it
                    latestConfiguration = configuration
                }
            } else {
                latestConnection
            }
        }
    }

    private fun createCspConnectionConfiguration(): CspConnectionConfiguration {
        logger.info("Create csp connection configuration")
        return CspConnectionConfiguration(
            identityStore,
            serverAddressProviderService.serverAddressProvider,
            version,
            isTestBuild,
            deviceCookieManager,
            incomingMessageProcessor,
            taskManager,
            AsyncResolver::getAllByName,
            isIpv6Preferred,
            ProxyAwareSocketFactory::makeSocket,
        )
    }

    private fun createD2mConnectionConfiguration(): D2mConnectionConfiguration {
        logger.info("Create d2m connection configuration")
        return D2mConnectionConfiguration(
            identityStore,
            serverAddressProviderService.serverAddressProvider,
            version,
            isTestBuild,
            deviceCookieManager,
            incomingMessageProcessor,
            taskManager,
            multiDeviceManager.propertiesProvider,
            multiDeviceManager.socketCloseListener,
            okHttpClientSupplier.get(),
        )
    }
}
