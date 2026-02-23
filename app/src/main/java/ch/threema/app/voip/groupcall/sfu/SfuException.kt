package ch.threema.app.voip.groupcall.sfu

import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.GroupCallException

class SfuException : GroupCallException {
    val statusCode: Int?

    constructor(
        msg: String,
        callDescription: GroupCallDescription? = null,
    ) : super(msg, callDescription) {
        statusCode = null
    }

    constructor(
        msg: String,
        statusCode: Int,
        callDescription: GroupCallDescription? = null,
    ) : super(msg, callDescription) {
        this.statusCode = statusCode
    }

    constructor(
        msg: String,
        cause: Throwable,
        callDescription: GroupCallDescription? = null,
    ) : super(msg, cause, callDescription) {
        statusCode = null
    }
}
