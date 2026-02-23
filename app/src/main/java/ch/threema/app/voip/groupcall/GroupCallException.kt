package ch.threema.app.voip.groupcall

import ch.threema.base.ThreemaException

open class GroupCallException : ThreemaException {
    val callDescription: GroupCallDescription?

    constructor(msg: String, callDescription: GroupCallDescription? = null) : super(msg) {
        this.callDescription = callDescription
    }

    constructor(
        msg: String,
        cause: Throwable,
        callDescription: GroupCallDescription? = null,
    ) : super(msg, cause) {
        this.callDescription = callDescription
    }
}
