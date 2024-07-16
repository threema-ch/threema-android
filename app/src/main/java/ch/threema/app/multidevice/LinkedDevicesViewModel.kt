/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

package ch.threema.app.multidevice

import androidx.annotation.AnyThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.ThreemaApplication.requireServiceManager
import ch.threema.app.multidevice.linking.DeviceJoinDataCollector
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinkedDevicesViewModel : ViewModel() {

    private val _isMdActive = MutableStateFlow(false)
    val isMdActive: Flow<Boolean> = _isMdActive.asStateFlow()

    private val _linkedDevices = MutableStateFlow<List<String>>(listOf())
    val linkedDevices: Flow<List<String>> = _linkedDevices.asStateFlow()

    // TODO(ANDR-2604): Remove
    private val _latestCloseReason = MutableSharedFlow<D2mSocketCloseReason?>()
    val latestCloseReason: Flow<D2mSocketCloseReason?> = _latestCloseReason.asSharedFlow()

    private val mdManager: MultiDeviceManager by lazy { requireServiceManager().multiDeviceManager }

    init {
        emitStates()
        collectLatestCloseReason()
    }

    @AnyThread
    fun linkDevice(deviceJoinOfferUri: String, deviceJoinDataCollector: DeviceJoinDataCollector) {
        CoroutineScope(Dispatchers.Default).launch {
            mdManager.linkDevice(deviceJoinOfferUri, deviceJoinDataCollector)
            emitStates()
        }
    }

    @AnyThread
    fun setMultiDeviceState(active: Boolean) {
        if (active) {
            activateMultiDevice()
        } else {
            deactivateMultiDevice()
        }
    }

    @AnyThread
    private fun activateMultiDevice() {
        CoroutineScope(Dispatchers.Default).launch {
            val serviceManager = requireServiceManager()
            mdManager.activate(
                "Android Client", // TODO(ANDR-2487): Should be userselectable (and updateable)
                serviceManager.taskManager,
                serviceManager.contactService,
                serviceManager.userService,
                serviceManager.forwardSecurityMessageProcessor
            )
            emitStates()
        }
    }

    @AnyThread
    private fun deactivateMultiDevice() {
        CoroutineScope(Dispatchers.Default).launch {
            val serviceManager = requireServiceManager()
            // TODO(ANDR-2603): Maybe show a spinner while we are waiting for deactivation to complete
            mdManager.deactivate(
                serviceManager.taskManager,
                serviceManager.userService,
                serviceManager.forwardSecurityMessageProcessor
            )
            emitStates()
        }
    }

    @AnyThread
    private fun emitStates() {
        emitIsMdActive()
        emitLinkedDevices()
    }

    @AnyThread
    private fun emitIsMdActive() {
        viewModelScope.launch {
            _isMdActive.emit(withContext(Dispatchers.Default) { mdManager.isMultiDeviceActive })
        }
    }

    @AnyThread
    private fun emitLinkedDevices() {
        viewModelScope.launch {
            _linkedDevices.emit(withContext(Dispatchers.Default) { mdManager.linkedDevices })
        }
    }

    @AnyThread
    private fun collectLatestCloseReason() {
        viewModelScope.launch {
            mdManager.latestSocketCloseReason.collect(_latestCloseReason)
        }
    }
}
