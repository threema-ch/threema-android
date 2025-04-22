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

import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.csp.CspConnectionConfiguration
import ch.threema.domain.protocol.connection.csp.CspConnectionImpl
import ch.threema.domain.protocol.connection.csp.CspControllers
import ch.threema.domain.protocol.connection.csp.socket.CspSocket
import ch.threema.domain.protocol.connection.csp.socket.SocketFactory
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.layer.AuthLayer
import ch.threema.domain.protocol.connection.layer.CspFrameLayer
import ch.threema.domain.protocol.connection.layer.EndToEndLayer
import ch.threema.domain.protocol.connection.layer.Layer5Codec
import ch.threema.domain.protocol.connection.layer.MonitoringLayer
import ch.threema.domain.protocol.connection.layer.MultiplexLayer
import ch.threema.domain.protocol.connection.layer.ServerConnectionLayers
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.InternalTaskManager
import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TaskManagerConfiguration
import ch.threema.domain.taskmanager.TaskManagerProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal class CspConnectionTest : ServerConnectionTest() {
    private val connectionLockProvider = object : ConnectionLockProvider {
        override fun acquire(timeoutMillis: Long, tag: ConnectionLockProvider.ConnectionLogTag): ConnectionLock {
            return object : ConnectionLock {
                override fun release() {
                    // Nothing to do in tests
                }

                override fun isHeld() = false
            }
        }
    }

    override fun createChatServerConnection(): ServerConnection {
        val configuration = createConfiguration()

        val taskManager = object : TaskManager, InternalTaskManager {
            override fun processInboundMessage(message: InboundMessage, lock: ConnectionLock) {
                // Nothing to do
            }

            override suspend fun startRunningTasks(
                layer5Codec: Layer5Codec,
                incomingMessageProcessor: IncomingMessageProcessor,
            ) {
                // Nothing to do
            }

            override suspend fun pauseRunningTasks() {
                // Nothing to do
            }

            override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> = CompletableDeferred()

            override fun hasPendingTasks(): Boolean = false

            override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
                // Nothing to do
            }

            override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
                // Nothing to do
            }
        }

        val dependencyProvider = ServerConnectionDependencyProvider {
            val controllers = CspControllers(configuration)

            val socket = CspSocket(
                configuration.socketFactory,
                TestChatServerAddressProvider(),
                controllers.serverConnectionController.ioProcessingStoppedSignal,
                controllers.serverConnectionController.dispatcher.coroutineContext,
            )

            ServerConnectionDependencies(
                controllers.mainController,
                socket,
                createConnectionLayers(
                    it,
                    controllers,
                    configuration.incomingMessageProcessor,
                    taskManager,
                ),
                connectionLockProvider,
                taskManager,
            )
        }

        return CspConnectionImpl(dependencyProvider)
    }

    private fun createConfiguration(): CspConnectionConfiguration {
        val incomingMessageProcessor = object : IncomingMessageProcessor {
            override suspend fun processIncomingCspMessage(
                messageBox: MessageBox,
                handle: ActiveTaskCodec,
            ) {
            }

            override suspend fun processIncomingD2mMessage(
                message: InboundD2mMessage.Reflected,
                handle: ActiveTaskCodec,
            ) {
            }

            override fun processIncomingServerAlert(alertData: CspMessage.ServerAlertData) {}
            override fun processIncomingServerError(errorData: CspMessage.ServerErrorData) {}
        }
        val taskManager = TaskManagerProvider.getTaskManager(
            TaskManagerConfiguration(
                {
                    object : TaskArchiver {
                        override fun addTask(task: Task<*, TaskCodec>) {}
                        override fun removeTask(task: Task<*, TaskCodec>) {}
                        override fun loadAllTasks(): List<Task<*, TaskCodec>> = emptyList()
                    }
                },
                TestNoopDeviceCookieManager(),
                true,
            ),
        )
        return CspConnectionConfiguration(
            TestIdentityStore(),
            serverAddressProvider,
            Version(),
            assertDispatcherContext = true,
            TestNoopDeviceCookieManager(),
            incomingMessageProcessor,
            taskManager,
            { emptyArray() },
            ipv6 = false,
            createSocketFactory(),
        )
    }

    private fun createConnectionLayers(
        connection: ServerConnection,
        controllers: CspControllers,
        incomingMessageProcessor: IncomingMessageProcessor,
        taskManager: InternalTaskManager,
    ): ServerConnectionLayers {
        return ServerConnectionLayers(
            CspFrameLayer(),
            MultiplexLayer(controllers.serverConnectionController),
            AuthLayer(controllers.layer3Controller),
            MonitoringLayer(connection, controllers.layer4Controller),
            EndToEndLayer(
                controllers.serverConnectionController.dispatcher.coroutineContext,
                controllers.serverConnectionController,
                connection,
                incomingMessageProcessor,
                taskManager,
                connectionLockProvider,
            ),
        )
    }

    private fun createSocketFactory() = SocketFactory { testSocket }
}
