package ch.threema.domain.protocol.connection.csp

import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.BaseServerConnection
import ch.threema.domain.protocol.connection.BaseServerConnectionConfiguration
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnectionDependencyProvider
import ch.threema.domain.protocol.connection.csp.socket.CspSocket
import ch.threema.domain.protocol.connection.csp.socket.HostResolver
import ch.threema.domain.protocol.connection.csp.socket.SocketFactory
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.TaskManager

/**
 * Only this interface is exposed to other modules
 */
interface CspConnection

internal class CspConnectionImpl(
    dependencyProvider: ServerConnectionDependencyProvider,
    awaitAppReady: suspend () -> Unit,
) : CspConnection, BaseServerConnection(dependencyProvider, awaitAppReady) {
    override fun onConnected() {
        socket.let {
            if (it is CspSocket) {
                it.setSocketSoTimeout(ProtocolDefines.READ_TIMEOUT * 1000)
            }
        }
    }

    override fun onCspAuthenticated() {
        socket.let {
            if (it is CspSocket) {
                it.setSocketSoTimeout(0)
            }
        }
    }

    override fun onException(t: Throwable) {
        if (connectionState != ConnectionState.LOGGEDIN) {
            socket.let {
                if (it is CspSocket) {
                    it.advanceAddress()
                }
            }
        }
    }
}

data class CspConnectionConfiguration(
    override val identityStore: IdentityStore,
    override val serverAddressProvider: ServerAddressProvider,
    override val version: Version,
    override val assertDispatcherContext: Boolean,
    override val deviceCookieManager: DeviceCookieManager,
    override val incomingMessageProcessor: IncomingMessageProcessor,
    override val taskManager: TaskManager,
    val hostResolver: HostResolver,
    val ipv6: Boolean,
    val socketFactory: SocketFactory,
) : BaseServerConnectionConfiguration
