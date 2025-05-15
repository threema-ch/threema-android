/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.multidevice.wizard

import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ch.threema.app.R
import ch.threema.app.ThreemaApplication.requireServiceManager
import ch.threema.app.activities.StateFlowViewModel
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.linking.DeviceLinkingInvalidQrCodeException
import ch.threema.app.multidevice.linking.DeviceLinkingScannedWebQrCodeException
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.multidevice.linking.DeviceLinkingUnsupportedProtocolException
import ch.threema.app.multidevice.wizard.steps.LinkNewDeviceLinkingProgressFragment
import ch.threema.app.multidevice.wizard.steps.LinkNewDeviceResultFragment
import ch.threema.app.multidevice.wizard.steps.LinkNewDeviceVerifyFragment
import ch.threema.app.tasks.TaskCreator
import ch.threema.base.utils.LoggingUtil
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = LoggingUtil.getThreemaLogger("LinkNewDeviceWizardViewModel")

class LinkNewDeviceWizardViewModel : StateFlowViewModel() {
    private val serviceManager: ServiceManager by lazy { requireServiceManager() }
    private val multiDeviceManager: MultiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val taskCreator: TaskCreator by lazy { serviceManager.taskCreator }

    private var linkingJob: Job? = null
    private var sendDataJob: Job? = null
    private var continueOnNewDeviceJob: Job? = null

    private val mutableLinkingProgressStringRes = MutableLiveData<Int>()
    val linkingProgressStringRes: LiveData<Int> get() = mutableLinkingProgressStringRes

    private val _onUserRequestedCancel = MutableSharedFlow<Unit>()
    val onUserRequestedCancel = _onUserRequestedCancel.asSharedFlow()

    private val mutableCurrentFragment = MutableLiveData<Fragment>()
    val currentFragment: LiveData<Fragment> get() = mutableCurrentFragment

    private val mutableNextFragment = MutableLiveData<Class<out Fragment>?>()
    val nextFragment: LiveData<Class<out Fragment>?> get() = mutableNextFragment

    var qrScanResult: String? = null

    lateinit var deviceLinkingStatusConnected: DeviceLinkingStatus.Connected

    private val _linkingResult: MutableStateFlow<LinkingResult?> = MutableStateFlow(null)
    val linkingResult: StateFlow<LinkingResult?> = _linkingResult.stateInViewModel(null)

    fun setCurrentFragment(fragment: Fragment) {
        mutableCurrentFragment.value = fragment
    }

    fun switchToFragment(newFragmentClass: Class<out Fragment>?) {
        mutableNextFragment.value = newFragmentClass
    }

    fun cancel() {
        viewModelScope.launch {
            _onUserRequestedCancel.emit(Unit)
        }
    }

    fun linkDevice() {
        if (linkingJob?.isActive == true) {
            return
        }
        val deviceJoinUri: String = qrScanResult ?: run {
            logger.error("Missing qr code scan result to start linking")
            return
        }
        linkingJob = CoroutineScope(Dispatchers.Default).launch {
            multiDeviceManager.linkDevice(
                serviceManager = serviceManager,
                deviceJoinOfferUri = deviceJoinUri,
                taskCreator = taskCreator,
            ).collect(::handleLinkingStatus)
        }
    }

    private suspend fun handleLinkingStatus(deviceLinkingStatus: DeviceLinkingStatus) {
        withContext(Dispatchers.Main) {
            try {
                when (deviceLinkingStatus) {
                    is DeviceLinkingStatus.Connected -> {
                        deviceLinkingStatusConnected = deviceLinkingStatus
                        switchToFragment(LinkNewDeviceVerifyFragment::class.java)
                    }

                    is DeviceLinkingStatus.Completed -> {
                        cancelAllJobs()
                        _linkingResult.value = LinkingResult.Success
                        switchToFragment(LinkNewDeviceResultFragment::class.java)
                    }

                    is DeviceLinkingStatus.Failed -> {
                        cancelAllJobs()
                        val linkingResult = when (deviceLinkingStatus.throwable) {
                            is DeviceLinkingInvalidQrCodeException -> LinkingResult.Failure.UnknownQrCode
                            is DeviceLinkingScannedWebQrCodeException -> LinkingResult.Failure.ThreemaWebQrCode
                            is DeviceLinkingUnsupportedProtocolException -> LinkingResult.Failure.OldRendezvousProtocolVersion
                            is UnknownHostException -> LinkingResult.Failure.GenericNetwork
                            else -> LinkingResult.Failure.Generic
                        }
                        showResultFailure(linkingResult)
                    }
                }
            } catch (e: Exception) {
                showResultFailure(LinkingResult.Failure.Unexpected)
            }
        }
    }

    fun cancelAllJobs() {
        if (linkingJob?.isActive == true) {
            linkingJob?.cancel()
        }
        if (sendDataJob?.isActive == true) {
            sendDataJob?.cancel()
        }
        if (continueOnNewDeviceJob?.isActive == true) {
            continueOnNewDeviceJob?.cancel()
        }
    }

    /**
     * Show linking progress
     */
    @UiThread
    fun showLinkingProgress() {
        switchToFragment(LinkNewDeviceLinkingProgressFragment::class.java)

        sendDataJob = CoroutineScope(Dispatchers.IO).launch {
            delay(5000)
            withContext(Dispatchers.Main) {
                mutableLinkingProgressStringRes.value = R.string.sending_data
                continueOnNewDevice()
            }
        }
    }

    private fun continueOnNewDevice() {
        continueOnNewDeviceJob = CoroutineScope(Dispatchers.IO).launch {
            delay(10000)
            withContext(Dispatchers.Main) {
                mutableLinkingProgressStringRes.value = R.string.continue_on_new_device
            }
        }
    }

    fun showResultFailure(failure: LinkingResult.Failure) {
        if (linkingResult.value == null) {
            _linkingResult.value = failure
            switchToFragment(LinkNewDeviceResultFragment::class.java)
        }
    }
}
