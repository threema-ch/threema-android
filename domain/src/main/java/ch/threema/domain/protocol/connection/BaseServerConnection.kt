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

import androidx.annotation.WorkerThread
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.protocol.Version
import ch.threema.domain.protocol.connection.csp.DeviceCookieManager
import ch.threema.domain.protocol.connection.socket.ServerSocket
import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason
import ch.threema.domain.protocol.connection.util.ConnectionLoggingUtil
import ch.threema.domain.protocol.connection.util.MainConnectionController
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.IncomingMessageProcessor
import ch.threema.domain.taskmanager.QueueSendCompleteListener
import ch.threema.domain.taskmanager.TaskManager
import java.io.IOException
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val logger = ConnectionLoggingUtil.getConnectionLogger("BaseServerConnection")

/**
 * The [BaseServerConnection] is an (abstract) implementation of the [ServerConnection] that utilises
 * different layers for handling different aspects of the connection:
 *  - Layer 1: Decodes the bytes received from the server into a container format
 *  - Layer 2: Demultiplexes the container into messages from different protocols (e.g. CSP, D2M)
 *  - Layer 3: Handles the authentication to the server and the transport encryption
 *  - Layer 4: Monitors the connection, reacts to some control message and sends keepalive echo requests
 *  - Layer 5: Dispatches the messages to the task manager for further processing
 *
 * Messages received in the layer 5 ([ch.threema.domain.protocol.connection.layer.EndToEndLayer]) are
 * passed on to the [ch.threema.domain.taskmanager.TaskManager] by the EndToEnd layer for further
 * processing.
 */
internal abstract class BaseServerConnection(
    private val dependencyProvider: ServerConnectionDependencyProvider,
) : ServerConnection, ServerConnectionDispatcher.ExceptionHandler {
    private val connectionStateListeners = mutableSetOf<ConnectionStateListener>()

    private val stateLock = ReentrantLock()

    @Volatile
    private var state: ConnectionState = ConnectionState.DISCONNECTED

    override val connectionState: ConnectionState
        get() = stateLock.withLock { state }

    private val running = AtomicBoolean(false)
    override val isRunning: Boolean
        get() = running.get() || connectionJob?.isActive == true

    protected val socket: ServerSocket
        get() = dependencies.socket

    private lateinit var dependencies: ServerConnectionDependencies

    private var connectionLock: ConnectionLock? = null

    private var reconnectAllowed = AtomicBoolean(true)

    @Volatile
    private var isReconnect = false

    override val isNewConnectionSession: Boolean
        get() = !isReconnect

    private var reconnectAttemptsSinceLastLogin = 0
    private var ioJob: Job? = null

    private var connectionJob: Job? = null

    private val canConnect: Boolean
        get() = running.get() && reconnectAllowed.get()

    protected val controller: MainConnectionController
        get() = dependencies.mainController

    override fun disableReconnect() {
        reconnectAllowed.set(false)
    }

    override fun handleException(throwable: Throwable) {
        logger.error("Exception in connection dispatcher; Cancel io processing")
        if (this::dependencies.isInitialized) {
            controller.ioProcessingStoppedSignal.completeExceptionally(throwable)
            controller.dispatcher.close()
        }
    }

    override fun addConnectionStateListener(listener: ConnectionStateListener) {
        synchronized(connectionStateListeners) {
            connectionStateListeners.add(listener)
        }
    }

    override fun removeConnectionStateListener(listener: ConnectionStateListener) {
        synchronized(connectionStateListeners) {
            connectionStateListeners.remove(listener)
        }
    }

    final override fun start() {
        logger.info("Start")

        if (running.getAndSet(true) || connectionJob?.isActive == true) {
            logger.warn("Connection is already running")
            return
        }

        if (connectionState != ConnectionState.DISCONNECTED) {
            logger.warn("Connection is not disconnected. Abort connecting.")
            return
        }

        isReconnect = false
        // Allow reconnect attempts in a new session
        reconnectAllowed.set(true)

        connectionJob = CoroutineScope(Dispatchers.Default).launch {
            while (canConnect) {
                var monitorCloseEventJob: Job? = null
                var queueSendCompleteListener: QueueSendCompleteListener? = null
                try {
                    setup()

                    logger.debug("Start connecting")
                    setConnectionState(ConnectionState.CONNECTING)
                    socket.connect()
                    setConnectionState(ConnectionState.CONNECTED)

                    connectionLock = dependencies.connectionLockProvider.acquire(
                        60_000,
                        ConnectionLockProvider.ConnectionLogTag.PURGE_INCOMING_MESSAGE_QUEUE,
                    )

                    // To prevent races where this while loop has been entered just before stop()
                    // has been called, and stop() has been called before the socket was
                    // initialized, check again if a reconnect is still allowed. Otherwise, close
                    // the socket and abort connection.
                    if (!reconnectAllowed.get()) {
                        socket.close(ServerSocketCloseReason("Reconnect not allowed"))
                        connectionLock?.release()
                        break
                    }

                    // We must keep the CPU awake until we have processed all incoming messages
                    // to avoid missing messages in deeper sleep states.
                    queueSendCompleteListener = QueueSendCompleteListener {
                        logger.info("CSP queue was processed, releasing connection lock")
                        connectionLock?.release()
                    }
                    // The listener must be registered before processing io has been started.
                    // Otherwise the queue send complete event could already have been triggered
                    // before the listener was added.
                    dependencies.taskManager.addQueueSendCompleteListener(queueSendCompleteListener)

                    // Handle IO until the connection dies
                    ioJob = launch { processIo() }

                    controller.connected.complete(Unit)
                    onConnected()

                    val waitForCspAuthenticatedJob = launch {
                        controller.cspAuthenticated.await()
                        onCspAuthenticated()
                        reconnectAttemptsSinceLastLogin = 0
                        setConnectionState(ConnectionState.LOGGEDIN)
                    }
                    // Monitor close events of the socket
                    monitorCloseEventJob = launch {
                        val reason = socket.closedSignal.await()
                        logger.warn("Socket was closed, reason={}", reason)
                        if (reason.reconnectAllowed == false) {
                            disableReconnect()
                        }
                        onSocketClosed(reason)
                        if (!waitForCspAuthenticatedJob.isCompleted) {
                            // Cancel awaiting the csp authentication when the socket is closed
                            // as it will never complete
                            logger.debug("Cancel waiting for csp authentication.")
                            waitForCspAuthenticatedJob.cancel()
                        } else {
                            logger.debug("Csp authentication already completed")
                        }
                        logger.debug("Socket watchdog completed")
                    }

                    waitForCspAuthenticatedJob.join()

                    ioJob?.join()
                } catch (exception: Exception) {
                    logger.error("Connection exception", exception)

                    onException(exception)
                }

                setConnectionState(ConnectionState.DISCONNECTED)

                closeSocket("Disconnected")

                controller.connectionClosed.complete(Unit)

                queueSendCompleteListener?.let { dependencies.taskManager.removeQueueSendCompleteListener(it) }

                if (canConnect) {
                    prepareReconnect()
                }
                connectionLock?.release()
                monitorCloseEventJob?.cancel()
            }
            logger.info("Connection ended")
            running.set(false)
        }
    }

    @WorkerThread
    @Throws(InterruptedException::class)
    override fun stop() {
        synchronized(this) {
            if (running.get()) {
                logger.info("Stop")
                disableReconnect()
                closeSocket("Connection stopped")
                logger.trace("Join connection job")
                runBlocking { connectionJob?.join() }
                logger.trace("Connection job joined")
                controller.dispatcher.close()
                logger.info("Connection is stopped")
            } else {
                logger.warn("Connection has not been started or is already stopped")
            }
            setConnectionState(ConnectionState.DISCONNECTED)
        }
    }

    /**
     * Called when the socket connection to the server has been established.
     */
    protected open fun onConnected() {}

    /**
     * Called when the csp handshake has been completed.
     */
    protected open fun onCspAuthenticated() {}

    /**
     * Called when an exception occurs during establishing the connection or if processing io has been
     * stopped exceptionally.
     * Note that exceptions that this method will not be called with exceptions that occurred while
     * processing messages in the pipelines.
     * If this method is called it means that the connection has failed and will be disconnected. It may
     * be reconnected subsequently depending on the state of the connection.
     */
    protected open fun onException(t: Throwable) {}

    /**
     * Called when the server socket has been closed.
     */
    protected open fun onSocketClosed(reason: ServerSocketCloseReason) {}

    private fun setConnectionState(state: ConnectionState) {
        stateLock.withLock {
            val previousState = this.state
            this.state = state

            synchronized(connectionStateListeners) {
                if (previousState != this.state) {
                    logger.debug(
                        "Notify connection state listeners. state={}, address={}",
                        state,
                        socket.address,
                    )
                    connectionStateListeners.forEach { listener ->
                        try {
                            listener.updateConnectionState(state)
                        } catch (e: Exception) {
                            logger.warn("Exception while invoking connection state listener", e)
                        }
                    }
                }
            }
        }
    }

    private fun setup() {
        dependencies = dependencyProvider.create(this)
        dependencies.mainController.dispatcher.exceptionHandler = this

        val socket = dependencies.socket
        val layers = dependencies.layers

        // Setup io pipeline
        socket.source
            .pipeThrough(layers.layer1Codec.decoder)
            .pipeThrough(layers.layer2Codec.decoder)
            .pipeThrough(layers.layer3Codec.decoder)
            .pipeThrough(layers.layer4Codec.decoder)
            .pipeInto(layers.layer5Codec)

        layers.layer5Codec.source
            .pipeThrough(layers.layer4Codec.encoder)
            .pipeThrough(layers.layer3Codec.encoder)
            .pipeThrough(layers.layer2Codec.encoder)
            .pipeThrough(layers.layer1Codec.encoder)
            .pipeInto(socket)
    }

    /**
     * Process IO of the underlying socket.
     *
     * This will continue until there is either an exception while processing or the
     * connection has been closed.
     */
    private suspend fun processIo() {
        try {
            socket.processIo()
        } catch (e: SocketException) {
            // This exception is thrown on a regular basis
            // e.g. when the server closes the connection or when the socket is closed
            // during device sleep.
            // Since we do not want to flood the log with redundant stack traces only
            // the exception message is logged
            logger.warn("Socket exception while processing io: {}", e.message)
        } catch (e: Exception) {
            logger.error("Connection exception while processing io", e)
        }
    }

    private fun joinIoProcessing() {
        logger.trace("Join io processing job")
        runBlocking { ioJob?.join() }
        logger.trace("Io processing joined")
    }

    private fun closeSocket(msg: String) {
        logger.info("Close socket")
        try {
            socket.close(ServerSocketCloseReason(msg))
        } catch (e: IOException) {
            logger.warn("Exception when closing socket", e)
        }
    }

    /**
     * Make sure [ServerSocket.close] has been called prior to reconnecting (or stopping of io processing
     * has been initiated by other means).
     * There might be deadlocks otherwise.
     * This methods also waits for a calculated delay based on the previous reconnect attempts before
     * returning.
     */
    private suspend fun prepareReconnect() {
        logger.debug("Prepare reconnect")
        isReconnect = true
        reconnectAttemptsSinceLastLogin++
        try {
            joinIoProcessing()
            /* Don't reconnect too quickly */
            val reconnectDelay = getReconnectDelay()
            logger.info("Waiting {} milliseconds before reconnecting", reconnectDelay)
            delay(reconnectDelay)
        } catch (e: CancellationException) {
            logger.debug("Reconnect cancelled", e)
            disableReconnect()
        }
    }

    /**
     * Calculate the reconnect delay with bounded exponential backoff.
     */
    private fun getReconnectDelay(): Long {
        val base = ProtocolDefines.RECONNECT_BASE_INTERVAL.toDouble()
        val exponent = min(reconnectAttemptsSinceLastLogin - 1, 10)
        val reconnectDelayS = base.pow(exponent)
        val delayS = min(reconnectDelayS, ProtocolDefines.RECONNECT_MAX_INTERVAL.toDouble())
        return (delayS * 1000).toLong()
    }
}

/**
 * The lock keeps the device awake and prevents the app from being stopped while in the background.
 */
interface ConnectionLock {
    /**
     * Release the lock when the device can go to sleep.
     */
    fun release()

    /**
     * Returns true if the lock currently keeps the device awake. If false is returned, the
     * connection lock has either been released or timed out.
     */
    fun isHeld(): Boolean
}

interface ConnectionLockProvider {
    enum class ConnectionLogTag {
        PURGE_INCOMING_MESSAGE_QUEUE,
        INBOUND_MESSAGE,
    }

    fun acquire(timeoutMillis: Long, tag: ConnectionLogTag): ConnectionLock
}

interface BaseServerConnectionConfiguration {
    val identityStore: IdentityStore
    val serverAddressProvider: ServerAddressProvider
    val version: Version

    /**
     * If set to `true` it will be asserted that received messages
     * are actually processed in the connection's [ServerConnectionDispatcher]'s
     * context.
     * If the messages are not processed in the correct context and
     * [assertDispatcherContext] set to `true`, an [Error] will
     * be thrown.
     * This is meant for development purposes and should be disabled in production.
     */
    val assertDispatcherContext: Boolean

    val deviceCookieManager: DeviceCookieManager

    val incomingMessageProcessor: IncomingMessageProcessor

    val taskManager: TaskManager
}
