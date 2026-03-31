package ch.threema.app.pinlock

sealed class PinLockScreenEvent {
    data object Unlock : PinLockScreenEvent()
    data object Cancel : PinLockScreenEvent()
    data object NavigateToLauncher : PinLockScreenEvent()
}
