package ch.threema.app.voicemessage

import android.Manifest.permission
import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.di.DIJavaCompat.isSessionScopeReady
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ActivityService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toHMMSS
import ch.threema.common.toIntCapped
import com.google.android.material.button.MaterialButton
import kotlin.jvm.Throws
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

private val logger = getThreemaLogger("VoiceRecorderActivity")

class VoiceRecorderActivity : ThreemaAppCompatActivity(), OnAudioFocusChangeListener {

    init {
        logScreenVisibility(logger)
    }

    private val viewModel: VoiceRecorderViewModel by viewModel {
        parametersOf(
            requireMessageReceiverArgument(),
        )
    }

    private var wasRecreated = false

    private val preferenceService by inject<PreferenceService>()

    private lateinit var timerText: TextView
    private lateinit var sendButton: MaterialButton
    private lateinit var playButton: ImageView
    private lateinit var recordingPauseResumeButton: ImageView
    private lateinit var recordingOrPlayingIndicator: ImageView
    private lateinit var bluetoothToggle: ImageView
    private lateinit var seekBar: SeekBar

    private var hasAudioFocus = false

    private var blinkingJob: Job? = null
    private var keepAliveJob: Job? = null

    private lateinit var audioManager: AudioManager

    private val audioStateChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.onScoStateChanged(
                scoAudioState = intent.getIntExtra(
                    /* name = */
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    /* defaultValue = */
                    AudioManager.SCO_AUDIO_STATE_ERROR,
                ),
            )
        }
    }

    private val updateSeekbarCallback: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            viewModel.mediaPlayer?.let { mediaPlayer ->
                if (mediaPlayer.isPlaying) {
                    val currentProgress = mediaPlayer.currentPosition
                    seekBar.progress = currentProgress
                    timerText.text = currentProgress.milliseconds.toHMMSS()
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isSessionScopeReady()) {
            finish()
            return
        }

        setContentView(R.layout.activity_voice_recorder)

        // keep screen on during recording
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioManager = getSystemService<AudioManager>()!!

        if (intent == null) {
            logger.error("Missing intent")
            finish()
            return
        }

        wasRecreated = savedInstanceState != null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (
                !ConfigUtils.requestBluetoothConnectPermissions(
                    this,
                    null,
                    PERMISSION_REQUEST_CODE_BLUETOOTH_CONNECT,
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permission.BLUETOOTH_CONNECT),
                )
            ) {
                return
            }
        }

        postPermissionOnCreate(wasRecreated = wasRecreated)
    }

    @Throws(IllegalStateException::class)
    private fun requireMessageReceiverArgument(): MessageReceiver<*> {
        val messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(this, intent)
        check(messageReceiver != null) {
            "Missing message receiver"
        }
        return messageReceiver
    }

    private fun postPermissionOnCreate(wasRecreated: Boolean) {
        findViewById<LinearLayout>(R.id.button_layout).apply {
            layoutTransition.enableTransitionType(LayoutTransition.APPEARING or LayoutTransition.DISAPPEARING)
        }

        timerText = findViewById(R.id.timer_text)

        sendButton = findViewById(R.id.send_button)
        sendButton.setOnClickListener {
            viewModel.send()
        }

        findViewById<ImageView>(R.id.discard_button).setOnClickListener {
            viewModel.discard()
        }

        playButton = findViewById(R.id.play_button)

        recordingPauseResumeButton = findViewById(R.id.recording_pause_resume_button)

        seekBar = findViewById(R.id.seekbar)
        seekBar.setOnSeekBarChangeListener(
            object : OnSeekBarChangeListener {

                private var seekingTo: Int = -1

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        seekingTo = progress
                        timerText.text = progress.milliseconds.toHMMSS()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    viewModel.pausePlayback()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    if (seekingTo >= 0) {
                        viewModel.seekPlaybackTo(seekingTo.milliseconds)
                        viewModel.resumePlayback()
                    }
                }
            },
        )

        recordingOrPlayingIndicator = findViewById(R.id.recording_or_playing_indicator)

        val isBluetoothEnabled: Boolean = checkIsBluetoothEnabled()
        bluetoothToggle = findViewById(R.id.bluetooth_toggle)
        bluetoothToggle.isVisible = isBluetoothEnabled
        if (isBluetoothEnabled) {
            bluetoothToggle.setOnClickListener {
                onClickBluetoothToggle()
            }
            registerReceiver(
                /* receiver = */
                audioStateChangedReceiver,
                /* filter = */
                IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect(::onViewModelEvent)
                }
                launch {
                    viewModel.state.collect(::onStateChanged)
                }
            }
        }

        if (!wasRecreated) {
            if (isBluetoothEnabled && !preferenceService.getVoiceRecorderBluetoothDisabled()) {
                tryStartBluetoothScoConnection()
            }
            requestAudioFocus()
            viewModel.startRecording()
        }
    }

    private fun onViewModelEvent(event: VoiceRecorderViewModelEvent) {
        when (event) {
            is VoiceRecorderViewModelEvent.FailedToCreateAudioOutputFile -> {
                showToast(R.string.recording_canceled, ToastDuration.LONG)
                finish()
            }
            VoiceRecorderViewModelEvent.FailedToOpenAudioRecorder -> {
                showToast(R.string.recording_canceled, ToastDuration.LONG)
                finish()
            }
            VoiceRecorderViewModelEvent.FailedToPlayRecording -> {
                showToast(R.string.an_error_occurred)
            }
            VoiceRecorderViewModelEvent.RecorderReachedMaxDuration -> {
                showSendConfirmationDialog()
            }
            VoiceRecorderViewModelEvent.RecorderReachedMaxFileSize -> {
                showSendConfirmationDialog()
            }
            VoiceRecorderViewModelEvent.RecorderError -> {
                showToast(R.string.recording_canceled, ToastDuration.LONG)
                viewModel.stopRecording()
                finish()
            }
            is VoiceRecorderViewModelEvent.PlaybackFinished -> {
                seekBar.progress = event.endProgress
            }
            VoiceRecorderViewModelEvent.Sent -> {
                finish()
            }
            VoiceRecorderViewModelEvent.FailedToDetermineDuration -> {
                showToast(R.string.unable_to_determine_recording_length, ToastDuration.LONG)
            }
            VoiceRecorderViewModelEvent.ConfirmationRequiredToDiscard -> {
                showDiscardConfirmationDialog()
            }
            VoiceRecorderViewModelEvent.Discarded -> {
                finish()
            }
        }
    }

    private val onClickPauseRecording = View.OnClickListener {
        viewModel.pauseRecording()
    }

    private val onClickResumeRecording = View.OnClickListener {
        viewModel.resumeRecording()
    }

    private val onClickStopRecording = View.OnClickListener {
        viewModel.stopRecording()
    }

    private val onClickPlay = View.OnClickListener {
        if (viewModel.mediaPlayer != null) {
            viewModel.resumePlayback()
        } else {
            viewModel.startPlayback()
        }
    }

    private val onClickPausePlayback = View.OnClickListener {
        viewModel.pausePlayback()
    }

    @SuppressLint("SetTextI18n")
    private fun onStateChanged(screenState: VoiceRecorderScreenState) {
        logger.debug("State changed to {}", screenState)

        val mediaState = screenState.mediaState

        // Button to pause or resume the recording
        recordingPauseResumeButton.isInvisible = mediaState !is MediaState.Record
        if (mediaState is MediaState.Record) {
            if (mediaState.isRecording) {
                recordingPauseResumeButton.clearColorFilter()
            } else {
                recordingPauseResumeButton.setColorFilter(
                    getColor(R.color.material_red),
                    PorterDuff.Mode.SRC_IN,
                )
            }
            recordingPauseResumeButton.setImageResource(
                if (mediaState.isRecording) R.drawable.ic_pause else R.drawable.ic_record,
            )
            recordingPauseResumeButton.setContentDescription(
                getString(if (mediaState.isRecording) R.string.pause else R.string.continue_recording),
            )

            if (mediaState.isRecording) {
                recordingPauseResumeButton.setOnClickListener(onClickPauseRecording)
            } else {
                recordingPauseResumeButton.setOnClickListener(onClickResumeRecording)
            }
        }

        // The blinking indicator that blinks while recording or playback is active
        if (mediaState is MediaState.Playback) {
            recordingOrPlayingIndicator.setColorFilter(
                ConfigUtils.getColorFromAttribute(this, R.attr.colorOnSurface),
                PorterDuff.Mode.SRC_IN,
            )
            recordingOrPlayingIndicator.setImageResource(R.drawable.ic_play)
        } else {
            recordingOrPlayingIndicator.clearColorFilter()
            recordingOrPlayingIndicator.setImageResource(R.drawable.ic_record)
        }

        // Button to cancel the recording, start, pause or resume the playback
        playButton.setImageResource(
            when (mediaState) {
                is MediaState.Record -> R.drawable.ic_stop
                is MediaState.FinishedRecording -> R.drawable.ic_play
                is MediaState.Playback -> if (mediaState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            },
        )
        playButton.contentDescription = getString(
            when (mediaState) {
                is MediaState.Record -> R.string.stop
                is MediaState.FinishedRecording -> R.string.play
                is MediaState.Playback -> if (mediaState.isPlaying) R.string.stop else R.string.play
            },
        )
        playButton.setOnClickListener(
            when (mediaState) {
                is MediaState.Record -> onClickStopRecording
                is MediaState.FinishedRecording -> onClickPlay
                is MediaState.Playback -> if (mediaState.isPlaying) onClickPausePlayback else onClickPlay
            },
        )

        // Seekbar while playback
        seekBar.isVisible = mediaState is MediaState.Playback
        if (mediaState is MediaState.Playback) {
            if (mediaState.duration.inWholeMilliseconds.toIntCapped() != seekBar.max) {
                seekBar.max = mediaState.duration.inWholeMilliseconds.toIntCapped()
            }
            if (mediaState.isPlaying) {
                Choreographer.getInstance().postFrameCallback(updateSeekbarCallback)
            } else {
                Choreographer.getInstance().removeFrameCallback(updateSeekbarCallback)
            }
        }

        setInhibitAppLock(
            mediaState is MediaState.Record || mediaState is MediaState.Playback && mediaState.isPlaying,
        )

        val currentlyRecording = mediaState is MediaState.Record && mediaState.isRecording
        val currentlyPlaying = mediaState is MediaState.Playback && mediaState.isPlaying
        if (currentlyRecording || currentlyPlaying) {
            startBlinking()
        } else {
            stopBlinking()
            recordingOrPlayingIndicator.isGone = true
        }

        when (mediaState) {
            is MediaState.Record -> timerText.text = mediaState.duration.toHMMSS()
            is MediaState.FinishedRecording -> timerText.text = "00:00"
            is MediaState.Playback -> {}
        }

        bluetoothToggle.setImageResource(
            when (screenState.scoAudioState) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> R.drawable.ic_bluetooth_connected
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED, AudioManager.SCO_AUDIO_STATE_ERROR -> R.drawable.ic_bluetooth_disabled
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> R.drawable.ic_bluetooth_searching_outline
                else -> R.drawable.ic_bluetooth_searching_outline
            },
        )
    }

    private fun showSendConfirmationDialog() {
        GenericAlertDialog.newInstance(
            /* title = */
            R.string.recording_stopped_title,
            /* message = */
            R.string.recording_stopped_message,
            /* positive = */
            R.string.yes,
            /* negative = */
            R.string.no,
            /* cancelable = */
            false,
        ).apply {
            setCallback(
                object : DialogClickListener {
                    override fun onYes(tag: String?, data: Any?) {
                        viewModel.send()
                    }

                    override fun onNo(tag: String?, data: Any?) {
                        viewModel.stopRecording()
                        finish()
                    }
                },
            )
        }.show(supportFragmentManager)
    }

    private fun showDiscardConfirmationDialog() {
        GenericAlertDialog.newInstance(
            /* title = */
            R.string.cancel_recording,
            /* message = */
            R.string.cancel_recording_message,
            /* positive = */
            R.string.yes,
            /* negative = */
            R.string.no,
            /* cancelable = */
            false,
        ).apply {
            setCallback { _, _ ->
                viewModel.discard(force = true)
            }
        }.show(supportFragmentManager)
    }

    private fun onClickBluetoothToggle() {
        val isBluetoothScoOn = try {
            audioManager.isBluetoothScoOn()
        } catch (e: Exception) {
            logger.error("Failed to determine current bluetooth sco state", e)
            return
        }
        if (isBluetoothScoOn) {
            tryStopBluetoothScoConnection()
        } else {
            tryStartBluetoothScoConnection()
        }
    }

    private fun checkIsBluetoothEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val result = bluetoothAdapter != null &&
            bluetoothAdapter.isEnabled &&
            bluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothAdapter.STATE_CONNECTED

        logger.debug("isBluetoothEnabled = {}", result)
        return result
    }

    private fun startBlinking() {
        if (blinkingJob?.isActive == true) {
            return
        }
        logger.debug("startBlinking")
        blinkingJob = lifecycleScope.launch {
            while (isActive) {
                recordingOrPlayingIndicator.isInvisible = !recordingOrPlayingIndicator.isInvisible
                delay(600.milliseconds)
            }
        }
    }

    private fun stopBlinking() {
        logger.debug("stopBlinking")
        blinkingJob?.cancel()
    }

    override fun enableOnBackPressedCallback() = true

    override fun handleOnBackPressed() {
        showDiscardConfirmationDialog()
    }

    private fun requestAudioFocus() {
        logger.debug("requestAudioFocus")
        if (hasAudioFocus) {
            return
        }
        audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
        )
        hasAudioFocus = true
    }

    private fun abandonAudioFocus() {
        logger.debug("abandonAudioFocus")
        getSystemService<AudioManager>()?.abandonAudioFocus(this)
        hasAudioFocus = false
    }

    /**
     * Keep timed app-lock from activating by simulating activity
     *
     * @param shouldInhibit true if timed app-lock should be deactivated, false otherwise
     */
    private fun setInhibitAppLock(shouldInhibit: Boolean) {
        if (shouldInhibit && keepAliveJob?.isActive != true) {
            logger.info("Start inhibiting timed app-lock")
            keepAliveJob = lifecycleScope.launch {
                while (isActive) {
                    ActivityService.activityUserInteract(this@VoiceRecorderActivity)
                    delay(20.seconds)
                }
            }
        } else if (!shouldInhibit) {
            logger.info("Stop inhibiting timed app-lock")
            keepAliveJob?.cancel()
            keepAliveJob = null
        }
    }

    private fun tryStartBluetoothScoConnection() {
        logger.info("Starting bluetooth sco connection")
        try {
            audioManager.startBluetoothSco()
        } catch (e: Exception) {
            logger.error("Failed to start bluetooth sco connection", e)
        }
    }

    private fun tryStopBluetoothScoConnection() {
        logger.info("Stopping bluetooth sco connection")
        try {
            audioManager.stopBluetoothSco()
        } catch (e: Exception) {
            logger.error("Failed to stop bluetooth sco connection", e)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            // Lost focus for an unbounded amount of time: stop playback and release media player
            hasAudioFocus = false
            viewModel.onLostAudioFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        logger.debug("onPause - isChangingConfigurations: {}", isChangingConfigurations)
        if (!isChangingConfigurations) {
            viewModel.onPause()
        }
    }

    override fun onDestroy() {
        logger.debug("onDestroy - isChangingConfigurations: {}", isChangingConfigurations)
        Choreographer.getInstance().removeFrameCallback(updateSeekbarCallback)
        if (checkIsBluetoothEnabled()) {
            // Keep bluetooth sco connection and audio focus during an activity recreate
            if (!isChangingConfigurations) {
                tryStopBluetoothScoConnection()
                abandonAudioFocus()
            }
            runCatching {
                unregisterReceiver(audioStateChangedReceiver)
            }
        }
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE_BLUETOOTH_CONNECT) {
            postPermissionOnCreate(wasRecreated = wasRecreated)
        }
    }

    companion object {

        const val VOICE_MESSAGE_FILE_EXTENSION = ".aac"

        private const val PERMISSION_REQUEST_CODE_BLUETOOTH_CONNECT = 45454

        @JvmStatic
        val defaultSamplingRate: Int
            get() = if (ConfigUtils.hasBrokenAudioRecorder()) 44000 else 44100
    }
}
