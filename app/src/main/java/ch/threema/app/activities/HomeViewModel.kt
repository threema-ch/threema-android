/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.managers.ServiceManager
import ch.threema.common.TimeProvider
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private companion object {
        val minimumTimeBetweenDeviceGroupCheck: Duration = 60.minutes
    }

    private var checkMultiDeviceGroupJob: Job? = null

    @JvmOverloads
    fun checkMultiDeviceGroup(
        serviceManager: ServiceManager,
        timeProvider: TimeProvider = TimeProvider.default,
    ) {
        if (checkMultiDeviceGroupJob?.isActive == true) {
            return
        }
        checkMultiDeviceGroupJob = viewModelScope.launch {
            val lastMultiDeviceGroupCheckTime = serviceManager.preferenceService.lastMultiDeviceGroupCheckTimestamp ?: Instant.MIN
            val timeDifferenceInSeconds: Long = timeProvider.get().epochSecond - lastMultiDeviceGroupCheckTime.epochSecond
            if (timeDifferenceInSeconds < minimumTimeBetweenDeviceGroupCheck.inWholeSeconds) {
                return@launch
            }

            serviceManager.preferenceService.setLastMultiDeviceGroupCheckTimestamp(timeProvider.get())

            if (serviceManager.multiDeviceManager.isMultiDeviceActive) {
                // Schedule a persistent task to check whether multi device needs to be disabled
                serviceManager.taskCreator.scheduleDeactivateMultiDeviceIfAloneTask()
            }
        }
    }
}
