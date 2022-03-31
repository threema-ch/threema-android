/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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
import androidx.preference.PreferenceCategory
import ch.threema.app.R
import ch.threema.app.utils.AppRestrictionUtil
import ch.threema.app.utils.ConfigUtils

class SettingsCallsFragment : ThreemaPreferenceFragment() {

    private var fragmentView: View? = null
    private var enableCallReject: CheckBoxPreference? = null

    override fun initializePreferences() {
        super.initializePreferences()

        initWorkRestrictions()

        initEnableCallRejectPref()
    }

    override fun getPreferenceResource(): Int = R.xml.preference_calls

    private fun initWorkRestrictions() {
        if (ConfigUtils.isWorkRestricted()) {
            val callEnable: CheckBoxPreference = getPref(R.string.preferences__voip_enable)
            val disableCalls = AppRestrictionUtil.getBooleanRestriction(resources.getString(R.string.restriction__disable_calls))
            var disableVideoCalls = AppRestrictionUtil.getBooleanRestriction(resources.getString(R.string.restriction__disable_video_calls))
            if (disableCalls != null) {
                // admin does not want user to tamper with call setting
                callEnable.isEnabled = false
                callEnable.isSelectable = false
                callEnable.isChecked = !disableCalls
                if (disableCalls) {
                    // disabled calls also disable video calls
                    disableVideoCalls = true
                }
            }

            if (disableVideoCalls != null) {
                // admin does not want user to tamper with video call setting
                val videoCallEnable: CheckBoxPreference = getPref(R.string.preferences__voip_video_enable)
                videoCallEnable.isEnabled = false
                videoCallEnable.isSelectable = false
                videoCallEnable.isChecked = !disableVideoCalls
            }

            if (disableVideoCalls == null || !disableVideoCalls) {
                // video calls are force-enabled or left to the user - user may change profile setting
                val videoCategory: PreferenceCategory = getPref("pref_key_voip_video_settings")
                videoCategory.dependency = null
                videoCategory.isEnabled = true

                val videoCallProfile: DropDownPreference = getPref(R.string.preferences__voip_video_profile)
                videoCallProfile.dependency = null
                videoCallProfile.isEnabled = true
                videoCallProfile.isSelectable = true
            }
        }
    }

    private fun initEnableCallRejectPref() {
        enableCallReject = getPref(R.string.preferences__voip_reject_mobile_calls)
        enableCallReject?.setOnPreferenceChangeListener { _, newValue ->
            val newCheckedValue = newValue == true
            if (newCheckedValue) {
                ConfigUtils.requestPhonePermissions(requireActivity(), this@SettingsCallsFragment, PERMISSION_REQUEST_READ_PHONE_STATE)
            } else true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        this.fragmentView = view
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_READ_PHONE_STATE -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                this.enableCallReject?.isChecked = true
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
                ConfigUtils.showPermissionRationale(context, fragmentView, R.string.permission_phone_required)
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_READ_PHONE_STATE = 3
    }

}
