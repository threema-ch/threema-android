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

package ch.threema.app.voip.activities

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.*
import android.view.GestureDetector.SimpleOnGestureListener
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.transition.addListener
import androidx.core.view.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.adapters.GroupCallParticipantsAdapter
import ch.threema.app.adapters.decorators.VerticalGridLayoutGutterDecoration
import ch.threema.app.dialogs.BottomSheetListDialog
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.dialogs.ThreemaDialogFragment
import ch.threema.app.emojis.EmojiTextView
import ch.threema.app.listeners.SensorListener
import ch.threema.app.services.GroupService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.SensorService
import ch.threema.app.ui.AnimatedEllipsisTextView
import ch.threema.app.ui.BottomSheetItem
import ch.threema.app.ui.TooltipPopup
import ch.threema.app.utils.*
import ch.threema.app.voip.CallAudioSelectorButton
import ch.threema.app.voip.groupcall.GroupCallDescription
import ch.threema.app.voip.groupcall.LocalGroupId
import ch.threema.app.voip.util.VoipUtil
import ch.threema.app.voip.viewmodel.GroupCallViewModel
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.GroupModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.concurrent.schedule

private val logger = LoggingUtil.getThreemaLogger("GroupCallActivity")!!

@UiThread
class GroupCallActivity : ThreemaActivity(), GenericAlertDialog.DialogClickListener,
	SensorListener {

	companion object {
		private const val EXTRA_GROUP_ID = "EXTRA_GROUP_ID"
		private const val EXTRA_MICROPHONE_ACTIVE = "EXTRA_MICROPHONE_ACTIVE"

		private const val DURATION_ANIMATE_NAVIGATION_MILLIS = 300L
		private const val DURATION_ANIMATE_GRADIENT_VISIBILITY_MILLIS = 200L
		private const val TIMEOUT_HIDE_NAVIGATION_MILLIS = 7000L

		private const val DIALOG_TAG_MIC_PERMISSION_DENIED = "mic_perm_denied"
		private const val DIALOG_TAG_CAMERA_PERMISSION_DENIED = "cam_perm_denied"
		private const val DIALOG_TAG_SELECT_AUDIO_DEVICE = "audio_select_tag"

		private const val SENSOR_TAG_GROUP_CALL = "grpcall"
		private const val KEEP_ALIVE_DELAY = 20000L

		@JvmStatic
		fun getStartOrJoinCallIntent(context: Context, groupId: Int): Intent {
			return getGroupCallIntent(context, groupId)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}

		@JvmStatic
		fun getStartOrJoinCallIntent(context: Context, groupId: Int, microphoneActive: Boolean = true): Intent {
			return getStartOrJoinCallIntent(context, groupId)
				.putExtra(EXTRA_MICROPHONE_ACTIVE, microphoneActive)
		}

		private fun getGroupCallIntent(context: Context, groupId: Int): Intent {
			return Intent(context, GroupCallActivity::class.java)
				.putExtra(EXTRA_GROUP_ID, groupId)
		}
	}

	private val audioSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
	) {
		if (ConfigUtils.isPermissionGranted(this, RECORD_AUDIO)) {
			checkPhoneStateAndJoinCall()
		} else {
			setResult(RESULT_CANCELED)
			finish()
		}
	}

	private val cameraSettingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()
	) {
		if (ConfigUtils.isPermissionGranted(this, CAMERA)) {
			checkCameraPermissionAndStartCapturing()
		}
	}

	private val readPhoneStateSettingsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		if (!it && !ActivityCompat.shouldShowRequestPermissionRationale(this, READ_PHONE_STATE)) {
			ConfigUtils.showPermissionRationale(this, findViewById(R.id.group_call_layout), R.string.read_phone_state_short_message)
			// Finish activity when snack bar disappears (after 2750ms)
			Timer().schedule(2750) { finish() }
		} else {
			joinCall()
		}
	}

	// An actual runnable is used so it can be removed from the message queue if needed
	private val autoRemoveInfoAndControlsRunnable = Runnable {
		hideInfoAndControls()
	}

	private val viewModel: GroupCallViewModel by viewModels()

	private lateinit var lockAppService: LockAppService
	private lateinit var preferenceService: PreferenceService
	private lateinit var sensorService: SensorService
	private lateinit var groupService: GroupService

	private lateinit var permissionRegistry: PermissionRegistry

	private lateinit var views: Views

	private var infoAndControlsShown = true
	private var infoAndControlsShownManually = false
	private var sensorEnabled = false
	private var newIntent: Intent? = null

	private lateinit var participantsAdapter: GroupCallParticipantsAdapter
	private lateinit var gestureDetector: GestureDetectorCompat

	private val participantsLayoutManager = GridLayoutManager(this, 1, GridLayoutManager.VERTICAL, false)
	private val keepAliveHandler = Handler(Looper.getMainLooper())
	private val keepAliveTask: Runnable = object : Runnable {
		override fun run() {
			ThreemaApplication.activityUserInteract(this@GroupCallActivity)
			keepAliveHandler.postDelayed(this, KEEP_ALIVE_DELAY)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		logger.debug("Create group call activity")
		super.onCreate(savedInstanceState)

		lockAppService = ThreemaApplication.requireServiceManager().lockAppService
		preferenceService = ThreemaApplication.requireServiceManager().preferenceService
		sensorService = ThreemaApplication.requireServiceManager().sensorService
		groupService = ThreemaApplication.requireServiceManager().groupService
		permissionRegistry = PermissionRegistry(this)

		if (!isAllowed) {
			showToast(R.string.master_key_locked)
			finish()
			return
		}

		handleIntent(intent)

		setFullscreen()
		setContentView(R.layout.activity_group_call)
		hideSystemUi()

		adjustWindowOffsets()

		views = Views()

		viewModel.getFinishEvents().observe(this, this::handleFinishEvent)
		viewModel.title.observe(this, this::setTitle)
		viewModel.subTitle.observe(this, this::setSubTitle)
		viewModel.statusMessage.observe(this, this::setStatusMessage)
		viewModel.startTimeUpdate.observe(this, this::setStartTime)
		viewModel.group.observe(this, this::setGroupChatAction)
		viewModel.getCameraFlipEvents().observe(this) { onCameraFlip() }
		viewModel.groupAvatar.observe(this) { views.background.setImageBitmap(it) }

		initAudioButtons()
		initCameraButtons()
		initHangupButton()
		initGradientClick()
		initParticipantsList()

		viewModel.isConnecting().observe(this) {
			views.background.visibility = when (it) {
				GroupCallViewModel.ConnectingState.IDLE, GroupCallViewModel.ConnectingState.INITIATED -> View.VISIBLE
				else -> View.GONE
			}
		}

		viewModel.isCallRunning().observe(this) { running ->
			if (running) {
				views.buttonToggleCamera.visibility = View.VISIBLE
				views.buttonToggleMic.visibility = View.VISIBLE
				views.buttonSelectAudioDevice.visibility = View.VISIBLE
				views.buttonToggleCamera.postDelayed({
					if (!viewModel.toggleCameraTooltipShown
						&& infoAndControlsShown
						&& views.buttonToggleCamera.visibility == View.VISIBLE
						&& views.buttonFlipCamera.visibility != View.VISIBLE) {
						val location = IntArray(2)
						views.buttonToggleCamera.getLocationInWindow(location)
						location[1] -= views.buttonToggleCamera.height / 5
						TooltipPopup(
							this,
							R.string.preferences__tooltip_gc_camera,
							R.layout.popup_tooltip_top_right,
							this
						).show(
							this,
							views.buttonToggleCamera,
							getString(R.string.tooltip_voip_turn_on_camera),
							TooltipPopup.ALIGN_BELOW_ANCHOR_ARROW_RIGHT,
							location,
							2500
						)
						viewModel.toggleCameraTooltipShown = true
					}
				}, 2000)
			} else {
				views.buttonToggleCamera.visibility = View.GONE
				views.buttonFlipCamera.visibility = View.GONE
				views.buttonToggleMic.visibility = View.GONE
				views.buttonSelectAudioDevice.visibility = View.GONE
			}
		}

		// make sure lock screen is not activated during call
		keepAliveHandler.removeCallbacksAndMessages(null)
		keepAliveHandler.postDelayed(keepAliveTask, KEEP_ALIVE_DELAY)

		gestureDetector = GestureDetectorCompat(this, object : SimpleOnGestureListener() {
			override fun onSingleTapUp(e: MotionEvent): Boolean {
				logger.trace("onSingleTapUp")
				toggleInfoAndControls()
				return true
			}

			override fun onLongPress(e: MotionEvent) {
				logger.trace("onLongPress")
			}
		})

		intent?.getBooleanExtra(EXTRA_MICROPHONE_ACTIVE, true)?.let {
			viewModel.microphoneActiveDefault = it
			intent?.removeExtra(EXTRA_MICROPHONE_ACTIVE)
		}
	}

	override fun onResume() {
		super.onResume()
		if (views.duration.visibility == View.VISIBLE) {
			views.layout.postDelayed(autoRemoveInfoAndControlsRunnable, TIMEOUT_HIDE_NAVIGATION_MILLIS)
		}
	}

	override fun onPause() {
		views.layout.removeCallbacks(autoRemoveInfoAndControlsRunnable)
		super.onPause()
	}

	override fun onNewIntent(intent: Intent?) {
		super.onNewIntent(intent)

		if (intent == null) {
			return
		}

		val currentCallGroupId = viewModel.getGroupId()
		val groupId = LocalGroupId(intent.getIntExtra(EXTRA_GROUP_ID, -1))
		if (currentCallGroupId != groupId) {
			// If we have a running group call and receive an intent for another group call, we
			// temporarily store the intent and leave the current call.
			this.newIntent = intent
			viewModel.leaveCall()
		}
	}

	override fun onDestroy() {
		logger.trace("destroy group call activity")

		// remove lockscreen keepalive
		keepAliveHandler.removeCallbacksAndMessages(null)

		participantsAdapter.teardown()

		// Unregister sensor listeners
		sensorService.unregisterSensors(SENSOR_TAG_GROUP_CALL)
		sensorEnabled = false

		// If an intent has been set in this field, we start a new instance
		if (newIntent != null) {
			startActivity(newIntent)
		}
		super.onDestroy()
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		permissionRegistry.handlePermissionResult(requestCode, permissions, grantResults)
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
	}

	private suspend fun requestMicrophonePermission(): Boolean = permissionRegistry.requestMicrophonePermissions().granted

	private suspend fun requestBluetoothPermission(): Boolean = permissionRegistry.requestBluetoothPermission(true).granted

	private fun handleIntent(intent: Intent) {
		logger.debug("handleIntent")

		val groupId = LocalGroupId(intent.getIntExtra(EXTRA_GROUP_ID, -1))
		viewModel.setGroupId(groupId)
		viewModel.cancelNotification()

		if (!lockAppService.isLocked) {
			val groupModel = groupService.getById(groupId.id)

			if (groupModel != null && groupService.isGroupMember(groupModel)) {
				checkPhoneStateAndJoinCall()
			} else {
				Toast.makeText(this, R.string.you_are_not_a_member_of_this_group, Toast.LENGTH_LONG).show()
				finish()
			}
		}
	}

	private fun checkPhoneStateAndJoinCall() {
		try {
			if (VoipUtil.isPSTNCallOngoingRespectPreference(this, this::joinCall, readPhoneStateSettingsLauncher)) {
				// A PSTN call is ongoing
				SimpleStringAlertDialog.newInstance(R.string.group_call, R.string.voip_another_pstn_call)
						.setOnDismissRunnable { finish() }
						.show(supportFragmentManager, "err")
			} else {
				joinCall()
			}
		} catch (exception: SecurityException) {
			logger.info("Permission not granted: {}", exception)
		}
	}

	private fun joinCall() {
		logger.debug("Joining call")
		CoroutineScope(Dispatchers.Default).launch {
			if (requestMicrophonePermission()) {
				if (!requestBluetoothPermission()) {
					withContext(Dispatchers.Main) {
						showToast(R.string.permission_bluetooth_connect_required)
					}
					logger.warn("BLUETOOTH_CONNECT permission not granted")
					// continue without bluetooth support
				}
				withContext(Dispatchers.Main) {
					viewModel.joinCall()
				}
			} else {
				logger.info("Microphone permission denied")
				if (!ActivityCompat.shouldShowRequestPermissionRationale(this@GroupCallActivity, RECORD_AUDIO)) {
					// permission was permanently denied
					withContext(Dispatchers.Main) {
						val alert : ThreemaDialogFragment = GenericAlertDialog.newInstance(
								R.string.group_call,
								getString(R.string.group_call_mic_permission_rationale,
										getString(R.string.app_name)),
								R.string.settings,
								R.string.cancel)
						alert.show(this@GroupCallActivity.supportFragmentManager, DIALOG_TAG_MIC_PERMISSION_DENIED)
					}
				} else {
					setResult(RESULT_CANCELED)
					finish()
				}
			}
		}
	}

	private fun setFullscreen() {
		// Set window styles for fullscreen-window size. Needs to be done before
		// adding content.
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		window.addFlags(getFullscreenWindowFlags())

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			setShowWhenLocked(true)
			setTurnScreenOn(true)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
			keyguard?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
				override fun onDismissError() {
					logger.warn("Keyguard dismissing is currently not feasible")
				}

				override fun onDismissSucceeded() {
					logger.debug("Keyguard dismissed")
				}

				override fun onDismissCancelled() {
					logger.debug("Keyguard dismissing cancelled")
				}
			})
		}
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus) {
			hideSystemUi()
		}

		if (sensorEnabled) {
			if (hasFocus) {
				sensorService.registerSensors(SENSOR_TAG_GROUP_CALL, this, true)
			} else {
				sensorService.unregisterSensors(SENSOR_TAG_GROUP_CALL)
			}
		}
	}

	private fun hideSystemUi() {
		WindowCompat.setDecorFitsSystemWindows(window, false)
		WindowInsetsControllerCompat(window, findViewById(R.id.group_call_layout)).let {
			controller ->
			controller.hide(WindowInsetsCompat.Type.systemBars())
			controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			window.attributes.layoutInDisplayCutoutMode =
				WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
		}
	}

	private fun getFullscreenWindowFlags(): Int {
		var flags = (
			WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
			or WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES
		)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			@Suppress("DEPRECATION")
			flags = flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
			@Suppress("DEPRECATION")
			flags = flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
			@Suppress("DEPRECATION")
			flags = flags or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
		}
		return flags
	}

	private fun initAudioButtons() {
		initMuteMicrophoneButton()
		initAudioSelectionButton()
	}

	private fun initMuteMicrophoneButton() {
		var microphoneMuted = true
		viewModel.isMicrophoneActive().observe(this) { microphoneActive ->
			microphoneMuted = !microphoneActive
			val imageResource = when (microphoneMuted) {
				true -> R.drawable.ic_mic_off_outline
				false -> R.drawable.ic_keyboard_voice_outline
			}
			views.buttonToggleMic.setImageResource(imageResource)
		}
		views.buttonToggleMic.setOnClickListener {
			viewModel.muteMicrophone(!microphoneMuted)
		}
	}

	private fun initAudioSelectionButton() {
		viewModel.getSelectedAudioDevice().observe(this) {
			views.buttonSelectAudioDevice.selectedAudioDevice = it
			if (it === AudioDevice.EARPIECE) {
				if (!sensorService.isSensorRegistered(SENSOR_TAG_GROUP_CALL)) {
					sensorService.registerSensors(
						SENSOR_TAG_GROUP_CALL,
						this@GroupCallActivity,
						true
					)
				}
				sensorEnabled = true
			} else {
				sensorService.unregisterSensors(SENSOR_TAG_GROUP_CALL)
				sensorEnabled = false
			}

			volumeControlStream = when(it) {
				AudioDevice.SPEAKER_PHONE -> AudioManager.STREAM_MUSIC
				AudioDevice.EARPIECE,
				AudioDevice.WIRED_HEADSET,
				AudioDevice.BLUETOOTH -> AudioManager.STREAM_VOICE_CALL
				else -> AudioManager.USE_DEFAULT_STREAM_TYPE
			}
		}
		viewModel.getAudioDevices().observe(this) {
			views.buttonSelectAudioDevice.audioDevices = it.toSet()
		}
		views.buttonSelectAudioDevice.let {
			it.selectionListener = CallAudioSelectorButton.AudioDeviceSelectionListener { selectedDevice ->
				viewModel.selectAudioDevice(selectedDevice)
			}

			it.multiSelectionListener = CallAudioSelectorButton.AudioDeviceMultiSelectListener { audioDevices, selectedDevice ->
				var currentDeviceIndex = -1
				val items = ArrayList<BottomSheetItem>()

				for ((i, device) in audioDevices.withIndex()) {
					val index = device.ordinal
					items.add(BottomSheetItem(
							device.getIconResource(),
							getString(device.getStringResource()),
							index.toString())
					)
					if (device == selectedDevice) {
						currentDeviceIndex = i
					}
				}

				val dialog = BottomSheetListDialog.newInstance(0, items, currentDeviceIndex)
				dialog.setCallback { tag -> viewModel.selectAudioDevice(AudioDevice.values()[tag.toInt()]) }
				dialog.show(supportFragmentManager, DIALOG_TAG_SELECT_AUDIO_DEVICE)
			}
		}
	}

	private fun initCameraButtons() {
		var cameraMuted = false

		// init camera mute button
		viewModel.isCameraActive().observe(this) { cameraActive ->
			cameraMuted = !cameraActive
			val resource = when (cameraActive) {
				true -> R.drawable.ic_videocam_black_outline
				false -> R.drawable.ic_videocam_off_black_outline
			}
			views.buttonToggleCamera.setImageResource(resource)
			views.buttonFlipCamera.visibility = when {
				cameraActive -> View.VISIBLE
				else -> View.GONE
			}
		}
		views.buttonToggleCamera.setOnClickListener {
			if (cameraMuted) {
				checkCameraPermissionAndStartCapturing()
			} else {
				viewModel.muteCamera(true)
			}
		}

		// init camera flip button
		views.buttonFlipCamera.setOnClickListener {
			logger.debug("Flip camera")
			viewModel.flipCamera()
		}
	}

	private fun checkCameraPermissionAndStartCapturing() {
		CoroutineScope(Dispatchers.Default).launch {
			val granted = permissionRegistry.requestCameraPermissions().granted
			if (granted) {
				logger.debug("Start capturing from camera")
				withContext(Dispatchers.Main) {
					viewModel.muteCamera(false)
				}
			} else {
				logger.info("Camera permission denied")
				if (!ActivityCompat.shouldShowRequestPermissionRationale(this@GroupCallActivity, CAMERA)) {
					// permission was permanently denied
					withContext(Dispatchers.Main) {
						val alert : ThreemaDialogFragment = GenericAlertDialog.newInstance(
								R.string.group_call,
								getString(R.string.group_call_camera_permission_rationale,
										getString(R.string.app_name)),
								R.string.settings,
								R.string.cancel)
						alert.show(this@GroupCallActivity.supportFragmentManager, DIALOG_TAG_CAMERA_PERMISSION_DENIED)
					}
				}
			}
		}
	}

	private fun initHangupButton() {
		views.buttonHangup.apply {
			setOnClickListener { viewModel.leaveCall() }
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	private fun initGradientClick() {
		views.gradientOverlay.apply {
			setOnTouchListener { _, event ->
				if (!gestureDetector.onTouchEvent(event)) {
					views.participants.onTouchEvent(event)
				}
				true
			}
		}
	}

	private fun initParticipantsList() {
		val gutterPx = resources.getDimensionPixelSize(R.dimen.group_call_participants_item_gutter)

		val contactService = ThreemaApplication.requireServiceManager().contactService
		participantsAdapter = GroupCallParticipantsAdapter(contactService, gutterPx)
		views.participants.layoutManager = participantsLayoutManager
		views.participants.adapter = participantsAdapter
		views.participants.addItemDecoration(VerticalGridLayoutGutterDecoration(gutterPx))

		viewModel.getEglBaseAndParticipants().observe(this) { (eglBase, participants) ->
			participantsAdapter.isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
			participantsLayoutManager.spanCount = getParticipantsLayoutManagerSpanCount(participants.size)
			// TODO(ANDR-1956): It is actually not necessary to set eglBase each time, but it must be set, before any viewholders are created
			participantsAdapter.eglBase = eglBase
			participantsAdapter.setParticipants(participants)
		}
		viewModel.getCaptureStateUpdates().observe(this) {
			views.participants.adapter?.let { adapter ->
				if (adapter is GroupCallParticipantsAdapter) {
					adapter.updateCaptureStates()
				}
			}

		}
	}

	private fun getParticipantsLayoutManagerSpanCount(participants: Int): Int {
		return when {
			participants in 0..1 -> 1
			participants == 2 && participantsAdapter.isPortrait -> 1
			else -> 2
		}
	}

	private fun setTitle(title: String?) {
		views.title.text = title
	}

	private fun setSubTitle(subTitle: String?) {
		views.subTitle.text = subTitle
	}

	private fun setStatusMessage(message: String?) {
		views.status.text = message
		views.status.visibility = when(message) {
			null -> View.GONE
			else -> View.VISIBLE
		}
	}

	private fun setStartTime(startTime: Long?) {
		if (startTime != null) {
			views.duration.visibility = View.VISIBLE
			views.duration.base = startTime
			views.duration.start()
			if (!infoAndControlsShownManually) {
				views.layout.postDelayed(autoRemoveInfoAndControlsRunnable, TIMEOUT_HIDE_NAVIGATION_MILLIS)
			}
		} else {
			views.duration.visibility = View.GONE
			views.duration.stop()
		}
	}

	@UiThread
	private fun onCameraFlip() {
		views.participants.adapter?.let {
			if (it is GroupCallParticipantsAdapter) {
				it.updateMirroringForLocalParticipant()
			}
		}
	}

	private fun setGroupChatAction(groupModel: GroupModel?) {
		if (groupModel != null) {
			views.title.setOnClickListener {
				val intent = Intent(this, ComposeMessageActivity::class.java)
				intent.putExtra(ThreemaApplication.INTENT_DATA_GROUP, groupModel.id)
				startActivity(intent)
			}
		} else {
			views.title.setOnClickListener(null)
			views.title.isClickable = false
		}
	}

	private fun handleFinishEvent(event: GroupCallViewModel.FinishEvent) {
		logger.info("Finish group call activity: '{}'", event.reason)

		when (event.reason) {
			GroupCallViewModel.FinishEvent.Reason.ERROR,
			GroupCallViewModel.FinishEvent.Reason.INVALID_DATA,
			GroupCallViewModel.FinishEvent.Reason.TOKEN_INVALID,
			GroupCallViewModel.FinishEvent.Reason.NO_SUCH_CALL,
			GroupCallViewModel.FinishEvent.Reason.UNSUPPORTED_PROTOCOL_VERSION -> showToast(R.string.voip_gc_call_error)
			GroupCallViewModel.FinishEvent.Reason.SFU_NOT_AVAILABLE -> showToast(R.string.voip_gc_sfu_not_available)
			GroupCallViewModel.FinishEvent.Reason.FULL -> showCallFullToast(event.call)
			GroupCallViewModel.FinishEvent.Reason.LEFT -> Unit
		}

		if (event.call != null && viewModel.hasOtherJoinedCall(event.call)) {
			logger.info("There is another joined call, so recreate the activity")
			newIntent = getGroupCallIntent(this, event.call.groupId.id)
		}
		finish()
	}

	private fun showCallFullToast(callDescription: GroupCallDescription?) {
		val maxParticipants = callDescription?.maxParticipants
		if (maxParticipants != null) {
			showToast(R.string.voip_gc_call_full_n, maxParticipants.toInt())
		} else {
			showToast(R.string.voip_gc_call_full_generic)
		}
	}

	private fun showToast(@StringRes resId: Int, vararg params: Any) {
		Toast.makeText(this, getString(resId, *params), Toast.LENGTH_LONG).show()
	}

	private fun adjustWindowOffsets() {
		// Support notch
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content_layout)) { v: View, insets: WindowInsetsCompat ->
				if (!isInPictureInPictureMode) {
					if (insets.displayCutout != null) {
						v.setPadding(
								insets.displayCutout?.safeInsetLeft ?: 0,
								insets.displayCutout?.safeInsetTop ?: 0,
								insets.displayCutout?.safeInsetRight ?: 0,
								insets.displayCutout?.safeInsetBottom ?: 0
						)
					}
				} else {
					// reset notch margins for PIP
					v.setPadding(0, 0, 0, 0)
				}
				insets
			}
		}
	}

	private fun toggleInfoAndControls() {
		when {
			infoAndControlsShown -> hideInfoAndControls()
			else -> showInfoAndControls()
		}
	}

	private fun showInfoAndControls() {
		views.contentLayout.removeCallbacks(autoRemoveInfoAndControlsRunnable)

		val layoutMargin = resources.getDimensionPixelSize(R.dimen.call_activity_margin)
		val constraints = ConstraintSet()
		constraints.clone(views.contentLayout)
		constraints.clear(R.id.call_info, ConstraintSet.BOTTOM)
		constraints.clear(R.id.call_info, ConstraintSet.TOP)
		constraints.connect(R.id.call_info, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)
		constraints.clear(R.id.in_call_buttons, ConstraintSet.BOTTOM)
		constraints.clear(R.id.in_call_buttons, ConstraintSet.TOP)
		constraints.connect(R.id.in_call_buttons, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, layoutMargin)

		applyInfoAndControlsTransformation(true, constraints)
		infoAndControlsShown = true
		infoAndControlsShownManually = true
	}

	private fun hideInfoAndControls() {
		val constraints = ConstraintSet()

		constraints.clone(views.contentLayout)
		constraints.clear(R.id.call_info, ConstraintSet.BOTTOM)
		constraints.clear(R.id.call_info, ConstraintSet.TOP)
		constraints.connect(R.id.call_info, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
		constraints.clear(R.id.in_call_buttons, ConstraintSet.BOTTOM)
		constraints.clear(R.id.in_call_buttons, ConstraintSet.TOP)
		constraints.connect(R.id.in_call_buttons, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

		applyInfoAndControlsTransformation(false, constraints)
		infoAndControlsShown = false
		infoAndControlsShownManually = false
	}

	private fun applyInfoAndControlsTransformation(visible: Boolean, constraints: ConstraintSet)  {
		val transition = ChangeBounds()
		transition.duration = DURATION_ANIMATE_NAVIGATION_MILLIS
		transition.addListener(onEnd = {
			changeGradientVisibility(visible)
		})
		TransitionManager.beginDelayedTransition(views.contentLayout, transition)
		constraints.applyTo(views.contentLayout)
	}

	private fun changeGradientVisibility(visible: Boolean) {
		val alpha = when {
			visible -> 1f
			else -> 0f
		}
		views.gradientOverlay
			.animate()
			.setDuration(DURATION_ANIMATE_GRADIENT_VISIBILITY_MILLIS)
			.alpha(alpha)
	}

	private inner class Views {
		val layout: ConstraintLayout = findViewById(R.id.group_call_layout)
		val contentLayout: ConstraintLayout = findViewById(R.id.content_layout)

		val gradientOverlay: View = findViewById(R.id.gradient_overlay)

		// Background
		val background: ImageView = findViewById(R.id.background_image)

		// RecyclerView
		val participants: RecyclerView = findViewById(R.id.group_call_participants)

		// call info
		val title: EmojiTextView = findViewById(R.id.call_title)
		val subTitle: TextView = findViewById(R.id.call_sub_title)
		val status: AnimatedEllipsisTextView = findViewById(R.id.call_status)
		val duration: Chronometer = findViewById(R.id.call_duration)

		// buttons
		val buttonToggleCamera: ImageButton = findViewById(R.id.button_toggle_camera)
		val buttonFlipCamera: ImageButton = findViewById(R.id.button_flip_camera)
		val buttonToggleMic: ImageButton = findViewById(R.id.button_call_toggle_mic)
		val buttonSelectAudioDevice: CallAudioSelectorButton = findViewById(R.id.button_select_audio_device)
		val buttonHangup: ImageButton = findViewById(R.id.button_end_call)
	}

	override fun isPinLockable(): Boolean {
		return true
	}

	override fun onSensorChanged(key: String?, value: Boolean) {
		// called if sensor status changed
		logger.trace("onSensorChanged: {}={}", key, value)
	}

	override fun onYes(tag: String?, addData: Any?) {
		if (DIALOG_TAG_CAMERA_PERMISSION_DENIED == tag) {
			cameraSettingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
				data = Uri.fromParts("package", packageName, null)
			})
		} else if (DIALOG_TAG_MIC_PERMISSION_DENIED == tag) {
			audioSettingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
				data = Uri.fromParts("package", packageName, null)
			})
		}
	}

	override fun onNo(tag: String?, data: Any?) {
		if (DIALOG_TAG_MIC_PERMISSION_DENIED == tag) {
			logger.info("User confirmed denial of microphone permission")
			setResult(RESULT_CANCELED)
			finish()
		}
	}
}
