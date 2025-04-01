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
    fun createConnection(configuration: BaseServerConnectionConfiguration): ServerConnection {
        return when (configuration) {
            is CspConnectionConfiguration -> createCspConnection(configuration)
            is D2mConnectionConfiguration -> createD2mConnection(configuration)
            else -> throw ServerConnectionException("Unsupported connection configuration")
        }
    }

    private fun createD2mConnection(configuration: D2mConnectionConfiguration): ServerConnection {
        val dependencyProvider = ServerConnectionDependencyProvider {
            createD2mDependencies(it, configuration)
        }

        return D2mConnectionImpl(
            dependencyProvider,
            configuration.closeListener
        )
    }

    private fun createD2mDependencies(
        connection: ServerConnection,
        configuration: D2mConnectionConfiguration
    ): ServerConnectionDependencies {
        val controllers = D2mControllers(configuration)

        val addressProvider = D2mServerAddressProvider(
            configuration.serverAddressProvider,
            configuration.multiDevicePropertyProvider.get().keys.dgid,
            configuration.identityStore.serverGroup
        )

        val socket = D2mSocket(
            configuration.okHttpClient,
            addressProvider,
            controllers.mainController.ioProcessingStoppedSignal,
            controllers.serverConnectionController.dispatcher.coroutineContext
        )

        val layers = createD2mLayers(
            connection,
            controllers,
            controllers.serverConnectionController.dispatcher.coroutineContext,
            configuration.incomingMessageProcessor,
            configuration.taskManager as InternalTaskManager
        )

        return ServerConnectionDependencies(controllers.mainController, socket, layers)

    }

    private fun createD2mLayers(
        connection: ServerConnection,
        controllers: D2mControllers,
        dispatcher: CoroutineContext,
        incomingMessageProcessor: IncomingMessageProcessor,
        taskManager: InternalTaskManager
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
                taskManager
            )
        )
    }

    private fun createCspConnection(
        configuration: CspConnectionConfiguration
    ): ServerConnection {
        val chatServerAddressProvider = ChatServerAddressProviderImpl(configuration)

        val dependencyProvider = ServerConnectionDependencyProvider {
            createCspDependencies(it, configuration, chatServerAddressProvider)
        }

        return CspConnectionImpl(dependencyProvider)
    }

    private fun createCspDependencies(
        connection: ServerConnection,
        configuration: CspConnectionConfiguration,
        chatServerAddressProvider: ChatServerAddressProvider
    ): ServerConnectionDependencies {
        val controllers = CspControllers(configuration)

        val socket = CspSocket(
            configuration.socketFactory,
            chatServerAddressProvider,
            controllers.mainController.ioProcessingStoppedSignal,
            controllers.serverConnectionController.dispatcher.coroutineContext
        )

        val layers = createCspLayers(
            connection,
            controllers,
            controllers.serverConnectionController.dispatcher.coroutineContext,
            configuration.incomingMessageProcessor,
            configuration.taskManager as InternalTaskManager,
        )

        return ServerConnectionDependencies(controllers.mainController, socket, layers)
    }

    private fun createCspLayers(
        connection: ServerConnection,
        controllers: CspControllers,
        dispatcher: CoroutineContext,
        incomingMessageProcessor: IncomingMessageProcessor,
        taskManager: InternalTaskManager,
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
                taskManager
            ),
        )
    }
}
