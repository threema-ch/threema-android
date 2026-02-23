package ch.threema.app.voip.groupcall.sfu.webrtc

import androidx.annotation.WorkerThread
import ch.threema.app.webrtc.WrappedDataChannelObserver
import ch.threema.base.utils.getThreemaLogger
import org.webrtc.DataChannel

private val logger = getThreemaLogger("DataChannelCtx")

internal class DataChannelCtx(
    val dc: DataChannel,
    val observer: WrappedDataChannelObserver,
) {
    /**
     * IMPORTANT: Make sure this is executed in the ConnectionCtx-Worker
     */
    @WorkerThread
    fun teardown() {
        logger.trace("Teardown: DataChannelCtx")

        // DataChannel
        logger.trace("Teardown: DataChannel")
        dc.unregisterObserver()
        dc.close()
        dc.dispose()

        logger.trace("Teardown: /DataChannelCtx")
    }
}
