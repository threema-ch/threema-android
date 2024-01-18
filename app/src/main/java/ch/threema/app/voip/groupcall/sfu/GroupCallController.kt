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

package ch.threema.app.voip.groupcall.sfu

import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.sfu.connection.GroupCallConnectionState
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import org.webrtc.EglBase

interface GroupCallController {
    val callId: CallId

    /**
     *  Get a [Set] of all current call participants.
     *
     *  This includes the [LocalParticipant] as well as all [RemoteParticipant]s.
     */
    val participants: Flow<Set<Participant>>

    /**
     * Emits [Unit] when a capture state update of any call participant has been received
     */
    val captureStateUpdates: Flow<Unit>

    /**
     * A completable that completes when the call has been left (or ended exceptionally).
     *
     * This will complete right when the call end has been initiated. The call will be ended in a
     * background task and completion will occur _before_ that background task is completed.
     *
     * When this completes this does not necessarily mean that the call is not ongoing anymore but rather that this
     * user is not joining the call any longer.
     */
    val callLeftSignal: Deferred<Unit>

    /**
     * A signal that completes when the call has been ended and teared down. That is, when the final
     * [GroupCallConnectionState] has been completed and there are no more states to process.
     *
     * This signal might complete exceptionally if an exception occurs during
     * a call that has not been handled otherwise.
     *
     * As with the [callLeftSignal] when this completes this does not necessarily mean that the call
     * is not ongoing anymore but rather that this user is not joining the call any longer, and the
     * call has been teared down locally.
     */
    val callDisposedSignal: Deferred<Unit>

    val connectedSignal: Deferred<Pair<ULong, Set<ParticipantId>>>

    val eglBase: EglBase

    var description: GroupCallDescription
    val descriptionSignal: Deferred<GroupCallDescription>

    var microphoneActive: Boolean

    var cameraActive: Boolean

    @UiThread
    suspend fun flipCamera()

    /**
     * Leave the call.
     *
     * Must signal leaving of the call to the SFU and discard / close all open data channels
     *
     * This method returns immediately and will exceute the leave-logic of the call in a background task.
     */
    @AnyThread
    fun leave()

    /**
     * Return true if the leave timer has triggered the group call leave, false otherwise.
     */
    fun hasForeverAloneTimerFired(): Boolean

    /**
     * During the connection sequence the call must be confirmed or declined after the CONNECTING state
     * before moving on to the CONNECTED state
     */
    @WorkerThread
    fun confirmCall()

    @WorkerThread
    fun declineCall()

    /**
     * Remove all participants (including self) from the call that are no members of the call's group.
     *
     * If the local participant is no longer a member of the group, the call must be left.
     *
     * @param groupMembers A set containing all group member's identities
     */
    @WorkerThread
    fun purgeCallParticipants(groupMembers: Set<String>)
}
