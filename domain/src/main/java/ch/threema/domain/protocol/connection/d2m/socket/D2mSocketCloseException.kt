package ch.threema.domain.protocol.connection.d2m.socket

import ch.threema.domain.protocol.connection.socket.ServerSocketException

class D2mSocketCloseException(msg: String, private val closeCode: D2mCloseCode) :
    ServerSocketException(msg) {
    override val message: String
        get() = "${super.message}, $closeCode"
}
