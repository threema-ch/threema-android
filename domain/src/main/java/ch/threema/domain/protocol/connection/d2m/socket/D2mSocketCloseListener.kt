package ch.threema.domain.protocol.connection.d2m.socket

import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason

fun interface D2mSocketCloseListener {
    fun onSocketClosed(reason: ServerSocketCloseReason)
}
