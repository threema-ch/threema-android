/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

package ch.threema.app.preference

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.preference.CheckBoxPreference
import androidx.preference.DropDownPreference
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.services.VoipStateService
import ch.threema.app.voip.util.VoipUtil
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("SettingsCallsFragment")

@Suppress("unused")
class SettingsCallsFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private var fragmentView: View? = null
    private var enableCallReject: CheckBoxPreference? = null
    private lateinit var callEnable: CheckBoxPreference
    private lateinit var groupCallsEnable: CheckBoxPreference
    private var groupCallManager: GroupCallManager? = null
    private var voipStateService: VoipStateService? = null
    private var preferenceService: PreferenceService? = null

    override fun initializePreferences() {
        super.initializePreferences()

        try {
            val serviceManager = ThreemaApplication.requireServiceManager()
            groupCallManager = serviceManager.groupCallManager
            voipStateService = serviceManager.voipStateService
            preferenceService = serviceManager.preferenceService
        } catch (e: Exception) {
            logger.error("Could not init dependencies", e)
        }

        initCallPrefListeners()

        initWorkRestrictions()

        initEnableCallRejectPref()
    }

    override fun getPreferenceTitleResource(): Int = R.string.prefs_title_calls

    override fun getPreferenceResource(): Int = R.xml.preference_calls

    private fun initCallPrefListeners() {
        val preferenceService = preferenceService ?: run {
            logger.error("Cannot initialize preference listeners")
            return
        }

        callEnable = getPref(preferenceService.o2oCallPolicySetting.preferenceKey)
        callEnable.onChange<Boolean> { enabled ->
            if (!enabled && voipStateService?.callState?.isIdle != true) {
                VoipUtil.sendOneToOneCallHangupCommand(requireActivity())
            }
        }

        groupCallsEnable = getPref(preferenceService.groupCallPolicySetting.preferenceKey)
        groupCallsEnable.onChange<Boolean> { enabled ->
            if (!enabled) {
                groupCallManager?.abortCurrentCall()
            }
            ListenerManager.conversationListeners.handle { it.onModifiedAll() }
        }
    }

    private fun initWorkRestrictions() {
        if (!ConfigUtils.isWorkRestricted()) {
            return
        }

        val preferenceService = preferenceService ?: run {
            logger.error("Cannot init work restrictions as preference service is null")
            return
        }

        val disableCalls =
            AppRestrictionUtil.getBooleanRestriction(resources.getString(R.string.restriction__disable_calls))
        var disableVideoCalls =
            AppRestrictionUtil.getBooleanRestriction(resources.getString(R.string.restriction__disable_video_calls))
        var disableGroupCalls =
            AppRestrictionUtil.getBooleanRestriction(resources.getString(R.string.restriction__disable_group_calls))

        if (disableCalls != null) {
            // admin does not want user to tamper with call setting
            callEnable.isEnabled = false
            callEnable.isSelectable = false
            callEnable.isChecked = !disableCalls
            if (disableCalls) {
                // disabled calls also disable video and group calls
                disableVideoCalls = true
                disableGroupCalls = true
            } else {
                // remove dependency to allow user to manipulate advanced call settings if admin enabled calls
                val forceTurn: CheckBoxPreference = getPref(preferenceService.o2oCallConnectionPolicySetting.preferenceKey)
                val rejectCalls: CheckBoxPreference =
                    getPref(R.string.preferences__voip_reject_mobile_calls)

                forceTurn.dependency = null
                rejectCalls.dependency = null
            }
        }

        val videoCallEnable: CheckBoxPreference = getPref(preferenceService.o2oCallVideoPolicySetting.preferenceKey)
        if (disableVideoCalls != null) {
            // admin does not want user to tamper with video call setting
            videoCallEnable.isEnabled = false
            videoCallEnable.isSelectable = false
            videoCallEnable.isChecked = !disableVideoCalls

            if (!disableVideoCalls) {
                val videoCallProfile: DropDownPreference =
                    getPref(R.string.preferences__voip_video_profile)
                videoCallProfile.dependency = null
                videoCallProfile.isEnabled = true
                videoCallProfile.isSelectable = true
            }
        } else if (disableCalls == false) {
            // remove dependency to allow user to manipulate video setting if calls are force-enabled
            videoCallEnable.dependency = null
        }

        if (disableGroupCalls != null) {
            // admin does not want user to tamper with group call setting
            groupCallsEnable.isEnabled = false
            groupCallsEnable.isSelectable = false
            groupCallsEnable.isChecked = !disableGroupCalls
        }
    }

    private fun initEnableCallRejectPref() {
        enableCallReject = getPref(R.string.preferences__voip_reject_mobile_calls)
        enableCallReject?.setOnPreferenceChangeListener { _, newValue ->
            val newCheckedValue = newValue == true
            if (newCheckedValue) {
                ConfigUtils.requestPhonePermissions(
                    requireActivity(),
                    this@SettingsCallsFragment,
                    PERMISSION_REQUEST_READ_PHONE_STATE,
                )
            } else {
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        this.fragmentView = view
        super.onViewCreated(view, savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray,
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_READ_PHONE_STATE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                this.enableCallReject?.isChecked = true
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                ConfigUtils.showPermissionRationale(
                    context,
                    fragmentView,
                    R.string.permission_phone_required,
                )
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_READ_PHONE_STATE = 3
    }
}
