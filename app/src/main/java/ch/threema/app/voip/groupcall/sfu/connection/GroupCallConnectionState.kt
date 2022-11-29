/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.voip.groupcall.sfu.connection

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.*

private val logger = LoggingUtil.getThreemaLogger("GroupCallConnectionState")

sealed class GroupCallConnectionState constructor(
    val stateName: StateName,
    internal val call: GroupCall
) {
    enum class StateName {
        JOINING,
        CONNECTING,
        CONNECTED,
        STOPPED,
        FAILED,
    }

    @WorkerThread
    suspend fun process() {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.debug("Process state {}", stateName)
        call.updateState(this)
        val state = getNextState()
        state?.process()
    }

    @AnyThread
    protected suspend fun observeCallEnd(): GroupCallConnectionState {
        call.callLeftSignal.await()
        return Stopped(call)
    }

    /**
     * Return a list of state providers. All the state providers are run simultaneously in a thread pool.
     * Therefore there is no guarantee for a state provider to be run on a specific thread.
     *
     * Whichever provider returns a state first "wins". When one state provider has returned the next state
     * the other ones are cancelled and joined, before the new state will be propagated.
     */
    @WorkerThread
    abstract fun getStateProviders(): List<suspend () -> GroupCallConnectionState?>

    @WorkerThread
    private suspend fun getNextState(): GroupCallConnectionState? {
        GroupCallThreadUtil.assertDispatcherThread()

        val nextStateSignal = CompletableDeferred<GroupCallConnectionState?>()

        val jobs = getStateProviders()
            .map { processStateProvider(it, nextStateSignal) }

        val nextState = try {
            nextStateSignal.await()
        } catch (e: Exception) {
            Failed(call, e)
        }

        jobs.forEach { it.cancel() }
        jobs.joinAll()

        return nextState
    }

    @WorkerThread
    private fun processStateProvider(
        stateProvider: suspend () -> GroupCallConnectionState?,
        nextStateSignal: CompletableDeferred<GroupCallConnectionState?>
    ): Job {
        GroupCallThreadUtil.assertDispatcherThread()

        return CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
            try {
                nextStateSignal.complete(stateProvider.invoke())
            } catch (e: Exception) {
                nextStateSignal.complete(Failed(call, e))
            }
        }
    }

}
