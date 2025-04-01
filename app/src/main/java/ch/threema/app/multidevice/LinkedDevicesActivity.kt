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

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.multidevice.wizard.LinkNewDeviceWizardActivity
import ch.threema.app.ui.EmptyRecyclerView
import ch.threema.app.ui.SilentSwitchCompat
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.d2m.socket.D2mSocketCloseReason
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.launch

private val logger = LoggingUtil.getThreemaLogger("LinkedDevicesActivity")

class LinkedDevicesActivity : ThreemaToolbarActivity() {
    private companion object {
        const val PERMISSION_REQUEST_CAMERA = 1
    }

    private val viewModel: LinkedDevicesViewModel by viewModels()

    private lateinit var devicesList: EmptyRecyclerView
    private lateinit var devicesAdapter: LinkedDevicesAdapter

    private lateinit var onOffButton: SilentSwitchCompat
    private lateinit var linkDeviceButton: ExtendedFloatingActionButton

    private var wizardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                logger.debug("Device linking success")
                viewModel.refreshLinkedDevices()
            } else {
                // TODO(ANDR-2758): proper error handling
                if (result.data?.getStringExtra(LinkNewDeviceWizardActivity.ACTIVITY_RESULT_EXTRA_FAILURE_REASON) != null) {
                    logger.debug("Device linking failed")
                } else {
                    logger.debug("Device linking cancelled (not started)")
                }
            }
        }

    override fun getLayoutResource(): Int = R.layout.activity_linked_devices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ConfigUtils.isMultiDeviceEnabled() && !serviceManager.multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Leave activity: MD is not enabled")
            finish()
            return
        }

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.md_linked_devices)
        }

        onOffButton = findViewById(R.id.on_off_button)
        onOffButton.setOnOffLabel(findViewById(R.id.on_off_button_text))
        onOffButton.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setMultiDeviceState(isChecked)
        }

        linkDeviceButton = findViewById(R.id.link_device_button)
        linkDeviceButton.setOnClickListener { initiateLinking() }
        // TODO(ANDR-2717): Remove
        linkDeviceButton.setOnLongClickListener {
            viewModel.dropOtherDevices()
            true
        }

        initDevicesList()

        startObservers()
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLinkingWizard()
            } else if (!this.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                ConfigUtils.showPermissionRationale(
                    this,
                    findViewById(R.id.parent_layout),
                    R.string.permission_camera_qr_required
                )
            }
        }
    }

    private fun initDevicesList() {
        val layoutManager = LinearLayoutManager(this)
        devicesList = findViewById(R.id.devices_list)
        devicesList.setHasFixedSize(true)
        devicesList.layoutManager = layoutManager
        devicesList.itemAnimator = DefaultItemAnimator()
        devicesList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (layoutManager.findFirstVisibleItemPosition() == 0) {
                    linkDeviceButton.extend()
                } else {
                    linkDeviceButton.shrink()
                }
            }
        })

        val emptyTextView = findViewById<TextView>(R.id.empty_text)
        devicesList.emptyView = emptyTextView
        devicesAdapter = LinkedDevicesAdapter()
        devicesList.adapter = devicesAdapter

    }

    private fun initiateLinking() {
        logger.debug("Initiate linking")
        if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
            startLinkingWizard()
        }
    }

    private fun startLinkingWizard() {
        logger.info("Start linking wizard")
        wizardLauncher.launch(Intent(this, LinkNewDeviceWizardActivity::class.java))
    }

    private fun startObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isMdActive.collect(::setMdEnabled) }
                launch { viewModel.linkedDevices.collect(::updateLinkedDevices) }
                launch { viewModel.latestCloseReason.collect(::updateLatestCloseReason) }
            }
        }
    }

    private fun setMdEnabled(enabled: Boolean) {
        onOffButton.isEnabled = enabled || ConfigUtils.isMultiDeviceEnabled()
        onOffButton.setCheckedSilent(enabled)
        linkDeviceButton.isEnabled = enabled
    }

    private fun updateLinkedDevices(linkedDevices: List<String>) {
        devicesAdapter.setDevices(linkedDevices)
    }

    // TODO(ANDR-2604): Remove
    private fun updateLatestCloseReason(reason: D2mSocketCloseReason?) {
        val latestCloseCode: TextView = findViewById(R.id.latest_close_code)
        if (reason != null) {
            latestCloseCode.text = reason.closeCode.toString()
            latestCloseCode.visibility = View.VISIBLE
        } else {
            latestCloseCode.visibility = View.GONE
        }
    }
}
