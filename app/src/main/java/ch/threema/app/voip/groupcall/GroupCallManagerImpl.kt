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

package ch.threema.app.voip.groupcall

import android.content.Context
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import ch.threema.app.BuildConfig
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.*
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voip.CallAudioManager
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.app.voip.groupcall.service.GroupCallService
import ch.threema.app.voip.groupcall.service.GroupCallServiceConnection
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.base.utils.Base64
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.Utils
import ch.threema.domain.protocol.ThreemaFeature
import ch.threema.domain.protocol.api.SfuToken
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallControlMessage
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartData
import ch.threema.domain.protocol.csp.messages.groupcall.GroupCallStartMessage
import ch.threema.storage.DatabaseServiceNew
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.data.status.GroupCallStatusDataModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.*
import kotlin.math.max

private val logger = LoggingUtil.getThreemaLogger("GroupCallManagerImpl")

@WorkerThread
class GroupCallManagerImpl(
	private val context: Context,
	private val databaseService: DatabaseServiceNew,
	private val groupService: GroupService,
	private val contactService: ContactService,
	private val preferenceService: PreferenceService,
	private val messageService: MessageService,
	private val groupMessagingService: GroupMessagingService,
	private val notificationService: NotificationService,
	private val sfuConnection: SfuConnection
) : GroupCallManager {
	private val callObservers: MutableMap<LocalGroupId, MutableSet<GroupCallObserver>> = mutableMapOf()
	private val callRefreshTimers: MutableMap<LocalGroupId, Job> = Collections.synchronizedMap(mutableMapOf())

	// TODO(ANDR-1957): Unsure if this is guarded properly for use outside of the GC thread. There
	//  is synchronization but it's not used consistently.
	private val peekFailedCounters: PeekFailedCounter = PeekFailedCounter()
	private val runningCalls: MutableMap<CallId, GroupCallDescription>
	private val chosenCalls: MutableMap<LocalGroupId, GroupCallDescription> = mutableMapOf()

	private var serviceConnection = GroupCallServiceConnection()

	/*
	Since the GroupCallManager is a dependency of the MessageProcessor which will be started upon
	app start, this will trigger the group call refresh steps for all considered running calls on
	app start.
	 */
	init {
		runningCalls = loadPersistedRunningCalls()
		runningCalls.values
			.map { it.groupId }
			.forEach { triggerGroupCallRefreshSteps(it) }
	}

	// TODO(ANDR-1959): Test
	@WorkerThread
	override fun handleControlMessage(message: GroupCallControlMessage): Boolean {
		logger.debug("Handle control message")

		return when (message) {
			is GroupCallStartMessage -> handleGroupCallStart(message)
			else -> handleUnknownMessageType(message)
		}
	}

	@WorkerThread
	override suspend fun getAudioManager(): CallAudioManager {
		GroupCallThreadUtil.assertDispatcherThread()

		return serviceConnection.getCallAudioManager()
	}

	@UiThread
	override fun addGroupCallObserver(group: GroupModel, observer: GroupCallObserver) {
		addGroupCallObserver(group.localGroupId, observer)
	}

	@UiThread
	override fun addGroupCallObserver(groupId: LocalGroupId, observer: GroupCallObserver) {
		synchronized(callObservers) {
			if (groupId !in callObservers) {
				callObservers[groupId] = Collections.synchronizedSet(mutableSetOf())
			}
		}
		if (callObservers[groupId]?.add(observer) == true) {
			observer.onGroupCallUpdate(chosenCalls[groupId])
		}
	}

	@UiThread
	override fun removeGroupCallObserver(group: GroupModel, observer: GroupCallObserver) {
		removeGroupCallObserver(group.localGroupId, observer)
	}

	@UiThread
	override fun removeGroupCallObserver(groupId: LocalGroupId, observer: GroupCallObserver) {
		synchronized(callObservers) {
			callObservers[groupId]?.remove(observer)
		}
	}

	@WorkerThread
	override suspend fun joinCall(group: GroupModel): GroupCallController {
		GroupCallThreadUtil.assertDispatcherThread()

		val groupId = group.localGroupId
		logger.debug("Join call for group {}", groupId)

		val controller = getGroupCallControllerForJoinedCall(groupId)
		return if (controller != null) {
			controller
		} else {
			val chosenCall = getChosenCall(groupId)
			if (chosenCall == null) {
				// there is no group call considered running for this group. Start it!
				logger.debug("Create new group call")
				createCall(group)
			} else {
				// join `callId` and return controls
				logger.debug("Join existing group call")
				joinCall(chosenCall).also {
					// wait until the call is 'CONNECTED'
					it.connectedSignal.await()
					// always confirm the call when joining and not creating a call
					it.confirmCall()
					// If two participants almost simultaneously start a group call, only one group
					// call will be created. The other participant will join the chosen call while
					// aborting the creation of its own group call. This can lead to a race where
					// the group call start notification is shown even though the join process has
					// already started. For this case, we cancel the notification here.
					notificationService.cancelGroupCallNotification(groupId.id)
				}
			}
		}
	}

	@WorkerThread
	override fun abortCurrentCall() {
		logger.warn("Aborting current call")
		val controller = serviceConnection.getCurrentGroupCallController()
		if (controller != null) {
			controller.leave()
		} else {
			context.stopService(GroupCallService.getStopIntent(context))
		}
	}

	@WorkerThread
	override fun leaveCall(call: GroupCallDescription): Boolean {
		serviceConnection.getCurrentGroupCallController()?.let {
			if (it.callId == call.callId) {
				it.leave()
				return true
			}
		}
		return false
	}

	@AnyThread
	override fun isJoinedCall(call: GroupCallDescription): Boolean {
		return serviceConnection.getCurrentGroupCallController()?.callId == call.callId
	}

	@AnyThread
	override fun hasJoinedCall(groupId: LocalGroupId): Boolean {
		return serviceConnection.getCurrentGroupCallController()?.description?.groupId == groupId
	}

	@AnyThread
	override fun hasJoinedCall(): Boolean {
		return serviceConnection.getCurrentGroupCallController()?.callId != null
	}

	@AnyThread
	override fun getCurrentChosenCall(groupModel: GroupModel): GroupCallDescription? {
		return chosenCalls[groupModel.localGroupId]
	}

	override fun getCurrentGroupCallController(): GroupCallController? {
		return serviceConnection.getCurrentGroupCallController()
	}

	@AnyThread
	override fun sendGroupCallStartToNewMembers(groupModel: GroupModel, newMembers: List<String>) {
		if (newMembers.isEmpty()) {
			return
		}
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			val groupCallDescription = getChosenCall(groupModel.localGroupId) ?: return@launch
			val sfuToken = obtainToken()
			sendGroupCallStartMessage(groupModel, GroupCallStartData(
					ProtocolDefines.GC_PROTOCOL_VERSION.toUInt(),
					groupCallDescription.gck,
					sfuToken.sfuBaseUrl
			), groupCallDescription.getStartedAtDate(), newMembers.toTypedArray())
		}
	}

	@WorkerThread
	private suspend fun joinCall(callDescription: GroupCallDescription): GroupCallController {
		GroupCallThreadUtil.assertDispatcherThread()

		val intent = GroupCallService.getStartIntent(context, callDescription.sfuBaseUrl, callDescription.callId, callDescription.groupId)
		logger.debug("Start Group Call Foreground Service")
		ContextCompat.startForegroundService(context, intent)
		context.bindService(intent, serviceConnection, 0)
		val controller = serviceConnection.getGroupCallController()
		logger.trace("Got controller")
		controller.description = callDescription
		attachCallDisposedHandler(controller)
		return controller
	}

	@WorkerThread
	private fun attachCallDisposedHandler(controller: GroupCallController) {
		GroupCallThreadUtil.assertDispatcherThread()

		val callId = controller.callId
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			try {
				controller.callDisposedSignal.await()
				logger.info("Call has been disposed")
			} catch (e: Exception) {
				logger.error("Call ended exceptionally", e)
			}
			// Reset service connection
			serviceConnection = GroupCallServiceConnection()
			runningCalls[callId]?.let { triggerGroupCallRefreshSteps(it.groupId) }
		}
	}

	@WorkerThread
	private suspend fun leaveCall(callId: CallId) {
		GroupCallThreadUtil.assertDispatcherThread()

		serviceConnection.getCurrentGroupCallController()?.also {
			if (it.callId == callId) {
				logger.debug("Leaving call {}", callId)
				it.leave()
				it.callDisposedSignal.await()
			} else {
				logger.info("Attempt to leave call that is not joined")
			}
		} ?: run {
			logger.info("Trying to leave call, but no call is joined")
		}
	}

	@WorkerThread
	@Throws(GroupCallException::class)
	private suspend fun createCall(group: GroupModel): GroupCallController {
		GroupCallThreadUtil.assertDispatcherThread()

		val callStartData = createGroupCallStartData()
		val callId = CallId.create(group, callStartData)
		logger.debug("Created call id: {}", callId)

		val groupId = group.localGroupId
		val callDescription = GroupCallDescription(
			callStartData.protocolVersion,
			groupId,
			callStartData.sfuBaseUrl,
			callId,
			callStartData.gck,
			Date().time.toULong()
		)
		val callController = joinCall(callDescription)

		logger.debug("Got controller")

		// TODO(ANDR-1963): abort if a new call has been started while we are waiting for the sfu connection and the 'Hello'-Message
		val (startedAt, participantIds) = callController.connectedSignal.await()
		callDescription.startedAt = startedAt

		logger.debug("Got {} participants", participantIds.size)

		if (participantIds.isNotEmpty()) {
			logger.trace("Participants: {}", participantIds)
			callController.declineCall()
			throw GroupCallException("Invalid participants list for creation of group call (must be empty)")
		} else {
			callController.confirmCall()
		}

		// Make function cancellable. This enables cancelling call creation before GroupCallStart
		// is sent to other group members.
		// This is useful when someone accidentally pressed the call button and immediately hangs up
		// in the GroupCallActivity.
		yield()

		val chosenCall = getCurrentChosenCall(group)

		if (chosenCall != null && chosenCall.callId != callId) {
			callController.leave()
			callController.callDisposedSignal.await()
			logger.warn("There is already another chosen call for group ${group.localGroupId}")
			return joinCall(group)
		}

		sendGroupCallStartMessage(group, callStartData, callDescription.getStartedAtDate(), null)
		sendCallInitAsText(callId, group, callStartData)

		addRunningCall(callDescription)
		triggerGroupCallRefreshSteps(callDescription.groupId)

		createStartedStatusMessage(
				callDescription,
				group,
				contactService.me,
				true,
				callDescription.getStartedAtDate()
		)

		return callController
	}

	/**
	 * Get the {@link GroupCallController} for a joined call for a certain group.
	 *
	 * If there is no joined call, or the joined call does not match the given group, no controller
	 * will be returned.
	 *
	 * @param groupId The {@link LocalGroupId} of the group of which the controller should be obtained.
	 *
	 * @return The {@link GroupCallController} if there is a joined call for this group, {@code null} otherwise
	 */
	@WorkerThread
	private fun getGroupCallControllerForJoinedCall(groupId: LocalGroupId): GroupCallController? {
		GroupCallThreadUtil.assertDispatcherThread()

		return serviceConnection.getCurrentGroupCallController()?.let {
			if (chosenCalls[groupId]?.callId == it.callId) {
				it
			} else {
				null
			}
		}
	}

	@WorkerThread
	private fun handleGroupCallStart(message: GroupCallStartMessage): Boolean {
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			processGroupCallStart(message)
		}
		return true
	}

	@WorkerThread
	private suspend fun processGroupCallStart(message: GroupCallStartMessage) {
		GroupCallThreadUtil.assertDispatcherThread()

		val group = groupService.getGroup(message)
		val groupId = group.localGroupId
		logger.debug("Process group call start for group {}: {}", groupId, message.data)

		if (isInvalidSfuBaseUrl(message.data.sfuBaseUrl)) {
			logger.warn("Invalid sfu base url: {}", message.data.sfuBaseUrl)
			return
		}

		if (hasDuplicateGck(groupId, message.data.gck)) {
			logger.warn("Duplicate gck received. Abort handling of group call start")
			return
		}

		val callId = CallId.create(group, message.data)
		val call = GroupCallDescription(
			message.data.protocolVersion,
			groupId,
			message.data.sfuBaseUrl,
			callId,
			message.data.gck,
			Date().time.toULong()
		)

		addRunningCall(call)

		val callerContactModel = contactService.getByIdentity(message.fromIdentity)
		if (callerContactModel != null) {
			val isOutbox = message.fromIdentity.equals(contactService.me.identity)
			createStartedStatusMessage(
					call,
					group,
					callerContactModel,
					isOutbox,
					message.date)
		}

		val chosenCall = runGroupCallRefreshSteps(groupId)

		if (chosenCall != null) {
			if (ConfigUtils.isGroupCallsEnabled() && !consolidateJoinedCall(chosenCall, groupId)) {
				if (callerContactModel != null) {
					// Only needed if the call is not yet joined
					if (!isJoinedCall(chosenCall)) {
						logger.debug("Show group call notification")
						notificationService.addGroupCallNotification(group, callerContactModel)
						synchronized(callObservers) {
							callObservers[groupId]?.forEach { it.onGroupCallStart(group, call) }
						}
					} else {
						logger.debug("Call already joined")
					}
				} else {
					logger.debug("Caller could not be determined")
				}
			} else if (!ConfigUtils.isGroupCallsEnabled()) {
				logger.info("Group call is running but disabled. Not showing notification.")
			}
		} else {
			logger.info("Group call seems not to be running any more. Not showing notification.")
		}
	}

	/**
	 * Creates a status message for a started call
	 */
	private fun createStartedStatusMessage(callDescription: GroupCallDescription,
										   group: GroupModel,
										   caller: ContactModel,
										   isOutbox: Boolean,
										   startedAt: Date) {
		messageService.createGroupCallStatus(
				GroupCallStatusDataModel.createStarted(
						callDescription.callId.toString(),
						callDescription.groupId.id,
						caller.identity),
				groupService.createReceiver(group),
				caller,
				callDescription,
				isOutbox,
				startedAt
		)
	}

	/**
	 * Makes sure that - if a call of this group is joined - the chosen call is joined.
	 *
	 * If the user is currently in a group call of this group which is not the chosen call the call is
	 * exited and the chosen call is joined.
	 *
	 * @return true, if the call has been joined, false otherwise
	 */
	@WorkerThread
	private suspend fun consolidateJoinedCall(chosenCall: GroupCallDescription, groupId: LocalGroupId): Boolean {
		GroupCallThreadUtil.assertDispatcherThread()

		val joinedCall = getJoinedCall()

		// TODO(ANDR-1959): This should be tested thoroughly. There could easily be timing problems
		//  because the call is left and immediately a new call is joined.
		//  There might be problems when the foreground service is already running / bound etc.
		return if (joinedCall != null && isOfGroupButNotChosenCall(groupId, joinedCall, chosenCall.callId)) {
			logger.info("Leave joined call because it is not the chosen call for group {}. Join the chosen call instead.", groupId)

			val groupController = serviceConnection.getCurrentGroupCallController()

			val microphoneActive = groupController?.microphoneActive ?: true

			leaveCall(joinedCall)

			groupService.getById(groupId.id)?.let {
				val newGroupController = joinCall(it)
				newGroupController.microphoneActive = microphoneActive
			}

			context.startActivity(
				GroupCallActivity.getStartOrJoinCallIntent(
					context,
					groupId.id,
					microphoneActive,
				)
			)
			true
		} else {
			false
		}
	}

	@WorkerThread
	private fun isOfGroupButNotChosenCall(groupId: LocalGroupId, callId: CallId, chosenCall: CallId): Boolean {
		GroupCallThreadUtil.assertDispatcherThread()

		val isOfGroup = callId in getRunningCalls(groupId).map { it.callId }
		return isOfGroup && callId != chosenCall
	}

	@WorkerThread
	private suspend fun isInvalidSfuBaseUrl(baseUrl: String): Boolean {
		GroupCallThreadUtil.assertDispatcherThread()

		return !obtainToken().isAllowedBaseUrl(baseUrl)
	}

	@WorkerThread
	private fun hasDuplicateGck(groupId: LocalGroupId, gck: ByteArray) = getRunningCalls(groupId)
			.any { it.gck.contentEquals(gck) }

	@WorkerThread
	private fun handleUnknownMessageType(message: GroupCallControlMessage): Boolean {
		val type = when (message) {
			is AbstractMessage -> "0x%x".format(message.type)
			else -> "???"
		}
		logger.info("Unknown group call control message type {}", type)
		return true
	}

	@WorkerThread
	private suspend fun createGroupCallStartData(): GroupCallStartData {
		GroupCallThreadUtil.assertDispatcherThread()

		val sfuToken = obtainToken()
		return GroupCallStartData(
			ProtocolDefines.GC_PROTOCOL_VERSION.toUInt(),
			createGck(),
			sfuToken.sfuBaseUrl
		)
	}

	@WorkerThread
	private fun createGck(): ByteArray {
		val random = SecureRandom()
		val gck = ByteArray(ProtocolDefines.GC_GCK_LENGTH)
		random.nextBytes(gck)
		return gck
	}

	/**
	 * Create the GroupCallStartMessage and send it to the given group members. Note that the
	 * GroupCallStartMessage is never sent to group members where the group call feature mask is not
	 * set. This method assumes that the feature masks are updated and therefore does not fetch the
	 * newest feature masks from the server.
	 *
	 * The same create date is used for all messages. If a call is already running, then the call
	 * duration is subtracted from current time to match the original created at from the first
	 * group call start message.
	 *
	 * @param group the group model where the group call message is sent to
	 * @param data the group call start data
	 * @param sendTo the list of identities who receive the message; if null it is sent to all group members
	 */
	@WorkerThread
	private fun sendGroupCallStartMessage(group: GroupModel, data: GroupCallStartData, startedAt: Date, sendTo: Array<String>?) {
		GroupCallThreadUtil.assertDispatcherThread()

		val identities = (sendTo ?: groupService.getGroupIdentities(group))
			.filter { identity -> contactService.getByIdentity(identity)?.let { ThreemaFeature.canGroupCalls(it.featureMask) } ?: false }
			.toTypedArray()

		// TODO(ANDR-1766): Created at should most likely be injected in GroupMessagingService#sendMessage
		val createdAt = startedAt
		val count = groupMessagingService.sendMessage(group, identities) {
			GroupCallStartMessage(data).apply {
				date = createdAt
				messageId = it }
		}
		logger.trace("{} group call start messages sent", count)
	}

	@WorkerThread
	private fun sendCallInitAsText(callId: CallId, group: GroupModel, callStartData: GroupCallStartData) {
		if (BuildConfig.DEBUG && preferenceService.isGroupCallSendInitEnabled) {
			val groupJson = JSONObject()
			groupJson.put("creator", group.creatorIdentity)
			groupJson.put("id", Base64.encodeBytes(group.apiGroupId.groupId))

			val membersJson = JSONArray()
			groupService.getGroupIdentities(group)
				.mapNotNull { contactService.getByIdentity(it) }
				.forEach {
					val member = JSONObject()
					member.put("identity", it.identity)
					member.put("publicKey", Base64.encodeBytes(it.publicKey))
					membersJson.put(member)
				}

			val json = JSONObject()
			json.put("type", "group-call")
			json.put("protocolVersion", callStartData.protocolVersion.toInt())
			json.put("group", groupJson)
			json.put("members", membersJson)
			json.put("gck", Base64.encodeBytes(callStartData.gck))
			json.put("sfuBaseUrl", callStartData.sfuBaseUrl)
			val callIdText = Utils.byteArrayToHexString(callId.bytes)
			val jsonText = json.toString(0)
			val callInit = Base64.encodeBytes(jsonText.encodeToByteArray())
			val message = "*CallId:*\n$callIdText\n\n*CallData:*\n$callInit"

			val receiver = groupService.createReceiver(group)
			ThreemaApplication.requireServiceManager().messageService
				.sendText(message, receiver)
		}
	}

	/**
	 * Run the group call refresh steps and return the chosen call if present.
	 */
	private suspend fun runGroupCallRefreshSteps(groupId: LocalGroupId): GroupCallDescription? {
		// Corresponds to the Group Call Refresh Steps 1-3 and 5
		val chosenCall = getChosenCall(groupId)

		// Step 4: reschedule group call refresh steps if there is a running call
		scheduleGroupCallRefreshSteps(groupId)

		// Step 6: abort the steps (update the UI)
		if (chosenCall == null) {
			chosenCalls.remove(groupId)
			synchronized(callObservers) {
				callObservers[groupId]?.forEach { it.onGroupCallUpdate(null) }
			}
			return null
		}

		// Step 7: display chosen-call as the currently running group call within the group
		updateChosenCallsAndNotifyCallObservers(groupId, chosenCall.callId)

		// Step 8 is handled by getting the chosen call before sending a GroupCallStart and therefore
		// this step is not part in the group call refresh steps implementation here

		// Step 9
		consolidateJoinedCall(chosenCall, groupId)

		// Step 10: return chosen-call
		return chosenCall
	}

	/**
	 * Perform steps 1-3 and 5 of the group call refresh steps and return the chosen call if present.
	 *
	 * If no group call is considered running for this group, null is returned.
	 *
	 *  TODO(ANDR-1959): Tests for group call refresh steps
	 */
	@WorkerThread
	private suspend fun getChosenCall(groupId: LocalGroupId): GroupCallDescription? {
		GroupCallThreadUtil.assertDispatcherThread()

		logger.debug("Get chosen call for group {}", groupId)
		logger.trace("runningCalls={}", runningCalls.values)
		return getConsideredRunningCalls(groupId)
			.toSet()
			.maxByOrNull { it.startedAt }
	}

	/**
	 * Initially trigger the group call refresh steps for a group.
	 *
	 * The steps will be run as soon as the executor is ready.
	 *
	 * If needed the group call refresh steps will be re-scheduled after execution.
	 */
	@WorkerThread
	private fun triggerGroupCallRefreshSteps(groupId: LocalGroupId) {
		logger.debug("Trigger group call refresh steps")
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			runGroupCallRefreshSteps(groupId)?.let {
				logger.debug("Chosen call: {}", it)
				consolidateJoinedCall(it, groupId)
			}
		}
	}

	@WorkerThread
	private fun scheduleGroupCallRefreshSteps(groupId: LocalGroupId) {
		// This is executed asynchronously in order to let the previous
		// group call refresh finish before the next attempt to re-schedule is started.
		// When rescheduling, a refresh is only scheduled if the previous refresh has finished.
		CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
			val hasRunningCall = getRunningCalls(groupId).isNotEmpty()
			val hasScheduledRefresh = callRefreshTimers[groupId]?.isCompleted == false
			if (hasRunningCall && !hasScheduledRefresh) {
				logger.trace("Schedule group call refresh steps for group {}", groupId)
				synchronized(callRefreshTimers) {
					callRefreshTimers[groupId] =
						CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
							delay(ProtocolDefines.GC_GROUP_CALL_REFRESH_STEPS_TIMEOUT_SECONDS * 1_000)
							runGroupCallRefreshSteps(groupId)
						}
				}
			} else if (!hasRunningCall) {
				logger.trace("Group {} has no running call. Do not schedule group call refresh steps")
			} else {
				logger.trace("Group call refresh steps already scheduled for group {}", groupId)
			}
		}
	}

	@WorkerThread
	private fun getJoinedCall(): CallId? {
		return serviceConnection.getCurrentGroupCallController()?.callId
	}

	private fun updateChosenCallsAndNotifyCallObservers(groupId: LocalGroupId, callId: CallId) {
		val call = runningCalls[callId]
		if (call == null) {
			chosenCalls.remove(groupId)
		} else {
			chosenCalls[groupId] = call
		}
		synchronized(callObservers) {
			callObservers[groupId]?.forEach { it.onGroupCallUpdate(call) }
		}
		val groupModel = groupService.getById(groupId.id)
		if (call == null && groupModel != null) {
			messageService.createGroupCallStatus(
				GroupCallStatusDataModel.createEnded(callId.toString()),
				groupService.createReceiver(groupModel),
				null,
				call,
				false,
				Date()
			)
		}
	}

	/**
	 * Get the calls that are considered running.
	 *
	 * For every call that is considered running the sfu is peeked, to update the current call state.
	 *
	 * If the call is not known to the sfu it is removed from the list of running calls for this group.
	 *
	 * If there are any pending refresh timers for a removed call, those are also removed.
	 */
	@WorkerThread
	private fun getConsideredRunningCalls(groupId: LocalGroupId): Flow<GroupCallDescription> {
		GroupCallThreadUtil.assertDispatcherThread()

		val consideredRunningCalls = getRunningCalls(groupId)
		return peekCalls(consideredRunningCalls)
			.onEach { purgeRunningCalls(groupId, it) }
			.filter { isMatchingProtocolVersion(it.call) && (it.isJoined || it.isHttpOk)  }
			.onEach { it.sfuResponse?.body?.let { r -> updateCallState(it.call.callId, r) }}
			.map { it.call }
			.also { purgeCallRefreshTimers(groupId) }
	}

	private fun isMatchingProtocolVersion(call: GroupCallDescription): Boolean {
		logger.debug("Call protocol version: local={}, call={}", ProtocolDefines.GC_PROTOCOL_VERSION, call.protocolVersion)
		return if (call.protocolVersion == ProtocolDefines.GC_PROTOCOL_VERSION.toUInt()) {
			true
		} else {
			logger.warn("Local gc protocol version is {} but running call has version {}", ProtocolDefines.GC_PROTOCOL_VERSION, call.protocolVersion)
			false
		}
	}

	@WorkerThread
	private fun purgeRunningCalls(groupId: LocalGroupId, peek: PeekResult) {
		GroupCallThreadUtil.assertDispatcherThread()

		if (!peek.isJoined && !peek.isPeekFailed && (peek.isHttpNotFound || isAbandonedCall(peek))) {
			val callId = peek.call.callId
			logger.debug("Remove call {}", callId)
			removeRunningCall(callId)
			notificationService.cancelGroupCallNotification(groupId.id)
			updateChosenCallsAndNotifyCallObservers(groupId, callId)
		}
	}

	@WorkerThread
	private fun isAbandonedCall(peek: PeekResult): Boolean {
		val failedCounter = if (!peek.isJoined && !peek.isHttpOk) {
			peekFailedCounters.getAndIncrementCounter(peek.call.callId)
		} else {
			peekFailedCounters.resetCounter(peek.call.callId)
		}
		return if (failedCounter >= ProtocolDefines.GC_PEEK_FAILED_ABANDON_MIN_TRIES) {
			// Note: we cannot use peek.call.getRunningSince() because we need an actual timestamp
			// not relative to the system uptime.
			val runningSince = max(Date().time - peek.call.startedAt.toLong(), 0L)
			runningSince >= ProtocolDefines.GC_PEEK_FAILED_ABANDON_MIN_CALL_AGE_MILLIS
		} else {
			false
		}
	}

	@WorkerThread
	private fun purgeCallRefreshTimers(groupId: LocalGroupId) {
		if (getRunningCalls(groupId).isEmpty()) {
			synchronized(callRefreshTimers) {
				callRefreshTimers.remove(groupId)?.cancel("purgeCallRefreshTimers")
			}
		}
	}

	@WorkerThread
	private fun peekCalls(calls: Collection<GroupCallDescription>): Flow<PeekResult> {
		GroupCallThreadUtil.assertDispatcherThread()

		logger.trace("Peek calls {}", calls.map { it.callId })
		return calls.asFlow()
			.map(this::peekCall)
			.flowOn(GroupCallThreadUtil.DISPATCHER)
	}

	@WorkerThread
	private suspend fun peekCall(call: GroupCallDescription): PeekResult {
		GroupCallThreadUtil.assertDispatcherThread()

		val isJoined = isJoinedCall(call)
		return try {
			PeekResult(
				call,
				peekCall(call, 1, false),
				isJoined = isJoined
			).also {
				logger.debug("Got peek result: {}", it.sfuResponse?.body)
			}
		} catch (e: SfuException) {
			logger.error("Could not peek call", e)
			PeekResult(
				call,
				sfuResponse = null,
				isPeekFailed = true,
				isJoined = isJoined
			)
		}
	}

	@WorkerThread
	private suspend fun peekCall(call: GroupCallDescription, retriesOnInvalidToken: Int, forceTokenRefresh: Boolean): PeekResponse {
		GroupCallThreadUtil.assertDispatcherThread()

		val token = obtainToken(forceTokenRefresh)
		val peekResponse = sfuConnection.peek(token, call.sfuBaseUrl, call.callId)
		return if (peekResponse.statusCode == HTTP_STATUS_TOKEN_INVALID && retriesOnInvalidToken > 0) {
			logger.info("Retry peeking with refreshed token")
			peekCall(call, retriesOnInvalidToken - 1, true)
		} else {
			peekResponse
		}
	}

	@WorkerThread
	private suspend fun obtainToken(forceTokenRefresh: Boolean = false): SfuToken {
		// TODO(ANDR-2090): Use a timeout according to protocol
		return sfuConnection.obtainSfuToken(forceTokenRefresh)
	}

	/**
	 * Get all calls for the specified group, that are currently considered running.
	 *
	 * If there are multiple calls considered running for this group only the call with the highest
	 * create timestamp is included. Calls with lower timestamps are removed from known calls since those are no longer considered running.
	 *
	 * In principle there might be multiple calls that were created at exactly the same time. Therefore
	 * a collection of calls is returned.
	 */
	@WorkerThread
	private fun getRunningCalls(groupId: LocalGroupId): Set<GroupCallDescription> {
		return synchronized(runningCalls) {
			runningCalls.values
				.filter { it.groupId == groupId }
				.toSet()
		}
	}

	/**
	 * Add the call to the list of considered running calls
	 */
	@WorkerThread
	private fun addRunningCall(call: GroupCallDescription) {
		GroupCallThreadUtil.assertDispatcherThread()

		logger.debug("Add running call {}", call)
		synchronized(runningCalls) {
			runningCalls[call.callId] = call
			persistRunningCall(call)
		}
	}

	@WorkerThread
	private fun removeRunningCall(callId: CallId): GroupCallDescription? {
		GroupCallThreadUtil.assertDispatcherThread()

		return synchronized(runningCalls) {
			val removedCall = runningCalls.remove(callId).also {
				logger.debug("call removed: {}, id={}", it != null, callId)
			}
			removedCall?.let { removePersistedRunningCall(it) }
			removedCall
		}
	}

	@WorkerThread
	private fun persistRunningCall(call: GroupCallDescription) {
		databaseService.groupCallModelFactory.createOrUpdate(call.toGroupCallModel())
	}

	@WorkerThread
	private fun removePersistedRunningCall(call: GroupCallDescription) {
		databaseService.groupCallModelFactory.delete(call.toGroupCallModel())
	}

	@WorkerThread
	private fun loadPersistedRunningCalls(): MutableMap<CallId, GroupCallDescription> {
		return databaseService.groupCallModelFactory.all
			.map { it.toGroupCallDescription() }
			.associateBy { it.callId }
			.toMutableMap()
	}

	@WorkerThread
	private fun updateCallState(callId: CallId, peekResponse: PeekResponseBody) {
		GroupCallThreadUtil.assertDispatcherThread()

		synchronized(runningCalls) {
			runningCalls[callId]?.let {
				it.maxParticipants = peekResponse.maxParticipants
				it.startedAt = peekResponse.startedAt
				it.setEncryptedCallState(peekResponse.encryptedCallState)
				logger.debug("Update call state {}", it)
			}
		}
	}
}

private data class PeekResult (
	val call: GroupCallDescription,
	val sfuResponse: PeekResponse?,
	val isJoined: Boolean = false,
	val isPeekFailed: Boolean = false
) {
	val isHttpOk: Boolean
		get() = !isPeekFailed && sfuResponse?.isHttpOk == true

	val isHttpNotFound: Boolean
		get() = !isPeekFailed && sfuResponse?.isHttpNotFound == true
}
