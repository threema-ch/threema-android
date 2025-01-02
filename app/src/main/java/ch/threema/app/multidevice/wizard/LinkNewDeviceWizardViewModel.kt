/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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
import androidx.lifecycle.ViewModel
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.multidevice.linking.Completed
import ch.threema.app.multidevice.linking.Connected
import ch.threema.app.multidevice.linking.DeviceLinkingStatus
import ch.threema.app.multidevice.linking.Failed
import ch.threema.app.tasks.TaskCreator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinkNewDeviceWizardViewModel : ViewModel() {
    private val multiDeviceManager: MultiDeviceManager by lazy { ThreemaApplication.requireServiceManager().multiDeviceManager }
    private val taskCreator: TaskCreator by lazy { ThreemaApplication.requireServiceManager().taskCreator }

    private var linkingJob: Job? = null
    private var sendDataJob: Job? = null
    private var continueOnNewDeviceJob: Job? = null

    private val mutableCurrentFragment = MutableLiveData<Fragment>()
    private val mutableNextFragment = MutableLiveData<Class<out Fragment>?>()
    private val mutableSendingDataText = MutableLiveData<Int>()

    val linkingProgressStringRes: LiveData<Int> get() = mutableSendingDataText
    val currentFragment: LiveData<Fragment> get() = mutableCurrentFragment
    val nextFragment: LiveData<Class<out Fragment>?> get() = mutableNextFragment

    var qrScanResult: String? = null
    var failureReason: String? = null
    var success: Boolean = false
    lateinit var connected: Connected

    fun setCurrentFragment(fragment: Fragment) {
        mutableCurrentFragment.value = fragment
    }

    fun switchToFragment(newFragmentClass : Class<out Fragment>?) {
        mutableNextFragment.value = newFragmentClass
    }

    fun linkDevice() {
        linkingJob = CoroutineScope(Dispatchers.Default).launch {
            multiDeviceManager.linkDevice(qrScanResult!!, taskCreator).collect {
                handleLinkingStatus(it)
            }
        }
    }

    private suspend fun handleLinkingStatus(deviceLinkingStatus: DeviceLinkingStatus) {
        withContext(Dispatchers.Main) {
            try {
                when (deviceLinkingStatus) {
                    is Connected -> {
                        connected = deviceLinkingStatus
                        try {
                            switchToFragment(LinkNewDeviceVerifyFragment::class.java)
                        } catch (e: Exception) {
                            showFailure("Unable to show challenge")
                        }
                    }

                    is Completed -> {
                        showSuccess()
                    }

                    is Failed -> {
                        showFailure(ThreemaApplication.getAppContext().getString(R.string.connection_error))
                    }
                }
            } catch (e: Exception) {
                showFailure("Exception while linking device: " + e.message)
            }
        }
    }

    fun cancelAllJobs() {
        linkingJob?.cancel()
        sendDataJob?.cancel()
        continueOnNewDeviceJob?.cancel()
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
                mutableSendingDataText.value = R.string.sending_data
                continueOnNewDevice()
            }
        }
    }

    private fun continueOnNewDevice() {
        continueOnNewDeviceJob = CoroutineScope(Dispatchers.IO).launch {
            delay(10000)
            withContext(Dispatchers.Main) {
                mutableSendingDataText.value = R.string.continue_on_new_device
            }
        }
    }

    /**
     * Show success screen
     */
    private fun showSuccess() {
        cancelAllJobs()
        success = true
        switchToFragment(LinkNewDeviceSuccessFragment::class.java)
    }

    /**
     * Show failure screen
     */
    fun showFailure(reason: String?) {
        failureReason = reason
        switchToFragment(LinkNewDeviceFailureFragment::class.java)
    }
}
