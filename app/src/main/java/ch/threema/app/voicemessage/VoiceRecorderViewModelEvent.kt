package ch.threema.app.voicemessage

sealed interface VoiceRecorderViewModelEvent {

    data object FailedToCreateAudioOutputFile : VoiceRecorderViewModelEvent

    data object FailedToOpenAudioRecorder : VoiceRecorderViewModelEvent

    data object RecorderReachedMaxDuration : VoiceRecorderViewModelEvent

    data object RecorderReachedMaxFileSize : VoiceRecorderViewModelEvent

    data object RecorderError : VoiceRecorderViewModelEvent

    data object FailedToPlayRecording : VoiceRecorderViewModelEvent

    data object FailedToDetermineDuration : VoiceRecorderViewModelEvent

    data class PlaybackFinished(val endProgress: Int) : VoiceRecorderViewModelEvent

    data object Sent : VoiceRecorderViewModelEvent

    data object ConfirmationRequiredToDiscard : VoiceRecorderViewModelEvent

    data object Discarded : VoiceRecorderViewModelEvent
}
