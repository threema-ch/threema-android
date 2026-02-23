package ch.threema.domain.protocol.connection.csp.socket

import java.net.InetSocketAddress
import java.net.Socket

fun interface SocketFactory {
    fun makeSocket(address: InetSocketAddress): Socket
}
