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

package ch.threema.domain.taskmanager

import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.OutboundD2mMessage
import ch.threema.domain.protocol.connection.data.OutboundMessage
import ch.threema.domain.protocol.connection.layer.Layer5Codec
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore

private val logger = getThreemaLogger("TaskRunner")

internal class TaskRunner(
    dispatchers: TaskManagerImpl.TaskManagerDispatchers,
    private val taskQueue: TaskQueue,
) {
    private val executorCoroutineContext = dispatchers.executorDispatcher.coroutineContext
    private val scheduleCoroutineContext = dispatchers.scheduleDispatcher.coroutineContext

    private var reconnectDelayMs = RECONNECT_MIN_DELAY_MS

    /**
     * The executor job that is currently active.
     */
    private var executorJob: Job? = null

    /**
     * The semaphore that the executor must acquire while starting.
     */
    private val executorSemaphore = Semaphore(1, 0)

    /**
     * The current layer 5 codec. This is used to send outbound messages.
     */
    private var layer5Codec: Layer5Codec? = null

    /**
     * The task handle that allows tasks to receive and send messages.
     */
    private val taskCodec = object : TaskCodec {
        private val reflectIdManager = ReflectIdManager()

        override suspend fun read(
            preProcess: (InboundMessage) -> MessageFilterInstruction,
        ): InboundMessage {
            return taskQueue.readMessage(preProcess, bypassTaskCodec)
        }

        override suspend fun write(message: OutboundMessage) {
            layer5Codec?.sendOutbound(message) ?: awaitCancellation()
        }

        override suspend fun reflectAndAwaitAck(
            encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult,
            storeD2dNonce: Boolean,
            nonceFactory: NonceFactory,
        ): ULong {
            val reflectId: UInt = reflect(encryptedEnvelopeResult)
            return awaitReflectAck(reflectId).also {
                if (storeD2dNonce) {
                    nonceFactory.store(NonceScope.D2D, encryptedEnvelopeResult.nonce)
                }
            }
        }

        override suspend fun reflect(encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult): UInt {
            val flags: UShort = 0u
            val reflectId: UInt = reflectIdManager.next()
            val reflect: OutboundD2mMessage.Reflect = OutboundD2mMessage.Reflect(
                flags,
                reflectId,
                encryptedEnvelopeResult.encryptedEnvelope,
            )
            logReflect(
                debugInfo = encryptedEnvelopeResult.debugInfo,
                reflectId = reflectId,
                flags = flags,
            )
            write(reflect)
            return reflectId
        }

        private fun logReflect(
            debugInfo: MultiDeviceKeys.EncryptedEnvelopeResult.DebugInfo,
            reflectId: UInt,
            @Suppress("SameParameterValue") flags: UShort,
        ) {
            logger.run {
                info("--> SENDING outbound D2D reflect message ${debugInfo.protoContentCaseName}")
                debug("--> SEND outbound D2D reflect message")
                debug("Id: {}", reflectId)
                debug("Flags: {}", flags)
                debug("Envelope: {}", debugInfo.rawEnvelopeContent)
                debug("--> END SEND")
            }
        }
    }

    /**
     * The bypass task codec is used for bypassed tasks. It ensures that tasks that are being run
     * as bypassed task cannot reflect or expect incoming messages.
     */
    private val bypassTaskCodec: BypassTaskCodec = BypassTaskCodec { taskCodec }

    enum class State {
        /**
         * The task runner is currently running.
         */
        RUNNING,

        /**
         * The task runner is still running, but the current task is being cancelled and no new tasks will be run.
         */
        STOPPING,

        /**
         * The task runner has been stopped and no task is currently running.
         */
        STOPPED,
    }

    /**
     * The current state of the task runner.
     */
    val state: State
        get() =
            if (layer5Codec == null) {
                if (executorJob?.isActive == true) {
                    State.STOPPING
                } else {
                    State.STOPPED
                }
            } else {
                State.RUNNING
            }

    /**
     * Start the task runner after the connection has been established.
     *
     * @param layer5Codec the current layer 5 codec for sending messages
     */
    internal suspend fun startTaskRunner(
        layer5Codec: Layer5Codec,
        incomingMessageProcessor: IncomingMessageProcessor,
    ) {
        logger.info("Starting task runner")

        // Stop old executor job to allow tasks to detect faster that they cannot send or receive
        // messages anymore
        if (executorJob?.isActive == true) {
            logger.info("Stopping previous executor job")
            stopTaskRunner(null)
        }

        // Acquire the executor semaphore during startup
        executorSemaphore.acquire()

        // Stop old executor job in case it has been started while waiting for the executor
        // semaphore
        if (executorJob?.isActive == true) {
            logger.info("Old executor job is still active. Stopping it.")
            stopTaskRunner(null)
        }

        this.layer5Codec = layer5Codec

        // Clear incoming message queue to get rid of old unprocessed messages
        runBlocking(executorCoroutineContext) {
            logger.info("Flushing incoming message queues")
            // As the connection just has been initiated, we will again receive un-acked messages
            taskQueue.recreateIncomingMessageQueue(incomingMessageProcessor)
        }

        // We handle the exceptions in invokeOnCompletion instead of the exception handler
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }

        // Start actually processing the tasks while the executor is active. Do not use the schedule
        // dispatcher. Otherwise multiple tasks can be launched simultaneously.
        executorJob = CoroutineScope(executorCoroutineContext).launch(exceptionHandler) {
            logger.debug("Executing tasks in coroutine scope {}", this)
            while (isActive) {
                runNextTask()

                // Set the delay to the minimum reconnect delay. Note that the task that just has
                // been successfully run may not be the same task that caused the trouble.
                reconnectDelayMs = RECONNECT_MIN_DELAY_MS
            }
        }.also {
            it.invokeOnCompletion { cause ->
                when (cause) {
                    // The task manager has detected that the server connection has been stopped or
                    // a new server connection has been established and the current executor job is
                    // running with a stale layer 5. In both cases, a new connection is being
                    // initiated which will trigger a start of the task runner again.
                    is ConnectionStoppedException -> logger.info("Task executor stopped", cause)
                    // A protocol exception has been detected by a task. This means, that the
                    // connection must be restarted.
                    is ProtocolException -> {
                        logger.warn(
                            "Task executor stopped. Restarting connection in {}ms.",
                            reconnectDelayMs,
                            cause,
                        )
                        restartConnection()
                    }
                    // If the connection is lost after the last task has just completed successfully
                    // or if a task has caught the network exception and decided to complete
                    // successfully, then the executor job also completes successfully. Therefore,
                    // the cause is null and the task runner will be started when a new connection
                    // has been established.
                    null -> logger.info("Task executor finished")
                    // Any other exception means that the task manager is behaving faulty. In this
                    // case we need to start the task manager again. This should never happen.
                    else -> {
                        logger.error("Task manager failed", cause)
                        CoroutineScope(scheduleCoroutineContext).launch {
                            // To avoid a busy loop, we delay the restarting of the task runner a bit here
                            delay(10.seconds)
                            startTaskRunner(layer5Codec, incomingMessageProcessor)
                        }
                    }
                }
            }
        }

        logger.info("Task runner started")
        executorSemaphore.release()
    }

    /**
     * Send an outbound message immediately. This method must only be used by the task manager to
     * send messages that are required to be sent outside of a task. This functionality must not be
     * provided to the rest of the application.
     */
    internal fun sendImmediately(outboundMessage: OutboundMessage) {
        layer5Codec?.sendOutbound(outboundMessage)
    }

    /**
     * Stop the task runner. Call this method only when the connection has been lost or a new
     * connection has been established while the task runner is still running (should not happen).
     */
    internal suspend fun stopTaskRunner(closeReason: ServerSocketCloseReason?) {
        layer5Codec = null

        if (executorJob == null) {
            logger.warn("Tried to stop task runner before starting it.")
            return
        }

        logger.info("Stopping task runner with reason {}", closeReason)

        executorJob?.cancelAndJoin(ConnectionStoppedException("The server connection has been ended", closeReason))
        logger.info("Executor job canceled and joined")

        taskQueue.removeDropOnDisconnectTasks(closeReason)
    }

    private suspend fun runNextTask() {
        val taskQueueElement = taskQueue.getNextTask()

        var executionAttempts = 0
        while (!taskQueueElement.isCompleted()) {
            // As we are starting an attempt, we increase the amount of the execution attempts
            executionAttempts++
            // Invoke the task and catch any unexpected exception
            try {
                taskQueueElement.run(taskCodec)
            } catch (exception: Exception) {
                when (exception) {
                    is NetworkException -> {
                        if (taskQueueElement.shouldDropOnDisconnect) {
                            taskQueueElement.completeExceptionally(exception)
                        }
                        throw exception
                    }
                    else -> {
                        logger.error(
                            "Error occurred on attempt $executionAttempts when running task",
                            exception,
                        )
                        // If we had an unexpected exception, we check if we have a retry left. If
                        // we don't, we complete the completable deferred exceptionally which ends
                        // the loop and notifies the task scheduler that it has failed.
                        if (executionAttempts >= taskQueueElement.maximumNumberOfExecutions) {
                            logger.error("Maximum number of retries reached. Task is completed exceptionally.")
                            taskQueueElement.completeExceptionally(exception)
                        }
                    }
                }
            }
        }
    }

    private fun restartConnection() {
        layer5Codec?.let {
            // Run reconnect runnable
            it.restartConnection(reconnectDelayMs)

            // Increase reconnect delay
            reconnectDelayMs = min(reconnectDelayMs * 2, RECONNECT_MAX_DELAY_MS)
        }
    }

    companion object {
        // The initial delay for reconnections are 2 seconds
        private const val RECONNECT_MIN_DELAY_MS = 2_000L

        // The maximum delay until a reconnection is triggered is 512 seconds (~8.5 minutes)
        private const val RECONNECT_MAX_DELAY_MS = 512_000L
    }
}

/**
 * This is a special exception, which means that there are problems with the server connection.
 * Tasks should only catch this exception if they can continue without server connection. If all
 * other exceptions ought to be caught, [catchAllExceptNetworkException] can be used.
 */
sealed class NetworkException(message: String) : CancellationException(message)

/**
 * This exception is only thrown by the task manager, when a new connection is being established or
 * the current connection has been stopped or lost. Tasks may catch this exception, if they can
 * continue without server connection.
 *
 * In case the socket has been properly closed, a [closeReason] is available.
 */
class ConnectionStoppedException internal constructor(message: String, val closeReason: ServerSocketCloseReason?) : NetworkException(message) {
    /**
     * Note that this constructor should only be used in tests.
     */
    constructor() : this("Test", null)
}

/**
 * This exception indicates that the task has not been scheduled because there is no connection available. It is only thrown for
 * [DropOnDisconnectTask]s as other task will be scheduled anyways.
 */
class ConnectionUnavailableException internal constructor() : NetworkException("No connection available")

/**
 * This exception must be thrown by tasks to enforce a reconnection to the server. The tasks are
 * retried when the connection is back. When this exception is thrown, it may indicate that there is
 * no connection (e.g. directory server unreachable), or an unexpected message has been received.
 */
class ProtocolException(message: String) : NetworkException(message)

/**
 * Runs this code and catches all exceptions except [NetworkException]. This method is intended to
 * be used within tasks where [NetworkException]s must not be caught.
 */
suspend inline fun <R, reified T> (suspend () -> R).catchExceptNetworkException(then: (e: T) -> R): R {
    return try {
        this()
    } catch (e: Exception) {
        when (e) {
            is NetworkException -> throw e
            is T -> then(e)
            else -> throw e
        }
    }
}

/**
 * Runs this code and catches all exceptions except [NetworkException].
 * This method is intended to be used within tasks where [NetworkException]s must not be caught.
 */
@Throws(NetworkException::class)
inline fun <R> runCatchingExceptNetworkException(block: () -> R): Result<R> =
    runCatching(block)
        .onFailure {
            if (it is NetworkException) throw it
        }

/**
 * Runs this code and catches all exceptions except [NetworkException]. This method is intended to
 * be used within tasks where [NetworkException]s must not be caught.
 */
inline fun <R> (() -> R).catchAllExceptNetworkException(then: (e: Exception) -> R): R {
    return try {
        this()
    } catch (e: Exception) {
        when (e) {
            is NetworkException -> throw e
            else -> then(e)
        }
    }
}

/**
 * Runs this code and catches all exceptions except [NetworkException]. This method is intended to
 * be used within tasks where [NetworkException]s must not be caught.
 */
suspend inline fun <R> (suspend () -> R).catchAllExceptNetworkException(then: (e: Exception) -> R): R {
    return try {
        this()
    } catch (e: Exception) {
        when (e) {
            is NetworkException -> throw e
            else -> then(e)
        }
    }
}

/**
 * The message filter instruction is used by tasks to specify if they need a certain message (e.g.
 * an outgoing message ack) or if it is not related to the task.
 */
enum class MessageFilterInstruction {
    /**
     * Bypass or backlog message depending on type:
     *
     * d2d messages: process them directly (bypass)
     * csp messages: keep them in order, but do not yet process them (backlog)
     */
    BYPASS_OR_BACKLOG,

    /**
     * This is the message that is expected by the task. This message should be removed from the
     * queue. The message is handled directly in the calling task.
     */
    ACCEPT,

    /**
     * Reject the message. TODO(ANDR-2868): handle this correctly
     *
     * This instruction is used when a message is received which is not expected at this moment and
     * violates the protocol. The message must be dropped and an exception should be raised in the
     * task manager and trigger a reconnect.
     */
    REJECT,
}

private suspend fun Job.cancelAndJoin(networkException: NetworkException) {
    cancel(networkException)
    return join()
}
