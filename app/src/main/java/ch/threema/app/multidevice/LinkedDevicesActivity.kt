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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.listeners.PreferenceListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.multidevice.wizard.LinkNewDeviceWizardActivity
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.ui.DebouncedOnClickListener
import ch.threema.app.ui.EmptyRecyclerView
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.SpacingValues
import ch.threema.app.ui.applyDeviceInsetsAsMargin
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.HiddenChatUtil
import ch.threema.app.utils.linkifyWeb
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

private val logger = getThreemaLogger("LinkedDevicesActivity")

class LinkedDevicesActivity : ThreemaToolbarActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()

    private val viewModel: LinkedDevicesViewModel by viewModel()

    private val devicesAdapter: LinkedDevicesAdapter by lazy {
        LinkedDevicesAdapter(viewModel::onClickedDevice)
    }

    private lateinit var linkDeviceButton: ExtendedFloatingActionButton
    private lateinit var deviceListContainer: FrameLayout
    private lateinit var devicesListRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyHintContainer: FrameLayout
    private lateinit var emptyHintTextView: TextView

    private val linkingWizardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        ::onLinkingWizardResult,
    )

    private val onPreferenceChangedListener = PreferenceListener { key, value ->
        viewModel.onPreferenceChanged(key, value)
    }

    override fun getLayoutResource(): Int = R.layout.activity_linked_devices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ConfigUtils.isMultiDeviceEnabled(this) && !multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Leave activity: MD is restricted by mdm and not active")
            finish()
            return
        }

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.md_linked_devices)
        }

        linkDeviceButton = findViewById(R.id.link_device_button)
        linkDeviceButton.setOnClickListener(
            object : DebouncedOnClickListener(1000L) {
                override fun onDebouncedClick(v: View?) {
                    logger.info("Link device button clicked")
                    if (!ConfigUtils.isMultiDeviceEnabled(this@LinkedDevicesActivity)) {
                        logger.warn("MD disabled, ignoring link button")
                        viewModel.updateLinkDeviceButtonEnabled()
                        return
                    }
                    if (ConfigUtils.hasProtection(preferenceService)) {
                        HiddenChatUtil.launchLockCheckDialog(
                            this@LinkedDevicesActivity,
                            preferenceService,
                        )
                    } else {
                        initiateLinking()
                    }
                }
            },
        )

        devicesListRefreshLayout = findViewById(R.id.devices_list_refresh)
        devicesListRefreshLayout.setColorSchemeColors(
            ConfigUtils.getColorFromAttribute(this, R.attr.colorPrimary),
        )
        deviceListContainer = findViewById(R.id.device_list_container)
        emptyHintContainer = findViewById(R.id.empty_text_container)
        emptyHintTextView = findViewById(R.id.empty_text)
        initDevicesList()

        startObservers()
        startPreferenceListener()
    }

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()
        findViewById<EmptyRecyclerView>(R.id.devices_list).applyDeviceInsetsAsPadding(
            insetSides = InsetSides.lbr(),
            ownPadding = SpacingValues(
                top = R.dimen.grid_unit_x1_5,
                bottom = R.dimen.grid_unit_x10,
            ),
        )
        findViewById<TextView>(R.id.empty_text).applyDeviceInsetsAsPadding(
            insetSides = InsetSides(top = false, right = true, bottom = true, left = true),
            ownPadding = SpacingValues.symmetric(
                vertical = R.dimen.grid_unit_x10,
                horizontal = R.dimen.grid_unit_x2,
            ),
        )
        findViewById<ExtendedFloatingActionButton>(R.id.link_device_button).applyDeviceInsetsAsMargin(
            insetSides = InsetSides.all(),
            ownMargin = SpacingValues.all(R.dimen.grid_unit_x2),
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateLinkDeviceButtonEnabled()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ACTIVITY_ID_CHECK_LOCK && resultCode == RESULT_OK) {
            initiateLinking()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLinkingWizard()
            } else if (!this.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                ConfigUtils.showPermissionRationale(
                    this,
                    findViewById(R.id.parent_layout),
                    R.string.permission_camera_qr_required,
                )
            }
        }
    }

    private fun initDevicesList() {
        val layoutManager = LinearLayoutManager(this)
        val devicesListRv: EmptyRecyclerView = findViewById(R.id.devices_list)
        devicesListRv.setHasFixedSize(true)
        devicesListRv.layoutManager = layoutManager
        devicesListRv.itemAnimator = DefaultItemAnimator()

        val desktopClientFlavour = BuildFlavor.current.desktopClientFlavor
        emptyHintTextView.text = getString(R.string.md_linked_devices_empty)
            .linkifyWeb(
                url = getString(desktopClientFlavour.downloadLink),
                onClickLink = { downloadLinkUri ->
                    if (downloadLinkUri.host == "three.ma") {
                        startActivity(Intent(Intent.ACTION_VIEW, downloadLinkUri))
                    }
                },
            )
        emptyHintTextView.movementMethod = LinkMovementMethod.getInstance()

        devicesListRv.adapter = devicesAdapter

        devicesListRefreshLayout.setOnRefreshListener {
            viewModel.updateDeviceList()
        }
    }

    private fun initiateLinking() {
        logger.debug("Initiate linking")
        if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
            startLinkingWizard()
        }
    }

    private fun startLinkingWizard() {
        if (!ConfigUtils.isMultiDeviceEnabled(this)) {
            logger.warn("MD disabled, not start linking wizard")
            viewModel.updateLinkDeviceButtonEnabled()
            return
        }
        logger.info("Start linking wizard")
        linkingWizardLauncher.launch(LinkNewDeviceWizardActivity.createIntent(this))
    }

    private fun startObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.viewState.collect(::setState) }
                launch { viewModel.isLoading.collect(::setIsLoading) }
                launch { viewModel.isLinkDeviceButtonEnabled.collect(::setIsLinkDeviceButtonEnabled) }
                launch { viewModel.onDropDeviceFailed.collect { showDropDeviceFailedDialog() } }
                launch { viewModel.onShowDeviceDetailDialog.collect(::showDeviceDetailDialog) }
                viewModel.initState()
            }
        }
    }

    private fun startPreferenceListener() {
        ListenerManager.preferenceListeners.add(onPreferenceChangedListener)
    }

    private fun setIsLoading(isLoading: Boolean) {
        devicesListRefreshLayout.isRefreshing = isLoading
    }

    private fun setIsLinkDeviceButtonEnabled(isEnabled: Boolean) {
        linkDeviceButton.isEnabled = isEnabled
    }

    private fun setState(state: LinkedDevicesUiState) {
        emptyHintContainer.isVisible = state is LinkedDevicesUiState.NoDevices
        if (state is LinkedDevicesUiState.Devices) {
            devicesAdapter.submitList(state.deviceListItems)
        } else if (state is LinkedDevicesUiState.NoDevices) {
            devicesAdapter.submitList(emptyList())
        }
    }

    private fun onLinkingWizardResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            logger.debug("Device linking success")
            viewModel.updateDeviceList()
        }
    }

    private fun showDeviceDetailDialog(deviceInfo: LinkedDeviceInfoUiModel) {
        val dropDeviceDialogView: View = LayoutInflater.from(this)
            .inflate(R.layout.dialog_drop_linked_device, null, false)

        val dropDeviceDialog: AlertDialog = MaterialAlertDialogBuilder(this)
            .setView(dropDeviceDialogView)
            .show()

        val platformIconImageView = dropDeviceDialogView.findViewById<ImageView>(R.id.platform_icon_Iv)
        platformIconImageView.setImageResource(deviceInfo.getPlatformDrawable())
        platformIconImageView.imageTintList = ColorStateList.valueOf(
            ConfigUtils.getColorFromAttribute(this, deviceInfo.getListItemStrokeColor()),
        )

        val deviceLabelTextView = dropDeviceDialogView.findViewById<TextView>(R.id.device_label_Tv)
        deviceLabelTextView.text = deviceInfo.getLabelTextOrDefault(this)

        val deviceVersionTextView = dropDeviceDialogView.findViewById<TextView>(R.id.device_version_Tv)
        deviceVersionTextView.text = deviceInfo.getPlatformDetailsTextOrDefault(this)

        val deviceTimestampTextView = dropDeviceDialogView.findViewById<TextView>(R.id.device_timestamp_Tv)
        deviceTimestampTextView.text = deviceInfo.getFormattedTimeInfo(this)

        val dropDeviceButton = dropDeviceDialogView.findViewById<MaterialButton>(R.id.drop_device_Btn)
        dropDeviceButton.setOnClickListener {
            viewModel.dropDevice(deviceId = deviceInfo.deviceId)
            dropDeviceDialog.dismiss()
        }

        val closeButton = dropDeviceDialogView.findViewById<MaterialButton>(R.id.close_dialog_Btn)
        closeButton.setOnClickListener {
            dropDeviceDialog.dismiss()
        }
    }

    private fun showDropDeviceFailedDialog() {
        MaterialAlertDialogBuilder(this).apply {
            setTitle(R.string.md_drop_device_failed_dialog_title)
            setMessage(R.string.md_drop_device_failed_dialog_message)
            setPositiveButton(R.string.md_drop_device_failed_dialog_button_close) { _, _ -> }
        }.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        ListenerManager.preferenceListeners.remove(onPreferenceChangedListener)
    }

    private companion object {
        const val PERMISSION_REQUEST_CAMERA = 1
    }
}
