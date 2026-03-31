package ch.threema.domain.protocol.connection.d2m

import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.BaseServerConnection
import ch.threema.domain.protocol.connection.BaseServerConnectionConfiguration
import ch.threema.domain.protocol.connection.ServerConnectionDependencyProvider
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseListener
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.TaskManager
import okhttp3.OkHttpClient

/**
 * Only this interface is exposed to other modules
 */
interface D2mConnection

internal class D2mConnectionImpl(
    dependencyProvider: ServerConnectionDependencyProvider,
    private val closeListener: D2mSocketCloseListener,
    awaitAppReady: suspend () -> Unit,
) : D2mConnection, BaseServerConnection(dependencyProvider, awaitAppReady) {
    override fun onSocketClosed(reason: ServerSocketCloseReason) {
        closeListener.onSocketClosed(reason)
    }
}

fun interface MultiDevicePropertyProvider {
    fun get(): MultiDeviceProperties
}

data class D2mConnectionConfiguration(
    override val identityStore: IdentityStore,
    override val serverAddressProvider: ServerAddressProvider,
    override val version: Version,
    override val assertDispatcherContext: Boolean,
    override val deviceCookieManager: DeviceCookieManager,
    override val incomingMessageProcessor: IncomingMessageProcessor,
    override val taskManager: TaskManager,
    val multiDevicePropertyProvider: MultiDevicePropertyProvider,
    val closeListener: D2mSocketCloseListener,
    val okHttpClient: OkHttpClient,
) : BaseServerConnectionConfiguration
