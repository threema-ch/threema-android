package ch.threema.app.voip.groupcall.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.CallAudioManager
import ch.threema.app.voip.groupcall.sfu.GroupCallController
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.*

private val logger = getThreemaLogger("GroupCallServiceConnection")

class GroupCallServiceConnection : ServiceConnection {
    private var groupCallController: GroupCallController? = null
    private val deferredServiceBinder: CompletableDeferred<GroupCallServiceBinder> =
        CompletableDeferred()
    private var disconnected = false

    override fun onServiceConnected(name: ComponentName?, serviceBinder: IBinder?) {
        logger.debug("Service connected")
        if (serviceBinder !is GroupCallServiceBinder) {
            deferredServiceBinder.completeExceptionally(ThreemaException("Bound to incompatible service"))
        } else {
            deferredServiceBinder.complete(serviceBinder)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        logger.debug("Service disconnected")
        disconnected = true
        deferredServiceBinder.completeExceptionally(ThreemaException("Service disconnected"))
    }

    /**
     * Get the GroupCallController as soon as it is available.
     *
     * Note that this call will suspend until a GroupCallController is available.
     * If the service is not connected (not bound, not started, crashed, ...)
     * it may never return a value.
     */
    @WorkerThread
    suspend fun getGroupCallController(): GroupCallController {
        return if (disconnected) {
            throw ThreemaException("Service disconnected")
        } else {
            deferredServiceBinder.await().getGroupCallController().also {
                groupCallController = it
            }
        }
    }

    @WorkerThread
    suspend fun getCallAudioManager(): CallAudioManager {
        return if (disconnected) {
            throw ThreemaException("Service disconnected")
        } else {
            deferredServiceBinder.await().getCallAudioManager()
        }
    }

    /**
     * Get the current group controller. This will only return a GroupCallController
     * if {@link #getGroupCallController} has been successfully called previously and the call has
     * not yet been ended
     *
     * @return the current GroupCallController or null if the GroupCallService is not bound
     *         or there is no joined call running
     */
    @AnyThread
    fun getCurrentGroupCallController(): GroupCallController? {
        return if (groupCallController?.callLeftSignal?.isCompleted == true) {
            null
        } else {
            groupCallController
        }
    }
}
