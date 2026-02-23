package ch.threema.domain.protocol.connection.socket

/**
 * The reason for closing the socket.
 * It is also possible to indicate whether it is allowed to reconnect after the connection has been
 * closed. If this flag is set to `false` no reconnects will be attempted. If set to `true` reconnects
 * _might_ be attempted if it has not been prohibited by other mechanisms (e.g. the connection monitoring).
 *
 * @param msg A message explaining why the socket was closed. Only used for logging
 * @param reconnectAllowed Indicator whether it is allowed to attempt a reconnect
 */
open class ServerSocketCloseReason(val msg: String, val reconnectAllowed: Boolean? = null) {
    override fun toString(): String {
        return "ServerSocketCloseReason(msg='$msg', reconnectAllowed=$reconnectAllowed)"
    }
}
