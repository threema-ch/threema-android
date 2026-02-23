package ch.threema.app.voip.groupcall.sfu.connection

import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.GroupCall
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("GroupCallConnectionState.Stopped")

class Stopped internal constructor(call: GroupCall) :
    GroupCallConnectionState(StateName.STOPPED, call) {
    @WorkerThread
    override fun getStateProviders() = listOf(
        suspend {
            GroupCallThreadUtil.assertDispatcherThread()

            logger.info("Call stopped, tearing down")
            call.teardown()
            null
        },
    )
}
