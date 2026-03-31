package ch.threema.domain.protocol.connection

import ch.threema.domain.protocol.connection.csp.CspConnectionConfiguration
import ch.threema.domain.protocol.connection.csp.CspConnectionImpl
import ch.threema.domain.protocol.connection.csp.CspControllers
import ch.threema.domain.protocol.connection.csp.socket.ChatServerAddressProvider
import ch.threema.domain.protocol.connection.csp.socket.ChatServerAddressProviderImpl
import ch.threema.domain.protocol.connection.csp.socket.CspSocket
import ch.threema.domain.protocol.connection.d2m.D2mConnectionConfiguration
import ch.threema.domain.protocol.connection.d2m.D2mConnectionImpl
import ch.threema.domain.protocol.connection.d2m.D2mControllers
import ch.threema.domain.protocol.connection.d2m.socket.D2mServerAddressProvider
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocket
import ch.threema.domain.protocol.connection.layer.AuthLayer
import ch.threema.domain.protocol.connection.layer.CspFrameLayer
import ch.threema.domain.protocol.connection.layer.D2mFrameLayer
import ch.threema.domain.protocol.connection.layer.EndToEndLayer
import ch.threema.domain.protocol.connection.layer.MonitoringLayer
import ch.threema.domain.protocol.connection.layer.MultiplexLayer
import ch.threema.domain.protocol.connection.layer.ServerConnectionLayers
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.InternalTaskManager
import kotlin.coroutines.CoroutineContext

object BaseServerConnectionProvider {
    @JvmStatic
    fun createConnection(
        configuration: BaseServerConnectionConfiguration,
        connectionLockProvider: ConnectionLockProvider,
        awaitAppReady: suspend () -> Unit,
    ): ServerConnection {
        return when (configuration) {
            is CspConnectionConfiguration -> createCspConnection(configuration, connectionLockProvider, awaitAppReady)
            is D2mConnectionConfiguration -> createD2mConnection(configuration, connectionLockProvider, awaitAppReady)
            else -> throw ServerConnectionException("Unsupported connection configuration")
        }
    }

    private fun createD2mConnection(
        configuration: D2mConnectionConfiguration,
        connectionLockProvider: ConnectionLockProvider,
        awaitAppReady: suspend () -> Unit,
    ): ServerConnection {
        val dependencyProvider = ServerConnectionDependencyProvider {
            createD2mDependencies(it, configuration, connectionLockProvider)
        }

        return D2mConnectionImpl(
            dependencyProvider,
            configuration.closeListener,
            awaitAppReady,
        )
    }

    private fun createD2mDependencies(
        connection: ServerConnection,
        configuration: D2mConnectionConfiguration,
        connectionLockProvider: ConnectionLockProvider,
    ): ServerConnectionDependencies {
        val controllers = D2mControllers(configuration)

        val addressProvider = D2mServerAddressProvider(
            configuration.serverAddressProvider,
            configuration.multiDevicePropertyProvider.get().keys.dgid,
            configuration.identityStore.getServerGroup()!!,
        )

        val socket = D2mSocket(
            configuration.okHttpClient,
            addressProvider,
            controllers.mainController.ioProcessingStoppedSignal,
            controllers.serverConnectionController.dispatcher.coroutineContext,
        )

        val layers = createD2mLayers(
            connection,
            controllers,
            controllers.serverConnectionController.dispatcher.coroutineContext,
            configuration.incomingMessageProcessor,
            configuration.taskManager as InternalTaskManager,
            connectionLockProvider,
        )

        return ServerConnectionDependencies(controllers.mainController, socket, layers, connectionLockProvider, configuration.taskManager)
    }

    private fun createD2mLayers(
        connection: ServerConnection,
        controllers: D2mControllers,
        dispatcher: CoroutineContext,
        incomingMessageProcessor: IncomingMessageProcessor,
        taskManager: InternalTaskManager,
        connectionLockProvider: ConnectionLockProvider,
    ): ServerConnectionLayers {
        return ServerConnectionLayers(
            D2mFrameLayer(),
            MultiplexLayer(controllers.serverConnectionController),
            AuthLayer(controllers.layer3Controller),
            MonitoringLayer(connection, controllers.layer4Controller),
            EndToEndLayer(
                dispatcher,
                controllers.serverConnectionController,
                connection,
                incomingMessageProcessor,
                taskManager,
                connectionLockProvider,
            ),
        )
    }

    private fun createCspConnection(
        configuration: CspConnectionConfiguration,
        connectionLockProvider: ConnectionLockProvider,
        awaitAppReady: suspend () -> Unit,
    ): ServerConnection {
        val chatServerAddressProvider = ChatServerAddressProviderImpl(configuration)

        val dependencyProvider = ServerConnectionDependencyProvider {
            createCspDependencies(it, configuration, chatServerAddressProvider, connectionLockProvider)
        }

        return CspConnectionImpl(dependencyProvider, awaitAppReady)
    }

    private fun createCspDependencies(
        connection: ServerConnection,
        configuration: CspConnectionConfiguration,
        chatServerAddressProvider: ChatServerAddressProvider,
        connectionLockProvider: ConnectionLockProvider,
    ): ServerConnectionDependencies {
        val controllers = CspControllers(configuration)

        val socket = CspSocket(
            configuration.socketFactory,
            chatServerAddressProvider,
            controllers.mainController.ioProcessingStoppedSignal,
            controllers.serverConnectionController.dispatcher.coroutineContext,
        )

        val layers = createCspLayers(
            connection,
            controllers,
            controllers.serverConnectionController.dispatcher.coroutineContext,
            configuration.incomingMessageProcessor,
            configuration.taskManager as InternalTaskManager,
            connectionLockProvider,
        )

        return ServerConnectionDependencies(controllers.mainController, socket, layers, connectionLockProvider, configuration.taskManager)
    }

    private fun createCspLayers(
        connection: ServerConnection,
        controllers: CspControllers,
        dispatcher: CoroutineContext,
        incomingMessageProcessor: IncomingMessageProcessor,
        taskManager: InternalTaskManager,
        connectionLockProvider: ConnectionLockProvider,
    ): ServerConnectionLayers {
        return ServerConnectionLayers(
            CspFrameLayer(),
            MultiplexLayer(controllers.serverConnectionController),
            AuthLayer(controllers.layer3Controller),
            MonitoringLayer(connection, controllers.layer4Controller),
            EndToEndLayer(
                dispatcher,
                controllers.serverConnectionController,
                connection,
                incomingMessageProcessor,
                taskManager,
                connectionLockProvider,
            ),
        )
    }
}
