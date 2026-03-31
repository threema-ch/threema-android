package ch.threema.app.preference

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.preference.CheckBoxPreference
import androidx.preference.DropDownPreference
import ch.threema.app.R
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.services.VoipStateService
import ch.threema.app.voip.util.VoipUtil
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsCallsFragment")

@Suppress("unused")
class SettingsCallsFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private val groupCallManager: GroupCallManager by inject()
    private val voipStateService: VoipStateService by inject()
    private val preferenceService: PreferenceService by inject()
    private val synchronizedSettingsService: SynchronizedSettingsService by inject()
    private val appRestrictions: AppRestrictions by inject()

    private var fragmentView: View? = null
    private var enableCallReject: CheckBoxPreference? = null
    private lateinit var callEnable: CheckBoxPreference
    private lateinit var groupCallsEnable: CheckBoxPreference

    override fun initializePreferences() {
        super.initializePreferences()
        initCallPrefListeners()
        initWorkRestrictions()
        initEnableCallRejectPref()
    }

    override fun getPreferenceTitleResource(): Int = R.string.prefs_title_calls

    override fun getPreferenceResource(): Int = R.xml.preference_calls

    private fun initCallPrefListeners() {
        callEnable = getPref(synchronizedSettingsService.getO2oCallPolicySetting().preferenceKey)
        callEnable.onChange<Boolean> { enabled ->
            if (!enabled && voipStateService.callState?.isIdle != true) {
                VoipUtil.sendOneToOneCallHangupCommand(requireActivity())
            }
        }

        groupCallsEnable = getPref(synchronizedSettingsService.getGroupCallPolicySetting().preferenceKey)
        groupCallsEnable.onChange<Boolean> { enabled ->
            if (!enabled) {
                groupCallManager.abortCurrentCall()
            }
            ListenerManager.conversationListeners.handle { it.onModifiedAll() }
        }
    }

    private fun initWorkRestrictions() {
        if (!ConfigUtils.isWorkRestricted()) {
            return
        }

        val disableCalls = appRestrictions.isCallsDisabledOrNull()
        var disableVideoCalls = appRestrictions.isVideoCallsDisabledOrNull()
        var disableGroupCalls = appRestrictions.isGroupCallsDisabledOrNull()

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
                val forceTurn: CheckBoxPreference = getPref(synchronizedSettingsService.getO2oCallConnectionPolicySetting().preferenceKey)
                val rejectCalls: CheckBoxPreference =
                    getPref(R.string.preferences__voip_reject_mobile_calls)

                forceTurn.dependency = null
                rejectCalls.dependency = null
            }
        }

        val videoCallEnable: CheckBoxPreference = getPref(synchronizedSettingsService.getO2oCallVideoPolicySetting().preferenceKey)
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
