package ch.threema.app.voicemessage

import android.app.Application
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.text.format.DateUtils
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.ui.MediaItem
import ch.threema.app.utils.MediaPlayerStateWrapper
import ch.threema.app.utils.MimeUtil
import ch.threema.app.voicemessage.VoiceRecorderActivity.Companion.VOICE_MESSAGE_FILE_EXTENSION
import ch.threema.app.voicemessage.VoiceRecorderActivity.Companion.defaultSamplingRate
import ch.threema.base.utils.getThreemaLogger
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("VoiceRecorderViewModel")

class VoiceRecorderViewModel(
    application: Application,
    private val fileService: FileService,
    private val messageService: MessageService,
    private val preferenceService: PreferenceService,
    private val messageReceiver: MessageReceiver<*>,
) : AndroidViewModel(application) {

    private val _events: MutableSharedFlow<VoiceRecorderViewModelEvent> = MutableSharedFlow()
    val events: SharedFlow<VoiceRecorderViewModelEvent> = _events

    private val _state: MutableStateFlow<VoiceRecorderScreenState> = MutableStateFlow(VoiceRecorderScreenState.initial())
    val state: StateFlow<VoiceRecorderScreenState> = _state

    private var audioOutputUri: Uri? = null

    private var mediaRecorder: MediaRecorder? = null
    var mediaPlayer: MediaPlayerStateWrapper? = null
        private set

    private var recordingTimerJob: Job? = null

    private val onStopRecordingListener = object : AudioRecorder.OnStopListener {
        override fun onRecordingReachedMaxDuration() {
            viewModelScope.launch {
                _events.emit(VoiceRecorderViewModelEvent.RecorderReachedMaxDuration)
            }
        }

        override fun onRecordingReachedMaxFileSize() {
            viewModelScope.launch {
                _events.emit(VoiceRecorderViewModelEvent.RecorderReachedMaxFileSize)
            }
        }

        override fun onRecordingError() {
            viewModelScope.launch {
                _events.emit(VoiceRecorderViewModelEvent.RecorderError)
            }
        }
    }

    fun startRecording() {
        val currentMediaRecordState = _state.value.mediaState
        if (currentMediaRecordState !is MediaState.Record || currentMediaRecordState.isRecording) {
            return
        }
        logger.info("Start recording")
        viewModelScope.launch {
            val outputUri: Uri = try {
                createAudioOutputFile()
            } catch (e: IOException) {
                logger.error("Failed to create temp audio file", e)
                _events.emit(VoiceRecorderViewModelEvent.FailedToCreateAudioOutputFile)
                return@launch
            }
            logger.info("Created recording output file {}", outputUri)
            val audioRecorder = AudioRecorder(application).apply {
                setOnStopListener(onStopRecordingListener)
            }
            try {
                mediaRecorder = audioRecorder.prepare(outputUri, defaultSamplingRate)!!
                    .also { mediaRecorder ->
                        mediaRecorder.start()
                        logger.info("Started recording with {}", mediaRecorder)
                    }
            } catch (e: Exception) {
                _events.emit(VoiceRecorderViewModelEvent.FailedToOpenAudioRecorder)
                logger.error("AudioRecorder exception occurred", e)
                runCatching {
                    mediaRecorder?.reset()
                    mediaRecorder?.release()
                    mediaRecorder = null
                }
                return@launch
            }
            audioOutputUri = outputUri
            _state.value = _state.value.copy(
                mediaState = MediaState.Record(
                    isRecording = true,
                    duration = Duration.ZERO,
                ),
            )
            startOrResumeRecordingTimer()
        }
    }

    private fun startOrResumeRecordingTimer() {
        val currentMediaRecordState = _state.value.mediaState
        if (currentMediaRecordState !is MediaState.Record || !currentMediaRecordState.isRecording || recordingTimerJob?.isActive == true) {
            return
        }
        recordingTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1.seconds)
                val currentState = _state.value
                if (currentState.mediaState is MediaState.Record && currentState.mediaState.isRecording) {
                    _state.compareAndSet(
                        expect = currentState,
                        update = currentState.copy(
                            mediaState = currentState.mediaState.copy(
                                duration = currentState.mediaState.duration + 1.seconds,
                            ),
                        ),
                    )
                } else {
                    break
                }
            }
        }
    }

    fun pauseRecording() {
        val currentMediaRecordState = _state.value.mediaState
        if (currentMediaRecordState !is MediaState.Record || !currentMediaRecordState.isRecording) {
            return
        }
        logger.info("Pause recording")
        try {
            mediaRecorder?.pause()
        } catch (e: Exception) {
            logger.error("Exception while pausing recording", e)
        }
        _state.value = _state.value.copy(
            mediaState = currentMediaRecordState.copy(
                isRecording = false,
            ),
        )
    }

    fun resumeRecording() {
        val currentMediaRecordState = _state.value.mediaState
        if (currentMediaRecordState !is MediaState.Record || currentMediaRecordState.isRecording) {
            return
        }
        logger.info("Resume recording")
        try {
            mediaRecorder?.resume()
        } catch (e: Exception) {
            logger.error("Exception while resuming recording", e)
        }
        _state.value = _state.value.copy(
            mediaState = currentMediaRecordState.copy(
                isRecording = true,
            ),
        )
        startOrResumeRecordingTimer()
    }

    fun stopRecording() {
        val currentMediaRecordState = _state.value.mediaState
        if (currentMediaRecordState !is MediaState.Record) {
            return
        }
        logger.info("Stop recording")
        try {
            mediaRecorder?.let { recorder ->
                recorder.stop()
                logger.info("Stopped recording with {}", recorder)
            }
        } catch (e: Exception) {
            logger.error("Exception while stopping recording", e)
        }
        releaseMediaRecorder()
        _state.value = _state.value.copy(
            mediaState = MediaState.FinishedRecording(
                uri = audioOutputUri!!,
            ),
        )
    }

    fun startPlayback() {
        val currentMediaFinishedRecordingState = _state.value.mediaState
        if (currentMediaFinishedRecordingState !is MediaState.FinishedRecording) {
            return
        }
        val mediaPlayer = MediaPlayerStateWrapper().apply {
            if (_state.value.scoAudioState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
            } else {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
            }
        }
        logger.info("Initializing media player")
        try {
            mediaPlayer.apply {
                setDataSource(application, currentMediaFinishedRecordingState.uri)
                setOnPreparedListener { player: MediaPlayer ->
                    mediaPlayer.start()
                    logger.info("Started media player {}", player)
                    _state.value = _state.value.copy(
                        mediaState = MediaState.Playback(
                            uri = currentMediaFinishedRecordingState.uri,
                            isPlaying = true,
                            duration = player.duration.coerceAtLeast(0).milliseconds,
                        ),
                    )
                }
                setOnCompletionListener { player: MediaPlayer ->
                    _state.value = _state.value.let { state ->
                        if (state.mediaState is MediaState.Playback) {
                            state.copy(
                                mediaState = state.mediaState.copy(
                                    isPlaying = false,
                                ),
                            )
                        } else {
                            state
                        }
                    }
                    viewModelScope.launch {
                        _events.emit(VoiceRecorderViewModelEvent.PlaybackFinished(player.duration))
                    }
                }
                prepare()
            }
            this@VoiceRecorderViewModel.mediaPlayer = mediaPlayer
        } catch (e: Exception) {
            viewModelScope.launch {
                _events.emit(VoiceRecorderViewModelEvent.FailedToPlayRecording)
            }
            logger.error("Failed to play recording", e)
            releaseMediaPlayer()
        }
    }

    fun pausePlayback() {
        val currentMediaPlaybackState = _state.value.mediaState
        val mediaPlayer = this.mediaPlayer
        if (currentMediaPlaybackState !is MediaState.Playback || !currentMediaPlaybackState.isPlaying || mediaPlayer == null) {
            return
        }
        logger.info("Pause media player {}", mediaPlayer)
        try {
            mediaPlayer.pause()
        } catch (e: Exception) {
            logger.error("Exception while pausing media player {}", mediaPlayer, e)
        }
        _state.value = _state.value.copy(
            mediaState = currentMediaPlaybackState.copy(isPlaying = false),
        )
    }

    fun seekPlaybackTo(duration: Duration) {
        val currentMediaPlaybackState = _state.value.mediaState
        val mediaPlayer = this.mediaPlayer
        if (currentMediaPlaybackState !is MediaState.Playback || mediaPlayer == null) {
            return
        }
        logger.info("Seek media player {} to {}", mediaPlayer, duration)
        if (duration > currentMediaPlaybackState.duration) {
            logger.warn("Cant seek media player to a frame exceeding its total duration")
            return
        }
        mediaPlayer.seekTo(
            /* msec = */
            duration.inWholeMilliseconds.toInt(),
        )
    }

    fun resumePlayback() {
        val currentMediaPlaybackState = _state.value.mediaState
        val mediaPlayer = this.mediaPlayer
        if (currentMediaPlaybackState !is MediaState.Playback || currentMediaPlaybackState.isPlaying || mediaPlayer == null) {
            return
        }
        logger.info("Resume media player {}", mediaPlayer)
        try {
            mediaPlayer.start()
        } catch (e: Exception) {
            logger.error("Exception while resuming media player {}", mediaPlayer, e)
        }
        _state.value = _state.value.copy(
            mediaState = currentMediaPlaybackState.copy(isPlaying = true),
        )
    }

    fun onPause() {
        when (_state.value.mediaState) {
            is MediaState.Record -> pauseRecording()
            is MediaState.Playback -> pausePlayback()
            else -> {}
        }
    }

    fun send() {
        val currentMediaState = _state.value.mediaState
        if (currentMediaState is MediaState.Record) {
            stopRecording()
        } else if (currentMediaState is MediaState.Playback) {
            releaseMediaPlayer()
        }

        val uri = audioOutputUri ?: run {
            logger.warn("Audio output uri is missing")
            viewModelScope.launch {
                _events.emit(VoiceRecorderViewModelEvent.FailedToDetermineDuration)
            }
            return
        }
        val audioFileDuration = getDurationFromFile(uri)
        if (audioFileDuration == Duration.ZERO) {
            viewModelScope.launch {
                _events.emit(VoiceRecorderViewModelEvent.FailedToDetermineDuration)
            }
            return
        }

        val mediaItem = MediaItem(uri, MimeUtil.MIME_TYPE_AUDIO_AAC, null).apply {
            durationMs = audioFileDuration.inWholeMilliseconds.coerceAtLeast(
                minimumValue = DateUtils.SECOND_IN_MILLIS,
            )
        }
        messageService.sendMediaAsync(
            /* mediaItems = */
            listOf(mediaItem),
            /* messageReceivers = */
            listOf(messageReceiver),
        )
        viewModelScope.launch {
            _events.emit(VoiceRecorderViewModelEvent.Sent)
        }
    }

    fun discard(force: Boolean = false) {
        viewModelScope.launch {
            when (val currentMediaState = _state.value.mediaState) {
                is MediaState.Record -> {
                    stopRecording()
                    if (currentMediaState.duration >= discardConfirmationThresholdDuration && !force) {
                        _events.emit(VoiceRecorderViewModelEvent.ConfirmationRequiredToDiscard)
                    } else {
                        _events.emit(VoiceRecorderViewModelEvent.Discarded)
                    }
                }
                is MediaState.FinishedRecording -> {
                    val duration = getDurationFromFile(currentMediaState.uri)
                    if (duration >= discardConfirmationThresholdDuration && !force) {
                        _events.emit(VoiceRecorderViewModelEvent.ConfirmationRequiredToDiscard)
                    } else {
                        _events.emit(VoiceRecorderViewModelEvent.Discarded)
                    }
                }
                is MediaState.Playback -> {
                    releaseMediaPlayer()
                    _events.emit(VoiceRecorderViewModelEvent.Discarded)
                }
            }
        }
    }

    fun onLostAudioFocus() {
        when (val currentMediaState = _state.value.mediaState) {
            is MediaState.Record -> {
                stopRecording()
            }
            is MediaState.FinishedRecording -> {
                // Do nothing
            }
            is MediaState.Playback -> {
                releaseMediaPlayer()
                _state.value = _state.value.copy(
                    mediaState = currentMediaState.copy(isPlaying = false),
                )
            }
        }
    }

    private fun getDurationFromFile(uri: Uri): Duration {
        logger.info("Attempting to retrieve duration from file {}", uri)
        val durationCheckMediaPlayer: MediaPlayer = MediaPlayer.create(application, uri)
            ?: run {
                logger.info("Unable to create a media player for checking size. File already deleted by OS?")
                return Duration.ZERO
            }
        val durationMs = durationCheckMediaPlayer.duration
        durationCheckMediaPlayer.release()
        logger.info("Duration in ms {}", durationMs)
        return if (durationMs > 0) {
            durationMs.milliseconds
        } else {
            Duration.ZERO
        }
    }

    @Throws(IOException::class)
    private fun createAudioOutputFile(): Uri =
        File.createTempFile(
            /* prefix = */
            "voice-",
            /* suffix = */
            VOICE_MESSAGE_FILE_EXTENSION,
            /* directory = */
            fileService.tempPath,
        ).toUri()

    /**
     *  Safe to call for an already released and cleared local media recorder
     */
    private fun releaseMediaRecorder() {
        mediaRecorder?.let { recorder ->
            logger.info("Releasing media recorder {}", recorder)
            runCatching {
                recorder.reset()
                recorder.release()
                mediaRecorder = null
                logger.info("Released media recorder {}", recorder)
            }
        }
    }

    /**
     *  Safe to call for an already released and cleared local media player
     */
    private fun releaseMediaPlayer() {
        mediaPlayer?.let { player ->
            runCatching {
                logger.info("Releasing media player {}", player)
                player.reset()
                player.release()
                mediaPlayer = null
                logger.info("Released media player {}", player)
            }
        }
    }

    fun onScoStateChanged(scoAudioState: Int) {
        val voiceRecorderBluetoothDisableSetting: Boolean? = when (scoAudioState) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> false
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> true
            AudioManager.SCO_AUDIO_STATE_ERROR -> false
            else -> null
        }
        voiceRecorderBluetoothDisableSetting?.let { setting ->
            preferenceService.setVoiceRecorderBluetoothDisabled(setting)
        }
        _state.value = _state.value.copy(
            scoAudioState = scoAudioState,
        )
        logger.info(
            "SCO audio state: {}",
            when (scoAudioState) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> "connected"
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "disconnected"
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> "connecting"
                AudioManager.SCO_AUDIO_STATE_ERROR -> "error"
                else -> ""
            },
        )
    }

    override fun onCleared() {
        releaseMediaRecorder()
        releaseMediaPlayer()
    }

    companion object {
        private val discardConfirmationThresholdDuration = 10.seconds
    }
}
