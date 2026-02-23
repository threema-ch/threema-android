package ch.threema.domain.protocol.connection.d2m.socket

import ch.threema.domain.protocol.connection.socket.ServerSocketCloseReason

class D2mSocketCloseReason(msg: String, val closeCode: D2mCloseCode) :
    ServerSocketCloseReason(msg, closeCode.isReconnectAllowed()) {
    override fun toString(): String {
        return "D2mSocketCloseReason(msg='$msg', closeCode=$closeCode, reconnectAllowed=$reconnectAllowed)"
    }
}
