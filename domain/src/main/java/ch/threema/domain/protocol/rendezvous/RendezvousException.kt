package ch.threema.domain.protocol.rendezvous

import ch.threema.base.ThreemaException

class RendezvousException : ThreemaException {
    constructor(msg: String) : super(msg)
    constructor(msg: String, throwable: Throwable) : super(msg, throwable)
}
