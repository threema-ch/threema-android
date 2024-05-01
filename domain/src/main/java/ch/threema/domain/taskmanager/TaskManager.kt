/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.PayloadProcessingException
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.data.CspMessage
import ch.threema.domain.protocol.connection.data.InboundD2mMessage
import ch.threema.domain.protocol.connection.data.InboundMessage
import ch.threema.domain.protocol.connection.data.toHex
import ch.threema.domain.protocol.connection.layer.Layer5Codec
import ch.threema.domain.protocol.csp.ProtocolDefines
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

private val logger = LoggingUtil.getThreemaLogger("TaskManager")

/**
 * This is the public task manager interface. It can be used by the client to schedule new tasks.
 */
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

    // This is only used for the migration phase until we fully use the task manager
    @Deprecated(
        "We should only be able to send and receive messages from within tasks.",
        ReplaceWith("TaskManager#schedule")
    )
    fun getMigrationTaskHandle(): ActiveTaskCodec

    /**
     * Add a queue send complete listener.
     */
    fun addQueueSendCompleteListener(listener: QueueSendCompleteListener)

    /**
     * Remove a queue send complete listener.
     */
    fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener)
}

/**
 * This is the internal task manager interface that is used by the server connection.
 */
internal interface InternalTaskManager {
    /**
     * Process an inbound message. Depending on the message type, the message is processed
     * immediately or it is scheduled to run in the task manager context.
     */
    fun processInboundMessage(message: InboundMessage)

    /**
     * Start the task runner with the given layer 5 codec.
     */
    suspend fun startRunningTasks(layer5Codec: Layer5Codec)

    /**
     * Pause running tasks as there is no network connection.
     */
    suspend fun pauseRunningTasks()
}

/**
 * The task manager that runs the tasks.
 */
internal class TaskManagerImpl(
    private val incomingMessageProcessor: IncomingMessageProcessor,
    taskArchiverCreator: () -> TaskArchiver,
    private val deviceCookieManager: DeviceCookieManager,
    private val dispatchers: TaskManagerDispatchers,
) : InternalTaskManager, TaskManager {
    private val queueSendCompleteListeners: MutableSet<QueueSendCompleteListener> = mutableSetOf()

    /**
     * This is the smart task queue that contains the local tasks as well as the incoming message
     * tasks.
     */
    private val taskQueue by lazy {
        TaskQueue(incomingMessageProcessor, taskArchiverCreator(), dispatchers)
    }

    /**
     * The task runner runs the tasks of the message queue while the task manager only schedules
     * them.
     */
    private val taskRunner: Lazy<TaskRunner> = lazy {
        TaskRunner(dispatchers, taskQueue)
    }

    @Deprecated(
        "We should only be able to send and receive messages from within tasks.",
        replaceWith = ReplaceWith("TaskManager#schedule")
    )
    override fun getMigrationTaskHandle(): ActiveTaskCodec = taskRunner.value.getTaskCodec()

    override suspend fun startRunningTasks(layer5Codec: Layer5Codec) {
        taskRunner.value.startTaskRunner(layer5Codec)
    }

    override suspend fun pauseRunningTasks() {
        if (taskRunner.isInitialized()) {
            taskRunner.value.stopTaskRunner()
        }
    }

    override fun <R> schedule(task: Task<R, TaskCodec>): Deferred<R> {
        logger.info("Scheduling new task: {}", task.getDebugString())

        val done = CompletableDeferred<R>()
        CoroutineScope(dispatchers.scheduleDispatcher.coroutineContext).launch {
            // Enqueue the task
            taskQueue.enqueueTask(task, done)

            logger.info("Task scheduled: {}", task.getDebugString())
        }
        return done
    }

    override fun hasPendingTasks(): Boolean = taskQueue.hasPendingTasks()

    override fun addQueueSendCompleteListener(listener: QueueSendCompleteListener) {
        synchronized(queueSendCompleteListeners) {
            queueSendCompleteListeners.add(listener)
        }
    }

    override fun removeQueueSendCompleteListener(listener: QueueSendCompleteListener) {
        synchronized(queueSendCompleteListeners) {
            queueSendCompleteListeners.remove(listener)
        }
    }

    override fun processInboundMessage(message: InboundMessage) {
        when (message) {
            is CspMessage -> processInboundCspMessage(message)
            is InboundD2mMessage -> processInboundD2mMessage(message)
        }
    }

    private fun processInboundD2mMessage(message: InboundD2mMessage) {
        logger.debug(
            "Processing inbound d2m message with payload type `{}`",
            message.payloadType.toHex()
        )
        schedule(message)
    }

    private fun processInboundCspMessage(message: CspMessage) {
        logger.debug(
            "Processing inbound csp message with payload type `{}`",
            message.payloadType.toHex()
        )

        when (message.payloadType.toInt()) {
            ProtocolDefines.PLTYPE_ERROR -> incomingMessageProcessor.processIncomingServerError(
                message.toServerErrorData()
            )

            ProtocolDefines.PLTYPE_ALERT -> incomingMessageProcessor.processIncomingServerAlert(
                message.toServerAlertData()
            )

            ProtocolDefines.PLTYPE_OUTGOING_MESSAGE_ACK -> schedule(message)
            ProtocolDefines.PLTYPE_INCOMING_MESSAGE -> schedule(message)
            ProtocolDefines.PLTYPE_QUEUE_SEND_COMPLETE -> notifyQueueSendComplete()
            ProtocolDefines.PLTYPE_DEVICE_COOKIE_CHANGE_INDICATION -> processDeviceCookieChangeIndication()
            else -> throw PayloadProcessingException("Unknown payload type ${message.payloadType}")
        }
    }

    private fun schedule(inboundMessage: InboundMessage) {
        logger.info(
            "Scheduling inbound message with payload type {}",
            inboundMessage.payloadType.toHex()
        )

        CoroutineScope(dispatchers.scheduleDispatcher.coroutineContext).launch {
            // Enqueue the message
            taskQueue.enqueueInboundMessage(inboundMessage)
        }
    }

    private fun processDeviceCookieChangeIndication() {
        deviceCookieManager.changeIndicationReceived()
        sendClearDeviceCookieChangeIndication()
    }

    private fun notifyQueueSendComplete() {
        synchronized(queueSendCompleteListeners) {
            // Iterate over a copy of the set to prevent concurrent modification. This is necessary,
            // because the queue complete listeners may remove themselves from the list inside
            // 'queueSendComplete'.
            queueSendCompleteListeners.toList().forEach {
                try {
                    it.queueSendComplete()
                } catch (e: Exception) {
                    logger.warn("Exception while invoking queue send complete listener", e)
                }
            }
        }
    }

    private fun sendClearDeviceCookieChangeIndication() {
        logger.debug("Clearing device cookie change indication")
        taskRunner.value.sendImmediately(
            CspMessage(
                ProtocolDefines.PLTYPE_CLEAR_DEVICE_COOKIE_CHANGE_INDICATION.toUByte(),
                byteArrayOf()
            )
        )
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
