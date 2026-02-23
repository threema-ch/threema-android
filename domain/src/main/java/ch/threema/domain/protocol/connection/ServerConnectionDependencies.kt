package ch.threema.domain.protocol.connection

import ch.threema.domain.protocol.connection.layer.ServerConnectionLayers
import ch.threema.domain.protocol.connection.socket.ServerSocket
import ch.threema.domain.protocol.connection.util.MainConnectionController
import ch.threema.domain.taskmanager.TaskManager

internal fun interface ServerConnectionDependencyProvider {
    fun create(connection: ServerConnection): ServerConnectionDependencies
}

internal data class ServerConnectionDependencies(
    val mainController: MainConnectionController,
    val socket: ServerSocket,
    val layers: ServerConnectionLayers,
    val connectionLockProvider: ConnectionLockProvider,
    val taskManager: TaskManager,
)
