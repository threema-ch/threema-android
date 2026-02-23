package ch.threema.domain.protocol.connection

import ch.threema.base.ThreemaException

open class ServerConnectionException : ThreemaException {
    constructor(msg: String?) : super(msg)
    constructor(msg: String?, cause: Throwable?) : super(msg, cause)
}

class InvalidSizeException(msg: String?) : ServerConnectionException(msg)
