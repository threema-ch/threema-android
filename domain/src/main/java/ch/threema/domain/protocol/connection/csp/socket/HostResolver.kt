package ch.threema.domain.protocol.connection.csp.socket

import java.net.InetAddress
import java.util.concurrent.ExecutionException

fun interface HostResolver {
    @Throws(ExecutionException::class, InterruptedException::class)
    fun getAllByName(name: String): Array<InetAddress>
}
