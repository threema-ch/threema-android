package ch.threema.app.dialogs.loadingtimeout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoadingWithTimeoutDialogViewModel : ViewModel() {

    private val _timeoutReached: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val timeoutReached: StateFlow<Boolean> = _timeoutReached.asStateFlow()

    private var awaitTimeoutJob: Job? = null

    fun awaitTimeout(timeout: Duration) {
        if (awaitTimeoutJob?.isActive == true) {
            return
        }
        awaitTimeoutJob = viewModelScope.launch {
            _timeoutReached.value = false
            delay(timeout)
            if (isActive) {
                _timeoutReached.value = true
            }
        }
    }
}
