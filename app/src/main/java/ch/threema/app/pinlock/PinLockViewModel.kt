package ch.threema.app.pinlock

import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LockAppService
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import ch.threema.common.plus
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

class PinLockViewModel(
    private val lockAppService: LockAppService,
    private val preferenceService: PreferenceService,
    private val timeProvider: TimeProvider,
    private val isCheckOnly: Boolean,
) : BaseViewModel<PinLockViewState, PinLockScreenEvent>() {

    private var failedAttempts: Int
        get() = preferenceService.getLockoutAttempts()
        set(value) {
            preferenceService.setLockoutAttempts(value)
        }
    private var lockoutDeadline: Instant?
        get() = (preferenceService.getLockoutDeadline())
            ?.let { deadline ->
                val now = timeProvider.get()
                deadline
                    .takeIf { it > now }
                    ?.coerceAtMost(now + LOCKOUT_TIMEOUT)
            }
        set(value) {
            preferenceService.setLockoutDeadline(value)
        }
    private var errorResetJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }
    private var countdownJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    override fun initialize() = runInitialization { PinLockViewState() }

    fun onChangedPin(pin: String) = runAction {
        if (pin.length > AppConstants.MAX_PIN_LENGTH) {
            endAction()
        }
        updateViewState {
            copy(pin = pin)
        }
    }

    fun onClickSubmit() = runAction {
        val pin = currentViewState.pin
        if (pin.isEmpty()) {
            endAction()
        }
        if (lockAppService.unlock(pin)) {
            failedAttempts = 0
            lockoutDeadline = null
            emitEvent(PinLockScreenEvent.Unlock)
        } else {
            failedAttempts++

            if (failedAttempts > MAX_FAILED_ATTEMPTS) {
                lockoutDeadline = timeProvider.get() + LOCKOUT_TIMEOUT
                handleAttemptLockout()
            } else {
                showError(error = ResourceIdString(R.string.pinentry_wrong_pin), duration = ERROR_MESSAGE_TIMEOUT)
            }
        }
    }

    private suspend fun ViewModelActionScope<PinLockViewState, PinLockScreenEvent>.showError(error: ResolvableString, duration: Duration? = null) {
        updateViewState {
            copy(
                pin = "",
                error = error,
            )
        }
        errorResetJob = null
        duration?.let {
            errorResetJob = runAction {
                delay(duration)
                updateViewState {
                    copy(error = null)
                }
            }
        }
    }

    private suspend fun ViewModelActionScope<PinLockViewState, PinLockScreenEvent>.handleAttemptLockout() {
        val deadline = lockoutDeadline ?: timeProvider.get()
        updateViewState {
            copy(
                pinEntryEnabled = false,
            )
        }
        countdownJob = runAction {
            var seconds = (deadline - timeProvider.get()).inWholeSeconds
            while (seconds > 0) {
                showError(error = { context -> context.getString(R.string.too_many_incorrect_attempts, seconds.toString()) })
                delay(1.seconds)
                seconds--
            }

            updateViewState {
                copy(
                    pinEntryEnabled = true,
                    error = null,
                )
            }
            failedAttempts = 0
        }
    }

    fun onClickCancel() = runAction {
        cancel()
    }

    fun onPressBack() = runAction {
        cancel()
    }

    override suspend fun onActive() {
        runAction {
            if (!lockAppService.isLocked && !isCheckOnly) {
                cancel()
            }
            handleAttemptLockout()
        }

        try {
            awaitCancellation()
        } finally {
            countdownJob = null
        }
    }

    private suspend fun ViewModelBaseScope<PinLockScreenEvent>.cancel() {
        if (isCheckOnly) {
            emitEvent(PinLockScreenEvent.Cancel)
        } else {
            emitEvent(PinLockScreenEvent.NavigateToLauncher)
        }
    }

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 3
        private val ERROR_MESSAGE_TIMEOUT = 3.seconds
        private val LOCKOUT_TIMEOUT = 30.seconds
    }
}
