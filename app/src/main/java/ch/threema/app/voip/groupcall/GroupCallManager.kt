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

package ch.threema.app.voip.groupcall

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.voip.CallAudioManager
import ch.threema.app.voip.groupcall.sfu.GroupCallController
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallControlMessage
import ch.threema.storage.models.GroupModel

@JvmInline
value class LocalGroupId(val id: Int)

val GroupModel.localGroupId: LocalGroupId
    get() = LocalGroupId(id)

interface GroupCallManager {
    /**
     * Handle a group call control message to update the known group calls. Should perform the group
     * call refresh steps when a new message is handled.
     *
     * If a new group call or group call ring is received, the GroupCallManager is responsible for
     * showing an appropriate notification.
     *
     * @return true if the message has been processed successfully, false otherwise
     */
    @WorkerThread
    fun handleControlMessage(message: GroupCallControlMessage): Boolean

    /**
     * Get the [CallAudioManager] which allows audio control during the call.
     */
    @WorkerThread
    suspend fun getAudioManager(): CallAudioManager

    /**
     * Add an observer for call states updates. The observer will be called with the current state
     * every time there has been a change in the group call state of the specified group.
     *
     * Group call state changes can for example occur when a new GroupCallControlMessage has been handled,
     * the group call refresh steps have been performed or a call has been ended.
     *
     * @param groupId The [LocalGroupId] for the group to observe the call state
     */
    @AnyThread
    fun addGroupCallObserver(groupId: LocalGroupId, observer: GroupCallObserver)
    @AnyThread
    fun addGroupCallObserver(group: GroupModel, observer: GroupCallObserver)

    @AnyThread
    fun removeGroupCallObserver(groupId: LocalGroupId, observer: GroupCallObserver)
    @AnyThread
    fun removeGroupCallObserver(group: GroupModel, observer: GroupCallObserver)

    /**
     * The same as [addGroupCallObserver] with the difference that the observer is notified of updates
     * of group calls in any group.
     *
     * Immediately upon registration the observer is notified with the [GroupCallDescription] of the running
     * call if any or `null` otherwise.
     */
    @AnyThread
    fun addGeneralGroupCallObserver(observer: GroupCallObserver)

    @AnyThread
    fun removeGeneralGroupCallObserver(observer: GroupCallObserver)

    /**
     * Join a GroupCall that is currently running.
     *
     * If there is no call considered running for this group no new call will be started.
     *
     * If the call for this group is already joined the method should return the corresponding
     * GroupCallController without executing any further connection steps.
     *
     * When joining a group call a connection to the sfu will be established.
     *
     * Note: There can only be ONE call (be it 1:1 or be it GroupCall) at any time! If there is another
     * ongoing call the other call must be ended.
     *
     * @param group The group in which the call should be joined
     *
     * @return The [GroupCallController] of the joined call or `null` if no call is running in this group
     */
    @WorkerThread
    suspend fun joinCall(group: GroupModel): GroupCallController?

    /**
     * Join a GroupCall. This may be a call that has to be created yet or a call
     * that has been started by someone else.
     *
     * If the call for this group is already joined the method should return the corresponding
     * GroupCallController without executing any further connection steps.
     *
     * When joining or creating a group call a connection to the sfu will be established.
     *
     * If the current user is the creator of the call a GroupCallStart message will be sent to other group
     * members upon call creation on the sfu.
     *
     * Note: There can only be ONE call (be it 1:1 or be it GroupCall) at any time! If there is another
     * ongoing call the other call must be ended.
     *
     * @param group The group in which the call should be joined/started
     *
     * @return The [GroupCallController] of the joined or created call
     */
    @WorkerThread
    suspend fun createCall(group: GroupModel): GroupCallController

    /**
     * This aborts the current call. Since the call might not be ended gracefully (normally the call service
     * will just be stopped), this should only be used in circumstances, when the call cannot be ended properly
     * by calling {@link GroupCallController#leave} e.g. during creation of a call while the connection is not
     * yet established and no controller is available.
     */
    @WorkerThread
    fun abortCurrentCall()

    /**
     * Leave the provided {@link GroupCall}
     */
    @WorkerThread
    fun leaveCall(call: GroupCallDescription): Boolean

    /**
     * @return true if the provided {@link GroupCall} is currently joined
     */
    @AnyThread
    fun isJoinedCall(call: GroupCallDescription): Boolean

    @AnyThread
    fun hasJoinedCall(groupId: LocalGroupId): Boolean

    /**
     * @return true if the user is joining any group call at the moment, false otherwise
     */
    @AnyThread
    fun hasJoinedCall(): Boolean

    /**
     * Get the current chosen call for the supplied group represented by its GroupModel
     * Returns null if no call was found.
     */
    @AnyThread
    fun getCurrentChosenCall(groupModel: GroupModel): GroupCallDescription?

    /**
     * Get the group call controller for the currently joined call.
     * Returns null if no call is joined.
     */
    fun getCurrentGroupCallController(): GroupCallController?

    /**
     * Sends the current group call of the given group to the new members. If there is no call in
     * the group or no new members, nothing is done.
     */
    @AnyThread
    fun sendGroupCallStartToNewMembers(groupModel: GroupModel, newMembers: List<String>)

    @AnyThread
    fun updateAllowedCallParticipants(groupModel: GroupModel)
}
