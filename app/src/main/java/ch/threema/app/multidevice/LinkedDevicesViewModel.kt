/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.AttrRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.lifecycle.viewModelScope
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.ThreemaApplication.Companion.requireServiceManager
import ch.threema.app.activities.StateFlowViewModel
import ch.threema.app.multidevice.unlinking.DropDeviceResult
import ch.threema.app.multidevice.unlinking.DropDevicesIntent
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.stores.PreferenceStoreInterface
import ch.threema.app.tasks.DropDevicesStepsTask
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.data.D2dMessage
import ch.threema.domain.protocol.connection.data.DeviceId
import ch.threema.domain.taskmanager.NetworkException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableList

private val logger = LoggingUtil.getThreemaLogger("LinkedDevicesViewModel")

class LinkedDevicesViewModel : StateFlowViewModel() {
    private val serviceManager by lazy { requireServiceManager() }
    private val mdManager: MultiDeviceManager by lazy { serviceManager.multiDeviceManager }
    private val taskCreator: TaskCreator by lazy { serviceManager.taskCreator }
    private val preferenceStore: PreferenceStoreInterface by lazy { serviceManager.preferenceStore }

    private val _state = MutableStateFlow<LinkedDevicesUiState>(LinkedDevicesUiState.Initial)
    val state: StateFlow<LinkedDevicesUiState> = _state.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val isMultiDeviceEnabled = MutableStateFlow(ConfigUtils.isMultiDeviceEnabled(ThreemaApplication.getAppContext()))

    val isLinkDeviceButtonEnabled: StateFlow<Boolean> =
        combine(isMultiDeviceEnabled, state, isLoading) { isMultiDeviceEnabled, state, isLoading ->
            isMultiDeviceEnabled &&
                !isLoading &&
                (state is LinkedDevicesUiState.NoDevices || (state is LinkedDevicesUiState.Devices && state.hasFreeSlotsInDeviceGroup))
        }
            .stateInViewModel(initialValue = false)

    private val _onDropDeviceFailed: MutableSharedFlow<Unit> = MutableSharedFlow()
    val onDropDeviceFailed: SharedFlow<Unit> = _onDropDeviceFailed.asSharedFlow()

    private val _onShowDeviceDetailDialog: MutableSharedFlow<LinkedDeviceInfoUiModel> = MutableSharedFlow()
    val onShowDeviceDetailDialog: SharedFlow<LinkedDeviceInfoUiModel> =
        _onShowDeviceDetailDialog.asSharedFlow()

    private var updateDeviceListJob: Job? = null
    private var dropDeviceJob: Job? = null

    fun initState() {
        if (mdManager.isMultiDeviceActive) {
            updateDeviceList()
        } else {
            _state.update { LinkedDevicesUiState.NoDevices }
        }
    }

    fun updateLinkDeviceButtonEnabled() {
        isMultiDeviceEnabled.value = ConfigUtils.isMultiDeviceEnabled(ThreemaApplication.getAppContext())
    }

    fun updateDeviceList() {
        if (updateDeviceListJob?.isActive == true) {
            return
        }
        _isLoading.update { true }
        updateDeviceListJob = viewModelScope.launch {
            val linkedDevices: List<LinkedDevice> = mdManager.loadLinkedDevices(taskCreator).getOrElse {
                emptyMap()
            }.toLinkedDevices(mdManager.propertiesProvider.get().keys)
            if (linkedDevices.isNotEmpty()) {
                emitDeviceListState(linkedDevices)
            } else {
                _state.update { LinkedDevicesUiState.NoDevices }
            }
        }.apply {
            invokeOnCompletion { throwable ->
                throwable?.let { logger.error("Exception when updating device list", it) }
                _isLoading.update { false }
            }
        }
    }

    private fun emitDeviceListState(linkedDevices: List<LinkedDevice>) {
        val sortedDevices = linkedDevices
            .map(LinkedDeviceInfoUiModel.Companion::fromModel)
            .sortedDescending()

        val maxDeviceSlots: Long = preferenceStore.getLong(PreferenceStore.PREFS_MD_MEDIATOR_MAX_SLOTS)
        val hasFreeSlotsInDeviceGroup: Boolean = (linkedDevices.size + 1) < maxDeviceSlots // + 1 because we need to count ourselves too
        val deviceListItems: List<LinkedDevicesAdapter.ListItem> =
            mutableListOf<LinkedDevicesAdapter.ListItem>().apply {
                if (!hasFreeSlotsInDeviceGroup) {
                    add(LinkedDevicesAdapter.ListItem.DeviceAmountWarning(maxDeviceSlots.toInt()))
                }
                addAll(sortedDevices.map(LinkedDevicesAdapter.ListItem::Device))
            }.toImmutableList()

        _state.value = LinkedDevicesUiState.Devices(
            maxDeviceSlots = maxDeviceSlots,
            hasFreeSlotsInDeviceGroup = hasFreeSlotsInDeviceGroup,
            deviceListItems = deviceListItems,
        )
    }

    fun dropDevice(deviceId: DeviceId) {
        if (dropDeviceJob?.isActive == true) {
            return
        }

        _isLoading.update { true }

        dropDeviceJob = viewModelScope.launch {
            val multiDeviceProperties = mdManager.propertiesProvider.get()
            val thisDeviceId = multiDeviceProperties.mediatorDeviceId
            check(thisDeviceId != deviceId) {
                "Cannot drop this device"
            }

            val dropDeviceResult = try {
                serviceManager.taskManager.schedule(
                    DropDevicesStepsTask(
                        intent = DropDevicesIntent.DropDevices(
                            deviceIdsToDrop = setOf(deviceId),
                            thisDeviceId = thisDeviceId,
                        ),
                        serviceManager = serviceManager,
                    ),
                ).await()
            } catch (e: NetworkException) {
                DropDeviceResult.Failure.Timeout
            }

            when (dropDeviceResult) {
                is DropDeviceResult.Success -> onDeviceDroppedSuccessfully(
                    dropDeviceResult.remainingLinkedDevices.toLinkedDevices(multiDeviceProperties.keys),
                )

                is DropDeviceResult.Failure -> _onDropDeviceFailed.emit(Unit)
            }
        }.apply {
            invokeOnCompletion { throwable ->
                throwable?.let { logger.error("Exception when dropping device", it) }
                _isLoading.update { false }
            }
        }
    }

    private suspend fun onDeviceDroppedSuccessfully(remainingLinkedDevices: List<LinkedDevice>) {
        if (remainingLinkedDevices.isNotEmpty()) {
            emitDeviceListState(remainingLinkedDevices)
        } else {
            _state.emit(LinkedDevicesUiState.NoDevices)
        }
    }

    fun onPreferenceChanged(key: String, value: Any?) {
        val currentState = _state.value
        if (key != PreferenceStore.PREFS_MD_MEDIATOR_MAX_SLOTS || currentState !is LinkedDevicesUiState.Devices) {
            return
        }
        val updatedMaxSlots: Long = (value as? Long?) ?: 0L
        _state.update {
            currentState.copy(maxDeviceSlots = updatedMaxSlots)
        }
    }

    fun onClickedDevice(linkedDeviceInfoUiModel: LinkedDeviceInfoUiModel) {
        if (!_isLoading.value) {
            viewModelScope.launch {
                _onShowDeviceDetailDialog.emit(linkedDeviceInfoUiModel)
            }
        }
    }
}

sealed interface LinkedDevicesUiState {
    data object Initial : LinkedDevicesUiState

    data class Devices(
        val maxDeviceSlots: Long,
        val hasFreeSlotsInDeviceGroup: Boolean,
        val deviceListItems: List<LinkedDevicesAdapter.ListItem>,
    ) : LinkedDevicesUiState

    data object NoDevices : LinkedDevicesUiState
}

/**
 *  UI model representation of [LinkedDevice] model.
 *  Split from model class to implement useful ui methods and sorting for device list.
 */
data class LinkedDeviceInfoUiModel(
    val deviceId: DeviceId,
    val label: String,
    val platform: D2dMessage.DeviceInfo.Platform,
    val platformDetails: String,
    val appVersion: String,
    val connectedSince: Long?,
    val lastDisconnectAt: Long?,
) : Comparable<LinkedDeviceInfoUiModel> {
    companion object {
        fun fromModel(linkedDevice: LinkedDevice) = LinkedDeviceInfoUiModel(
            deviceId = linkedDevice.deviceId,
            label = linkedDevice.label,
            platform = linkedDevice.platform,
            platformDetails = linkedDevice.platformDetails,
            appVersion = linkedDevice.appVersion,
            connectedSince = linkedDevice.connectedSince?.toLong(),
            lastDisconnectAt = linkedDevice.lastDisconnectAt?.toLong(),
        )
    }

    private val isValid: Boolean
        get() = platform != D2dMessage.DeviceInfo.Platform.UNSPECIFIED

    private val isCurrentlyActive: Boolean
        get() = connectedSince != null

    override fun compareTo(other: LinkedDeviceInfoUiModel): Int {
        return when {
            // try sort by connectedSince
            this.connectedSince != null && other.connectedSince == null -> 1
            this.connectedSince != null && other.connectedSince != null -> this.connectedSince.compareTo(
                other.connectedSince,
            )

            this.connectedSince == null && other.connectedSince != null -> -1
            // both connectedSince are not filled, sort by lastDisconnectAt
            this.lastDisconnectAt != null && other.lastDisconnectAt == null -> return 1
            this.lastDisconnectAt != null && other.lastDisconnectAt != null -> this.lastDisconnectAt.compareTo(
                other.lastDisconnectAt,
            )

            this.lastDisconnectAt == null && other.lastDisconnectAt != null -> -1
            else -> 0
        }
    }

    fun getLabelTextOrDefault(context: Context): String = when {
        isValid -> label
        else -> context.getString(R.string.md_device_invalid_label)
    }

    fun getPlatformDetailsTextOrDefault(context: Context): String = when {
        isValid -> context.getString(
            R.string.md_device_version_and_platform_info,
            appVersion,
            platformDetails,
        )

        else -> context.getString(R.string.md_device_invalid_hint)
    }

    fun getFormattedTimeInfo(context: Context): String? = when {
        isCurrentlyActive -> context.getString(R.string.md_device_currently_active)
        lastDisconnectAt != null -> context.getString(
            R.string.md_device_last_active_at,
            DateUtils.getRelativeDateTimeString(
                context,
                lastDisconnectAt.toLong(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL,
            ).toString(),
        )

        else -> null
    }

    @DimenRes
    fun getListItemStrokeWidth(): Int = when {
        isCurrentlyActive -> R.dimen.md_device_stroke_width_active
        else -> R.dimen.md_device_stroke_width_inactive
    }

    @AttrRes
    fun getListItemStrokeColor(): Int = when {
        !isValid -> R.attr.colorError
        isCurrentlyActive -> R.attr.colorPrimary
        else -> R.attr.colorOutline
    }

    @DrawableRes
    fun getPlatformDrawable(): Int = when (platform) {
        D2dMessage.DeviceInfo.Platform.ANDROID -> R.drawable.ic_platform_android
        D2dMessage.DeviceInfo.Platform.IOS -> R.drawable.ic_platform_ios
        D2dMessage.DeviceInfo.Platform.DESKTOP -> R.drawable.ic_platform_desktop
        D2dMessage.DeviceInfo.Platform.WEB -> R.drawable.ic_platform_web
        D2dMessage.DeviceInfo.Platform.UNSPECIFIED -> R.drawable.ic_platform_unknown
    }
}
