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
