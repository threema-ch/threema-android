package ch.threema.domain.protocol.connection

import ch.threema.common.stateFlowOf
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
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.csp.coders.MessageBox
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.InternalTaskManager
import ch.threema.domain.taskmanager.LeaderState
import ch.threema.domain.taskmanager.QueueSendCompleteState
import ch.threema.domain.taskmanager.ReflectionQueueDryState
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskArchiver
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TaskManagerConfiguration
import ch.threema.domain.taskmanager.TaskManagerProvider
import io.mockk.mockk
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.StateFlow

internal class CspConnectionTest : ServerConnectionTest() {
    private val connectionLockProvider = object : ConnectionLockProvider {
        override fun acquire(timeout: Duration, tag: ConnectionLockProvider.ConnectionLogTag): ConnectionLock {
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

            override suspend fun pauseRunningTasks(closeReason: ServerSocketCloseReason) {
                // Nothing to do
            }

            override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> = CompletableDeferred()

            override fun hasPendingTasks(): Boolean = false

            override fun watchQueueSendComplete(): StateFlow<QueueSendCompleteState> = stateFlowOf(QueueSendCompleteState.PENDING)

            override fun watchReflectionQueueDry(): StateFlow<ReflectionQueueDryState> = stateFlowOf(ReflectionQueueDryState.PENDING)

            override fun watchLeaderState(): StateFlow<LeaderState> = stateFlowOf(LeaderState.NOT_LEADER)
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

        return CspConnectionImpl(dependencyProvider, awaitAppReady = {})
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
                taskArchiver = {
                    object : TaskArchiver {
                        override fun addTask(task: Task<*, TaskCodec>) {}
                        override fun removeTask(task: Task<*, TaskCodec>) {}
                        override fun loadAllTasks(): List<Task<*, TaskCodec>> = emptyList()
                    }
                },
                deviceCookieManager = TestNoopDeviceCookieManager(),
                assertContext = true,
                getDebugString = { "" },
            ),
        )
        return CspConnectionConfiguration(
            identityStore = TestIdentityStore(),
            serverAddressProvider = serverAddressProvider,
            version = Version(),
            assertDispatcherContext = true,
            deviceCookieManager = TestNoopDeviceCookieManager(),
            incomingMessageProcessor = incomingMessageProcessor,
            taskManager = taskManager,
            hostResolver = mockk(),
            socketFactory = createSocketFactory(),
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
