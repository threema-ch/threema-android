package ch.threema.app.voicemessage

import android.media.AudioManager
import android.net.Uri
import kotlin.time.Duration

data class VoiceRecorderScreenState(
    val mediaState: MediaState,
    val scoAudioState: Int,
) {
    companion object {
        fun initial() = VoiceRecorderScreenState(
            mediaState = MediaState.Record(
                isRecording = false,
                duration = Duration.ZERO,
            ),
            scoAudioState = AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
        )
    }
}

sealed interface MediaState {

    /**
     *  @param duration The current duration of the recorder (only accurate to one full second)
     */
    data class Record(
        val isRecording: Boolean,
        val duration: Duration,
    ) : MediaState

    data class FinishedRecording(
        val uri: Uri,
    ) : MediaState

    data class Playback(
        val uri: Uri,
        val isPlaying: Boolean,
        val duration: Duration,
    ) : MediaState
}
