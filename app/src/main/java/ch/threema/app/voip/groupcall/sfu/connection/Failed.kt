package ch.threema.app.voip.groupcall.sfu.connection

import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.GroupCall
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("GroupCallConnectionState.Failed")

class Failed internal constructor(call: GroupCall, val reason: Throwable) :
    GroupCallConnectionState(StateName.FAILED, call) {
    @WorkerThread
    override fun getStateProviders() = listOf(
        suspend {
            GroupCallThreadUtil.assertDispatcherThread()

            // Make sure the [connectedSignal] is completed in case someone is waiting for it
            call.completableConnectedSignal.completeExceptionally(reason)

            logger.error("Call failed, tearing down\n{}", reason.stackTraceToString())
            call.teardown()
            null
        },
    )
}
