/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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
            val lastMultiDeviceGroupCheckTime = preferenceService.lastMultiDeviceGroupCheckTimestamp
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
