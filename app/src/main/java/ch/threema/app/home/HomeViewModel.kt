package ch.threema.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.tasks.TaskCreator
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeViewModel(
    private val preferenceService: PreferenceService,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskCreator: TaskCreator,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private var checkMultiDeviceGroupJob: Job? = null

    fun checkMultiDeviceGroup() {
        if (checkMultiDeviceGroupJob?.isActive == true) {
            return
        }
        checkMultiDeviceGroupJob = viewModelScope.launch {
            val lastMultiDeviceGroupCheckTime = preferenceService.getLastMultiDeviceGroupCheckTimestamp()
            if (lastMultiDeviceGroupCheckTime != null && timeProvider.get() - lastMultiDeviceGroupCheckTime < minimumTimeBetweenDeviceGroupCheck) {
                return@launch
            }

            preferenceService.setLastMultiDeviceGroupCheckTimestamp(timeProvider.get())

            if (multiDeviceManager.isMultiDeviceActive) {
                // Schedule a persistent task to check whether multi device needs to be disabled
                taskCreator.scheduleDeactivateMultiDeviceIfAloneTask()
            }
        }
    }

    private companion object {
        val minimumTimeBetweenDeviceGroupCheck = 60.minutes
    }
}
