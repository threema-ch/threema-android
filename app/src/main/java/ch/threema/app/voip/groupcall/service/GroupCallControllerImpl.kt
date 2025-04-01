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

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.GroupCallException
import ch.threema.app.voip.groupcall.GroupCallThreadUtil
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.app.voip.groupcall.sfu.connection.Failed
import ch.threema.app.voip.groupcall.sfu.connection.GroupCallConnectionState
import ch.threema.app.voip.groupcall.sfu.connection.Joining
import ch.threema.app.voip.groupcall.sfu.messages.P2PMessageContent
import ch.threema.app.voip.groupcall.sfu.webrtc.RemoteCtx
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.ContactModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.webrtc.EglBase
import java.lang.Runnable
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = LoggingUtil.getThreemaLogger("GroupCallControllerImpl")

internal class GroupCallControllerImpl(
    override val callId: CallId,
    private val onLeave: Runnable,
    private val me: ContactModel,
) : GroupCallController, GroupCall {
    private val confirmCallSignal = CompletableDeferred<Unit>()
    private val descriptionSetSignal = CompletableDeferred<Unit>()
    private val mutableParticipants = MutableSharedFlow<Set<Participant>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val mutableUpdateCaptureState = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var remoteCtxs: MutableMap<ParticipantId, RemoteCtx> = mutableMapOf()
    private var remoteParticipants: MutableSet<NormalRemoteParticipant> = mutableSetOf()

    private val teardownLock = ReentrantLock()
    private val teardownRoutines: MutableList<suspend () -> Unit> = mutableListOf()

    private var _description: GroupCallDescription? = null
    override var description: GroupCallDescription
        set(value) {
            _description = value
            descriptionSetSignal.complete(Unit)
        }
        get() = _description.let {
            if (!descriptionSetSignal.isCompleted || it == null) {
                throw GroupCallException("Description has not been initialized")
            }
            it
        }
    override val descriptionSignal: Deferred<GroupCallDescription> by lazy {
        CompletableDeferred<GroupCallDescription>().also {
            CoroutineScope(Dispatchers.Default).launch {
                descriptionSetSignal.await()
                it.complete(description)
            }
        }
    }


    override lateinit var parameters: GroupCallParameters

    override lateinit var dependencies: GroupCallDependencies

    override val callConfirmedSignal: Deferred<Unit> = confirmCallSignal

    override val callLeftSignal: CompletableDeferred<Unit> = CompletableDeferred()
    override val callDisposedSignal: CompletableDeferred<Unit> = CompletableDeferred()

    override val completableConnectedSignal: CompletableDeferred<Pair<ULong, Set<ParticipantId>>> =
        CompletableDeferred()
    override val connectedSignal: Deferred<Pair<ULong, Set<ParticipantId>>> =
        completableConnectedSignal

    override val dislodgedParticipants = MutableSharedFlow<ParticipantId>()

    private lateinit var _eglBase: EglBase
    override val eglBase: EglBase
        get() = if (this::_eglBase.isInitialized) {
            _eglBase
        } else {
            throw GroupCallException("EglBase is not initialized")
        }

    override lateinit var context: GroupCallContext

    private lateinit var localParticipant: LocalParticipant

    override val participants: Flow<Set<Participant>>
        get() = ifCallIsRunning {
            logger.trace("Get participants")
            mutableParticipants.asSharedFlow()
        }

    override val captureStateUpdates: Flow<Unit>
        get() = ifCallIsRunning {
            mutableUpdateCaptureState.asSharedFlow()
        }

    override var microphoneActive: Boolean
        get() = ifCallIsRunning { localParticipant.microphoneActive }
        set(value) = ifCallIsRunning {
            localParticipant.microphoneActive = value
            val stateUpdate =
                P2PMessageContent.CaptureState.Microphone(localParticipant.microphoneActive)
            context.sendBroadcast(stateUpdate)
        }

    override var cameraActive: Boolean
        get() = ifCallIsRunning { localParticipant.cameraActive }
        set(value) = ifCallIsRunning {
            localParticipant.cameraActive = value
            val stateUpdate = P2PMessageContent.CaptureState.Camera(localParticipant.cameraActive)
            context.sendBroadcast(stateUpdate)
        }

    private var foreverAloneTimeoutFired: Boolean = false

    private val groupCallLeaveTimer: GroupCallLeaveTimer = GroupCallLeaveTimer(participants) {
        foreverAloneTimeoutFired = true
        leave()
    }

    override fun hasForeverAloneTimerFired(): Boolean = foreverAloneTimeoutFired

    @WorkerThread
    override fun confirmCall() {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.trace("Confirm call")
        confirmCallSignal.complete(Unit)
    }

    @WorkerThread
    override fun declineCall() {
        GroupCallThreadUtil.assertDispatcherThread()

        logger.trace("Decline call")
        confirmCallSignal.completeExceptionally(GroupCallException("Call declined"))
    }

    @UiThread
    override suspend fun flipCamera() = suspendingIfCallIsRunning {
        localParticipant.flipCamera()
    }

    @AnyThread
    override fun leave() {
        logger.debug("Leave call")
        if (callLeftSignal.isCompleted) {
            logger.warn("Attempt to leave call that has already been left")
        } else {
            callLeftSignal.complete(Unit)
            groupCallLeaveTimer.disable()
            onLeave.run()
        }
    }

    @WorkerThread
    override fun setRemoteCtx(participantId: ParticipantId, remote: RemoteCtx) {
        GroupCallThreadUtil.assertDispatcherThread()

        remoteCtxs[participantId] = remote
    }

    @WorkerThread
    override fun setParticipant(participant: LocalParticipant) {
        GroupCallThreadUtil.assertDispatcherThread()

        if (this::localParticipant.isInitialized) {
            logger.warn("Local participant is already set")
        }
        localParticipant = participant
    }

    @WorkerThread
    override fun purgeCallParticipants(groupMembers: Set<String>) {
        logger.info("Purge call participants")
        GroupCallThreadUtil.assertDispatcherThread()

        if (localParticipant.identity !in groupMembers) {
            logger.info("Not in group anymore. Leave call.")
            leave()
        } else {
            CoroutineScope(Dispatchers.Default).launch {
                remoteParticipants
                    .filter { it.identity !in groupMembers }
                    .forEach {
                        logger.info("Dislodge participant {}", it.id)
                        dislodgedParticipants.emit(it.id)
                    }
            }
        }
    }

    @WorkerThread
    override fun updateParticipants(update: GroupCall.ParticipantsUpdate) {
        GroupCallThreadUtil.assertDispatcherThread()

        remoteParticipants.removeAll(update.remove)
        remoteParticipants.addAll(update.add)

        remoteParticipants.forEach {
            val remoteCtx = remoteCtxs[it.id]
            if (remoteCtx != null) {
                it.remoteCtx = remoteCtx
            } else {
                logger.warn("No remote context for {}", it.id)
            }
        }
        emitParticipants()
    }

    @WorkerThread
    private fun emitParticipants() {
        GroupCallThreadUtil.assertDispatcherThread()

        val allParticipants = mutableSetOf<Participant>()
        if (this::localParticipant.isInitialized) {
            allParticipants.add(localParticipant)
        }
        allParticipants.addAll(remoteParticipants)
        mutableParticipants.tryEmit(allParticipants.toSet())
    }

    @WorkerThread
    override fun updateCaptureStates() {
        GroupCallThreadUtil.assertDispatcherThread()

        mutableUpdateCaptureState.tryEmit(Unit)
    }

    @WorkerThread
    override fun updateState(state: GroupCallConnectionState) {
        GroupCallThreadUtil.assertDispatcherThread()

        when (state) {
            is Failed -> {
                val completed = callLeftSignal.completeExceptionally(state.reason)
                logger.debug("Completed exceptionally: {}", completed)
                groupCallLeaveTimer.disable()
                onLeave.run()
            }

            else -> logger.debug("Unhandled state update: {}", state)
        }
    }

    @WorkerThread
    override fun setEglBase(eglBase: EglBase) {
        GroupCallThreadUtil.assertDispatcherThread()

        if (this::_eglBase.isInitialized) {
            throw GroupCallException("EglBase is already set")
        }
        _eglBase = eglBase
    }

    @WorkerThread
    suspend fun join(
        context: Context,
        sfuBaseUrl: String,
        sfuConnection: SfuConnection,
        onError: () -> Unit
    ) {
        GroupCallThreadUtil.assertDispatcherThread()
        GroupCallThreadUtil.exceptionHandler = object : GroupCallThreadUtil.ExceptionHandler {
            override fun handle(t: Throwable) {
                GroupCallThreadUtil.exceptionHandler = null
                this@GroupCallControllerImpl.callLeftSignal.completeExceptionally(t)
            }
        }
        try {
            descriptionSetSignal.await()
            Joining(
                this@GroupCallControllerImpl,
                sfuBaseUrl,
                context,
                me,
                sfuConnection
            ).process()
            // This is only reached _after_ the call has been teared down
            callDisposedSignal.complete(Unit)
        } catch (e: Exception) {
            onError()
            descriptionSetSignal.completeExceptionally(e)
            callDisposedSignal.completeExceptionally(e)
        }
    }

    @WorkerThread
    override fun addTeardownRoutine(routine: suspend () -> Unit) {
        GroupCallThreadUtil.assertDispatcherThread()

        teardownRoutines.add(routine)
    }

    @WorkerThread
    override suspend fun teardown() {
        GroupCallThreadUtil.assertDispatcherThread()

        val routines: Deque<suspend () -> Unit> = teardownLock.withLock {
            val pendingRoutines = LinkedList(teardownRoutines)
            teardownRoutines.clear()
            pendingRoutines
        }

        while (routines.isNotEmpty()) {
            routines.pollLast()?.invoke()
        }
    }

    @AnyThread
    private fun <T> ifCallIsRunning(supplier: () -> T): T {
        return if (callLeftSignal.isCompleted) {
            throw ThreemaException("Call has already ended")
        } else {
            supplier()
        }
    }

    @AnyThread
    private suspend fun <T> suspendingIfCallIsRunning(supplier: suspend () -> T): T {
        return if (callLeftSignal.isCompleted) {
            throw ThreemaException("Call has already ended")
        } else {
            supplier()
        }
    }
}
