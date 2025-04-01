/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.voip.groupcall.service

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.CallAudioManager
import ch.threema.app.voip.groupcall.sfu.GroupCallController
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.*

private val logger = LoggingUtil.getThreemaLogger("GroupCallServiceConnection")

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
