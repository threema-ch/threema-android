package ch.threema.domain.taskmanager

import ch.threema.base.SessionScoped
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.emptyByteArray
import ch.threema.domain.protocol.connection.ConnectionLock
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.layer.Layer5Codec
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.csp.ProtocolDefines
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("TaskManager")

/**
 * This is the public task manager interface. It can be used by the client to schedule new tasks.
 */
@SessionScoped
interface TaskManager {
    /**
     * Schedule a new task asynchronously. The task is executed when all prior tasks have finished
     * and when a connection to the server is available. If the task is not persisted by the
     * [TaskArchiver], then the task is discarded if the application is stopped before the task
     * could have been executed.
     */
    @Suppress("DeferredIsResult")
    fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R>

    /**
     * Return true when there are some pending tasks in the task manager left, false otherwise.
     */
    fun hasPendingTasks(): Boolean

    /**
     * Watch the state of the message queue of the current connection.
     *
     * The [QueueSendCompleteState.DRY] state is only reported while the connection is open, so reading it guarantees that the server has sent the
     * entire message queue of an open connection. In case the connection is lost, this state is reset to PENDING.
     *
     * Note: In multi-device mode, the queue send complete message is only sent to the leader device, so on non-leader devices the state
     * may remain PENDING while the connection is open.
     */
    fun watchQueueSendComplete(): StateFlow<QueueSendCompleteState>

    /**
     * Watch the state of the reflection queue of the current connection.
     *
     * The [ReflectionQueueDryState.DRY] state is only reported while the connection is open, so reading it guarantees that the server has sent the
     * reflection queue dry message of an open connection. In case the connection is lost, this state is reset to PENDING.
     *
     * Note: The reflection queue only exists in multi-device mode, so if multi-device is not active, the state remains PENDING.
     */
    fun watchReflectionQueueDry(): StateFlow<ReflectionQueueDryState>

    /**
     * Watch the leader state of this device.
     *
     * The [LeaderState.LEADER] state is only reported while the connection is open, so reading it guarantees that the server has promoted this device
     * to leader on the open connection. In case the connection is lost, this state is reset to NOT_LEADER.
     *
     * Note: The leader role only exists in multi-device mode, so if multi-device is not active, the state remains NOT_LEADER.
     */
    fun watchLeaderState(): StateFlow<LeaderState>
}

/**
 * This is the internal task manager interface that is used by the server connection.
 */
internal interface InternalTaskManager {
    /**
     * Process an inbound message. Depending on the message type, the message is processed
     * immediately or it is scheduled to run in the task manager context.
     *
     * IMPORTANT: The `lock` MUST be released when the message has been processed.
     */
    fun processInboundMessage(message: InboundMessage, lock: ConnectionLock)

    /**
     * Start the task runner with the given layer 5 codec.
     */
    suspend fun startRunningTasks(
        layer5Codec: Layer5Codec,
        incomingMessageProcessor: IncomingMessageProcessor,
    )

    /**
     * Pause running tasks as there is no network connection.
     */
    suspend fun pauseRunningTasks(closeReason: ServerSocketCloseReason)
}

/**
 * The task manager that runs the tasks.
 */
internal class TaskManagerImpl(
    taskArchiverCreator: () -> TaskArchiver,
    private val deviceCookieManager: DeviceCookieManager,
    private val dispatchers: TaskManagerDispatchers,
    private val getDebugString: Task<*, *>.() -> String,
) : InternalTaskManager, TaskManager {
    /**
     * The state of the message queue of the current connection.
     *
     * It is set to [QueueSendCompleteState.DRY] by the incoming csp message task when the 'queue send complete message' of the current connection
     * has been processed, and reset to [QueueSendCompleteState.PENDING] when a new connection is established or the connection ends.
     */
    private val queueSendCompleteState = MutableStateFlow(QueueSendCompleteState.PENDING)

    /**
     * The state of the reflection queue of the current connection.
     *
     * It is set to [ReflectionQueueDryState.DRY] when the server has sent the 'reflection queue dry message', and reset to
     * [ReflectionQueueDryState.PENDING] when a new connection is established or the connection ends.
     */
    private val reflectionQueueDryState = MutableStateFlow(ReflectionQueueDryState.PENDING)

    /**
     * The state of the leader role of this device.
     *
     * It is set to [LeaderState.LEADER] by the incoming d2m message task when the 'role promoted to leader message' of the current connection has
     * been processed, and reset to [LeaderState.NOT_LEADER] when a new connection is established or the connection ends.
     */
    private val leaderState = MutableStateFlow(LeaderState.NOT_LEADER)

    private var incomingMessageProcessor: IncomingMessageProcessor? = null

    /**
     * This is the smart task queue that contains the local tasks as well as the incoming message
     * tasks.
     */
    private val taskQueue by lazy {
        TaskQueue(
            taskArchiver = taskArchiverCreator(),
            dispatcherAsserters = dispatchers,
            getDebugString = getDebugString,
        )
    }

    /**
     * The task runner runs the tasks of the message queue while the task manager only schedules
     * them.
     */
    private val taskRunner: Lazy<TaskRunner> = lazy {
        TaskRunner(dispatchers, taskQueue)
    }

    override suspend fun startRunningTasks(
        layer5Codec: Layer5Codec,
        incomingMessageProcessor: IncomingMessageProcessor,
    ) {
        this.incomingMessageProcessor = incomingMessageProcessor

        taskRunner.value.startTaskRunner(
            layer5Codec,
            incomingMessageProcessor,
            queueSendCompleteState,
            reflectionQueueDryState,
            leaderState,
        )
    }

    override suspend fun pauseRunningTasks(closeReason: ServerSocketCloseReason) {
        if (taskRunner.isInitialized()) {
            taskRunner.value.stopTaskRunner(closeReason)
            // The connection is gone, so the states of its queues and its leader role are not valid anymore. Note that stopTaskRunner waits
            // for all in-flight tasks to finish, so this reset always happens after the last queue send complete message of this connection
            // has been processed.
            queueSendCompleteState.value = QueueSendCompleteState.PENDING
            reflectionQueueDryState.value = ReflectionQueueDryState.PENDING
            leaderState.value = LeaderState.NOT_LEADER
        }
    }

    override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
        logger.info("Scheduling new task: {}", task.getDebugString())

        val done = CompletableDeferred<R>()
        CoroutineScope(dispatchers.scheduleDispatcher.coroutineContext).launch {
            if (task is DropOnDisconnectTask && taskRunner.value.state != TaskRunner.State.RUNNING) {
                logger.info("DropOnDisconnectTask {} won't be scheduled because there is currently no connection", task)
                done.completeExceptionally(ConnectionUnavailableException())
                return@launch
            }

            // Enqueue the task
            taskQueue.enqueueTask(task, done)

            logger.info("Task scheduled: {}", task.getDebugString())
        }
        return done
    }

    override fun hasPendingTasks(): Boolean = taskQueue.hasPendingTasks()

    override fun watchQueueSendComplete(): StateFlow<QueueSendCompleteState> = queueSendCompleteState.asStateFlow()

    override fun watchReflectionQueueDry(): StateFlow<ReflectionQueueDryState> = reflectionQueueDryState.asStateFlow()

    override fun watchLeaderState(): StateFlow<LeaderState> = leaderState.asStateFlow()

    override fun processInboundMessage(message: InboundMessage, lock: ConnectionLock) {
        when (message) {
            is CspMessage -> processInboundCspMessage(message, lock)
            is InboundD2mMessage -> processInboundD2mMessage(message, lock)
        }
    }

    private fun processInboundD2mMessage(message: InboundD2mMessage, lock: ConnectionLock) {
        logger.debug(
            "Processing inbound d2m message with payload type `{}`",
            message.payloadType.toHexString(),
        )
        schedule(message, lock)
    }

    private fun processInboundCspMessage(message: CspMessage, lock: ConnectionLock) {
        logger.debug(
            "Processing inbound csp message with payload type `{}`",
            message.payloadType.toHexString(),
        )

        // IMPORTANT: Make sure to release the `lock` in all match arms (directly or indirectly)
        when (message.payloadType.toInt()) {
            ProtocolDefines.PLTYPE_ERROR -> {
                incomingMessageProcessor.get {
                    logger.error("Got inbound server error before task manager has been started")
                }.processIncomingServerError(
                    message.toServerErrorData(),
                )
                lock.release()
            }

            ProtocolDefines.PLTYPE_ALERT -> {
                incomingMessageProcessor.get {
                    logger.error("Got inbound server alert before task manager has been started")
                }.processIncomingServerAlert(
                    message.toServerAlertData(),
                )
                lock.release()
            }

            ProtocolDefines.PLTYPE_OUTGOING_MESSAGE_ACK -> schedule(message, lock)
            ProtocolDefines.PLTYPE_INCOMING_MESSAGE -> schedule(message, lock)
            ProtocolDefines.PLTYPE_QUEUE_SEND_COMPLETE -> schedule(message, lock)
            ProtocolDefines.PLTYPE_DEVICE_COOKIE_CHANGE_INDICATION -> {
                processDeviceCookieChangeIndication()
                lock.release()
            }
            else -> {
                logger.warn("Ignoring unknown payload with type ${message.payloadType}")
                lock.release()
            }
        }
    }

    private fun schedule(inboundMessage: InboundMessage, lock: ConnectionLock) {
        logger.info(
            "Scheduling inbound message with payload type {}",
            inboundMessage.payloadType.toHexString(),
        )

        CoroutineScope(dispatchers.scheduleDispatcher.coroutineContext).launch {
            // Enqueue the message
            taskQueue.enqueueInboundMessage(inboundMessage, lock)
        }
    }

    private fun processDeviceCookieChangeIndication() {
        deviceCookieManager.onChangeIndicationReceived()
        sendClearDeviceCookieChangeIndication()
    }

    private fun sendClearDeviceCookieChangeIndication() {
        logger.debug("Clearing device cookie change indication")
        taskRunner.value.sendImmediately(
            CspMessage(
                ProtocolDefines.PLTYPE_CLEAR_DEVICE_COOKIE_CHANGE_INDICATION.toUByte(),
                emptyByteArray(),
            ),
        )
    }

    /**
     * Get the incoming message processor. Otherwise throws an illegal state exception.
     */
    private fun IncomingMessageProcessor?.get(runIfNull: () -> Unit = { }): IncomingMessageProcessor {
        if (this == null) {
            runIfNull()
            throw IllegalStateException("Cannot access incoming message queue as it is null")
        }
        return this
    }

    internal interface TaskManagerDispatcherAsserters {
        /**
         * The executor dispatcher is used for running the tasks. This asserter can be used to check
         * if a method is running with the correct dispatcher.
         */
        val executorDispatcher: TaskManagerDispatcherAsserter

        /**
         * The schedule dispatcher is used for scheduling tasks and incoming messages in the task
         * task manager. It is also used for persisting and loading tasks. The main goal of this
         * dispatcher is to keep the tasks in the correct order. This asserter can be used to check
         * if a method is running with the correct dispatcher.
         */
        val scheduleDispatcher: TaskManagerDispatcherAsserter
    }

    internal data class TaskManagerDispatchers(
        /**
         * The executor dispatcher is used for running the tasks.
         */
        override val executorDispatcher: TaskManagerDispatcher,
        /**
         * The schedule dispatcher is used for scheduling tasks and incoming messages in the task
         * task manager. It is also used for persisting and loading tasks. The main goal of this
         * dispatcher is to keep the tasks in the correct order.
         */
        override val scheduleDispatcher: TaskManagerDispatcher,
    ) : TaskManagerDispatcherAsserters
}
