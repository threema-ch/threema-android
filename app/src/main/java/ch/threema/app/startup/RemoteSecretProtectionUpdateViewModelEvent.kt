package ch.threema.app.startup

sealed class RemoteSecretProtectionUpdateViewModelEvent {
    data object PromptForCredentials : RemoteSecretProtectionUpdateViewModelEvent()
    data object Done : RemoteSecretProtectionUpdateViewModelEvent()
}
