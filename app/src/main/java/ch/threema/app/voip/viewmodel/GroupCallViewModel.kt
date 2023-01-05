/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.voip.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.lifecycle.*
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.ThreemaApplication.requireServiceManager
import ch.threema.app.services.GroupService
import ch.threema.app.services.NotificationService
import ch.threema.app.utils.AudioDevice
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voip.CallAudioManager
import ch.threema.app.voip.groupcall.*
import ch.threema.app.voip.groupcall.sfu.*
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.GroupModel
import kotlinx.coroutines.*
import org.webrtc.EglBase

private val logger = LoggingUtil.getThreemaLogger("GroupCallViewModel")

@UiThread
class GroupCallViewModel(application: Application) : AndroidViewModel(application), GroupCallObserver {
	enum class ConnectingState {
		IDLE, INITIATED, COMPLETED
	}
	data class FinishEvent(val reason: Reason, val call: GroupCallDescription? = null) {
		enum class Reason {
			LEFT,
			FULL,
			ERROR,
			INVALID_DATA,
			TOKEN_INVALID,
			NO_SUCH_CALL,
			SFU_NOT_AVAILABLE,
			UNSUPPORTED_PROTOCOL_VERSION,
		}
	}

	private val groupService: GroupService by lazy { requireServiceManager().groupService }
	private val groupCallManager: GroupCallManager by lazy { requireServiceManager().groupCallManager }
	private val notificationService: NotificationService by lazy { requireServiceManager().notificationService }

	private val groupId = MutableLiveData<LocalGroupId?>()
	private val startTime = MutableLiveData<Long?>()

	private var joinJob: Job? = null
	private lateinit var callController: GroupCallController

	private lateinit var audioManager: CallAudioManager

	var microphoneActiveDefault: Boolean? = null

	private val audioDevices = MutableLiveData<Set<AudioDevice>>(setOf())
	fun getAudioDevices(): LiveData<Set<AudioDevice>> = audioDevices
	private val selectedAudioDevice = MutableLiveData(AudioDevice.NONE)
	fun getSelectedAudioDevice(): LiveData<AudioDevice> = Transformations
		.distinctUntilChanged(selectedAudioDevice)

	private var connectingState = MutableLiveData(ConnectingState.IDLE)
	fun isConnecting(): LiveData<ConnectingState> = connectingState

	private val callRunning = MutableLiveData(false)
	fun isCallRunning(): LiveData<Boolean> = Transformations.distinctUntilChanged(callRunning)

	private val finishEvents = MutableLiveData<FinishEvent>()
	fun getFinishEvents(): LiveData<FinishEvent> = finishEvents

	private val eglBaseAndParticipants = MutableLiveData<Pair<EglBase, Set<Participant>>>()
	fun getEglBaseAndParticipants(): LiveData<Pair<EglBase, Set<Participant>>> = eglBaseAndParticipants

	private val participantsCount = Transformations.map(eglBaseAndParticipants) { it.second.size }

	private val captureStateUpdates = MutableLiveData<Unit>()
	fun getCaptureStateUpdates(): LiveData<Unit> = captureStateUpdates

	private val microphoneActive = MutableLiveData(false)
	fun isMicrophoneActive(): LiveData<Boolean> = Transformations.distinctUntilChanged(microphoneActive)

	private val cameraActive = MutableLiveData(false)
	fun isCameraActive(): LiveData<Boolean> = Transformations.distinctUntilChanged(cameraActive)

	private val cameraFlipEvents = MutableLiveData<Unit>()
	fun getCameraFlipEvents(): LiveData<Unit> = cameraFlipEvents

	val group = mapGroupModelLiveData()
	val groupAvatar = mapAvatar()
	val statusMessage = mapStatusMessage()
	val title = mapTitle()
	val subTitle = mapSubTitle()
	val startTimeUpdate = mapStartTime()

	var toggleCameraTooltipShown = false

	@UiThread
	override fun onCleared() {
		super.onCleared()
		updateGroupCallObserver(groupId.value, null)
	}

	@UiThread
	private fun updateGroupCallObserver(previousGroupId: LocalGroupId?, newGroupId: LocalGroupId?) {
		if (previousGroupId != null && newGroupId != previousGroupId) {
			groupCallManager.removeGroupCallObserver(previousGroupId, this)
		}

		if (newGroupId != null && previousGroupId != newGroupId) {
			groupCallManager.addGroupCallObserver(newGroupId, this)
		}
	}

	@AnyThread
	override fun onGroupCallUpdate(call: GroupCallDescription?) {
		logger.trace("Group call update")
		startTime.postValue(call?.getRunningSince())
	}

	@AnyThread
	override fun onGroupCallStart(groupModel: GroupModel, call: GroupCallDescription?) {
		logger.trace("Group call start")
	}

	@UiThread
	fun setGroupId(groupId: LocalGroupId) {
		val previousGroupId = this.groupId.value
		this.groupId.value = if (groupId.id > 0) {
			groupId
		} else {
			null
		}
		updateGroupCallObserver(previousGroupId, groupId)
	}

	@UiThread
	fun getGroupId() = groupId.value

	/**
	 * Cancel the notification for this call.
	 * This will only have an effect _after_ the [groupId] has been set.
	 */
	@UiThread
	fun cancelNotification() {
		groupId.value?.let {
			notificationService.cancelGroupCallNotification(it.id)
		}
	}

	@UiThread
	fun leaveCall() {
		if (joinJob?.isCompleted == true) {
			viewModelScope.launch { callController.leave() }
		} else {
			joinJob?.cancel()
			logger.info("Join call aborted")
		}
		finishEvents.postValue(getFinishEvent(FinishEvent.Reason.LEFT))
		callRunning.postValue(false)
	}

	@UiThread
	fun joinCall() {
		groupId.value?.let {
			groupService.getById(it.id)?.let {
				joinJob = CoroutineScope(GroupCallThreadUtil.DISPATCHER).launch {
					try {
						if (!groupCallManager.hasJoinedCall(it.localGroupId)) {
							if (groupCallManager.hasJoinedCall()) {
								val groupCallController = groupCallManager.getCurrentGroupCallController()
								groupCallManager.abortCurrentCall()
								groupCallController?.callDisposedSignal?.await()
							}
							connectingState.postValue(ConnectingState.INITIATED)
						}
						callController = groupCallManager.joinCall(it)
						connectingState.postValue(ConnectingState.COMPLETED)
						audioManager = groupCallManager.getAudioManager()
						callRunning.postValue(true)
						withContext(Dispatchers.Main) {
							initialiseValues()
						}
					} catch (e: CancellationException) {
						logger.warn("Join call has been cancelled")
						// Join aborted, stop group call service
						groupCallManager.abortCurrentCall()
					} catch (e: Exception) {
						logger.error("Error while joining call", e)
						finishEvents.postValue(mapExceptionToFinishEvent(e))
						callRunning.postValue(false)
					}
				}
			}
		}
	}

	@UiThread
	fun selectAudioDevice(device: AudioDevice) {
		audioManager.selectAudioDevice(device)
	}

	@UiThread
	fun muteMicrophone(muted: Boolean) {
		logger.trace("Mute {}", muted)
		callController.microphoneActive = !muted
		microphoneActive.postValue(callController.microphoneActive)
		triggerCaptureStateUpdate()
	}

	@UiThread
	fun muteCamera(muted: Boolean) {
		callController.cameraActive = !muted
		cameraActive.postValue(callController.cameraActive)
		triggerCaptureStateUpdate()
		// If camera is turned on, then don't use earpiece as output anymore as it is not convenient
		if (!muted && selectedAudioDevice.value == AudioDevice.EARPIECE) {
			// Switch to phone speaker (even if headset would be available) as in 1:1 calls
			audioManager.selectAudioDevice(AudioDevice.SPEAKER_PHONE)
		}
	}

	@UiThread
	fun flipCamera() {
		viewModelScope.launch {
			callController.flipCamera()
			cameraFlipEvents.postValue(Unit)
		}
	}

	fun hasOtherJoinedCall(call: GroupCallDescription): Boolean {
		return groupCallManager.hasJoinedCall() && !groupCallManager.isJoinedCall(call)
	}

	@UiThread
	private fun mapGroupModelLiveData(): LiveData<GroupModel?> {
		val distinctGroupId = Transformations.distinctUntilChanged(groupId)
		return Transformations.map(distinctGroupId, this::getGroupModel)
	}

	@UiThread
	private fun mapAvatar(): LiveData<Bitmap?> {
		return group.switchMap {
			liveData(Dispatchers.Default) {
				val avatar = groupService.getAvatar(it, true, false)
				emit(BitmapUtil.blurBitmap(avatar, ThreemaApplication.getAppContext()))
			}
		}
	}

	@UiThread
	private fun initialiseValues() {
		observeCallLeftSignal()
		initMicrophoneState()
		initCameraState()
		observeParticipants()
		observeCaptureStateUpdates()
		observeAudioDevices()
	}

	@UiThread
	private fun observeCallLeftSignal() {
		viewModelScope.launch {
			finishEvents.value = try {
				callController.callLeftSignal.await()
				getFinishEvent(FinishEvent.Reason.LEFT)
			} catch (e: Exception) {
				mapExceptionToFinishEvent(e)
			}
			callRunning.value = false
		}
	}

	private fun mapExceptionToFinishEvent(e: Exception): FinishEvent {
		val statusCode = if (e is SfuException) {
			e.statusCode
		} else {
			null
		}
		return when (statusCode) {
			HTTP_STATUS_DATA_INVALID -> getFinishEvent(FinishEvent.Reason.INVALID_DATA, e)
			HTTP_STATUS_TOKEN_INVALID -> getFinishEvent(FinishEvent.Reason.TOKEN_INVALID, e)
			HTTP_STATUS_NO_SUCH_CALL -> getFinishEvent(FinishEvent.Reason.NO_SUCH_CALL, e)
			HTTP_STATUS_UNSUPPORTED_PROTOCOL_VERSION -> getFinishEvent(FinishEvent.Reason.UNSUPPORTED_PROTOCOL_VERSION, e)
			HTTP_STATUS_SFU_NOT_AVAILABLE -> getFinishEvent(FinishEvent.Reason.SFU_NOT_AVAILABLE, e)
			HTTP_STATUS_CALL_FULL -> getFinishEvent(FinishEvent.Reason.FULL, e)
			else -> getFinishEvent(FinishEvent.Reason.ERROR, e)
		}
	}

	private fun getFinishEvent(reason: FinishEvent.Reason, exception: Exception? = null): FinishEvent {
		val description = if (exception is GroupCallException && exception.callDescription != null) {
			exception.callDescription
		} else if (this::callController.isInitialized) {
			callController.description
		} else {
			null
		}
		return FinishEvent(reason, description)
	}

	@UiThread
	private fun initMicrophoneState() {
		val enableMicrophone = microphoneActiveDefault ?: callController.microphoneActive
		muteMicrophone(!enableMicrophone)
		microphoneActiveDefault = null
	}

	@UiThread
	private fun initCameraState() {
		cameraActive.postValue(callController.cameraActive)
	}

	@UiThread
	private fun observeParticipants() {
		viewModelScope.launch {
			callController.participants
				.collect { eglBaseAndParticipants.value = callController.eglBase to it }
		}
	}

	@UiThread
	private fun observeCaptureStateUpdates() {
		viewModelScope.launch {
			callController.captureStateUpdates
				.collect {
					triggerCaptureStateUpdate()
				}
		}
	}

	@UiThread
	private fun observeAudioDevices() {
		viewModelScope.launch {
			audioManager.observeAvailableAudioDevices()
				.collect {
					audioDevices.value = it
				}
		}
		viewModelScope.launch {
			audioManager.observeSelectedDevice()
				.collect {
					selectedAudioDevice.value = it
				}
		}
	}

	@UiThread
	private fun triggerCaptureStateUpdate() {
		captureStateUpdates.postValue(Unit)
	}


	@AnyThread
	private fun getGroupModel(groupId: LocalGroupId?) = groupId?.let {
		groupService.getById(it.id)
	}

	@UiThread
	private fun mapTitle() = Transformations.map(group, this::getTitle)
	@UiThread
	private fun getTitle(groupModel: GroupModel?) = groupModel?.name ?: ""

	@UiThread
	private fun mapSubTitle() = participantsCount.map { count ->
		when {
			count > 0 -> ConfigUtils.getSafeQuantityString(ThreemaApplication.getAppContext(), R.plurals.n_participants_in_call, count, count)
			else -> null
		}
	}

	@UiThread
	private fun mapStatusMessage(): LiveData<String?> {
		val connectingStateText = MutableLiveData(
			getApplication<ThreemaApplication>().getString(R.string.voip_status_connecting)
		)
		return connectingState.distinctUntilChanged().switchMap {
			when(it) {
				ConnectingState.INITIATED -> connectingStateText
				else -> participantsCount.map { count -> getStatusMessage(count) }
			}
		}
	}

	@UiThread
	private fun getStatusMessage(numberOfParticipants: Int) = when(numberOfParticipants) {
		1 -> getApplication<ThreemaApplication>().getString(R.string.voip_gc_waiting_for_participants)
		else -> null
	}

	@UiThread
	private fun mapStartTime(): LiveData<Long?> {
		val mediator = MediatorLiveData<Long?>()
		mediator.addSource(startTime) {
			if (statusMessage.value != null) {
				mediator.value = null
			} else {
				mediator.value = it
			}
		}
		mediator.addSource(statusMessage) {
			if (it != null) {
				mediator.value = null
			} else {
				mediator.value = startTime.value
			}
		}
		return mediator
	}
}
