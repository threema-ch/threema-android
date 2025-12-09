/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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
import ch.threema.app.voip.groupcall.sfu.CallId
import ch.threema.app.voip.groupcall.sfu.GroupCallController
import ch.threema.base.SessionScoped
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallControlMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.domain.types.Identity
import ch.threema.storage.models.GroupModel
import kotlinx.coroutines.flow.Flow

@JvmInline
value class LocalGroupId(val id: Int)

val GroupModel.localGroupId: LocalGroupId
    get() = LocalGroupId(id)

val ch.threema.data.models.GroupModel.localGroupId: LocalGroupId
    get() = LocalGroupId(getDatabaseId().toInt())

@SessionScoped
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
    fun addGroupCallObserver(group: ch.threema.data.models.GroupModel, observer: GroupCallObserver)

    @AnyThread
    fun removeGroupCallObserver(groupId: LocalGroupId, observer: GroupCallObserver)

    @AnyThread
    fun removeGroupCallObserver(group: GroupModel, observer: GroupCallObserver)

    @AnyThread
    fun removeGroupCallObserver(group: ch.threema.data.models.GroupModel, observer: GroupCallObserver)

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
     * @param localGroupId The group in which the call should be joined
     *
     * @return The [GroupCallController] of the joined call or `null` if no call is running in this group
     */
    @WorkerThread
    suspend fun joinCall(localGroupId: LocalGroupId): GroupCallController?

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

    /**
     * @return true if the call with the passed callId is currently joined
     */
    @AnyThread
    fun isJoinedCall(callId: CallId): Boolean

    @AnyThread
    fun hasJoinedCall(groupId: LocalGroupId): Boolean

    /**
     * @return true if the user is joining any group call at the moment, false otherwise
     */
    @AnyThread
    fun hasJoinedCall(): Boolean

    /**
     * @return The current chosen call for the supplied group represented by its GroupModel or null if no call was found.
     */
    @AnyThread
    fun getCurrentChosenCall(groupModel: GroupModel): GroupCallDescription?

    /**
     * @return The current chosen call for the supplied group represented by its GroupModel or null if no call was found.
     */
    @AnyThread
    fun getCurrentChosenCall(groupModel: ch.threema.data.models.GroupModel): GroupCallDescription?

    /**
     * @return The current chosen call for the supplied group represented by its GroupModel or null if no call was found.
     */
    @AnyThread
    fun getCurrentChosenCall(localGroupId: LocalGroupId): GroupCallDescription?

    /**
     * @return The group call controller for the currently joined call or null if no call is joined.
     */
    fun getCurrentGroupCallController(): GroupCallController?

    /**
     * Get the group call start data for the given group in case there is a group call running.
     */
    @AnyThread
    suspend fun getGroupCallStartData(groupModel: ch.threema.data.models.GroupModel): GroupCallStartData?

    /**
     * Removes the given [identities] from the group call in [groupModel] if there is currently a
     * group call running. Note that the group call is left if [identities] contains the user's
     * identity.
     */
    @AnyThread
    fun removeGroupCallParticipants(
        identities: Set<Identity>,
        groupModel: ch.threema.data.models.GroupModel,
    )

    /**
     *  Creates a *cold* [Flow] that provides every change to the currently running calls
     */
    fun watchRunningCalls(): Flow<Map<CallId, GroupCallDescription>>
}
