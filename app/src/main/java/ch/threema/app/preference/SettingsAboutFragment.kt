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

@file:Suppress("DEPRECATION")

package ch.threema.app.preference

import android.annotation.SuppressLint
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.activities.*
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.LicenseServiceSerial
import ch.threema.app.utils.*
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("SettingsAboutFragment")

@Suppress("unused")
class SettingsAboutFragment : ThreemaPreferenceFragment() {

    private var updateUrl: String? = null
    private var updateMessage: String? = null

    private var aboutCounter = 0

    private var preferenceService: PreferenceService = requirePreferenceService()
    private var licenseService: LicenseService<*> = requireLicenceService()

    override fun initializePreferences() {
        super.initializePreferences()

        initLicensePref()

        initPrivacyPolicyPref()

        initTermsOfServicePref()

        initEndUserLicensePref()

        initAboutPref()

        initSelfUpdatePref()

        initWorkLicensePref()

        initDeviceInfoPref()

        initTranslatorPref()
    }

    override fun getPreferenceTitleResource(): Int = R.string.menu_about

    override fun getPreferenceResource(): Int = R.xml.preference_about

    private fun initLicensePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__licenses)
        licensePreference.setOnPreferenceClickListener {
            startActivity(Intent(requireActivity().applicationContext, LicenseActivity::class.java))
            true
        }
    }

    private fun initPrivacyPolicyPref() {
        val privacyPolicyPreference = getPref<Preference>(R.string.preferences__privacy_policy)
        if (ConfigUtils.isOnPremBuild() && !ConfigUtils.isDemoOPServer(preferenceService)) {
            privacyPolicyPreference.isVisible = false
        } else {
            privacyPolicyPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(Intent(requireActivity().applicationContext, PrivacyPolicyActivity::class.java))
                true
            }
        }
    }

    private fun initTermsOfServicePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__terms_of_service)
        if (BuildFlavor.getLicenseType() == BuildFlavor.LicenseType.ONPREM) {
            licensePreference.isVisible = false
        } else {
            licensePreference.setOnPreferenceClickListener {
                startActivity(Intent(requireActivity().applicationContext, TermsOfServiceActivity::class.java))
                true
            }
        }
    }

    private fun initEndUserLicensePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__eula)
        if (BuildFlavor.getLicenseType() == BuildFlavor.LicenseType.GOOGLE) {
            licensePreference.setOnPreferenceClickListener {
                startActivity(Intent(requireActivity().applicationContext, EulaActivity::class.java))
                true
            }
        } else {
            licensePreference.isVisible = false
        }
    }

    private fun initAboutPref() {
        val aboutPreference = getPref<Preference>(R.string.preferences__about)
        aboutPreference.title = getString(R.string.threema_version) + " " + getVersionString()
        aboutPreference.setSummary(R.string.about_copyright)
        aboutPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            if (aboutCounter == ABOUT_REQUIRED_CLICKS) {
                aboutCounter++
                val intent = Intent(requireActivity().applicationContext, AboutActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
                startActivity(intent)
                activity?.finish()
                true
            } else {
                aboutCounter++
                false
            }
        }
    }

    private fun initSelfUpdatePref() {
        val checkUpdatePreference = getPref<Preference>(R.string.preferences__check_updates)

        if (BuildFlavor.maySelfUpdate()) {
            checkUpdatePreference.setOnPreferenceClickListener {
                checkForUpdates(licenseService as LicenseServiceSerial)
                true
            }
        } else {
            val aboutCategory = getPref<PreferenceCategory>("pref_key_about_header")
            aboutCategory.removePreference(checkUpdatePreference)
        }
    }

    private fun initWorkLicensePref() {
        val aboutCategory = getPref<PreferenceCategory>("pref_key_about_header")
        val workLicensePreference = getPref<Preference>(R.string.preferences__work_license_name)

        if (ConfigUtils.isWorkBuild()) {
            workLicensePreference.summary = preferenceService.licenseUsername
            preferenceScreen.removePreference(getPref("pref_key_feedback_header"))
            if (ConfigUtils.isWorkRestricted()) {
                val readonly = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__readonly_profile))
                if (readonly != null && readonly) {
                    aboutCategory.removePreference(workLicensePreference)
                }
            }
        } else {
            aboutCategory.removePreference(workLicensePreference)
        }
    }

    private fun initDeviceInfoPref() {
        getPrefOrNull<Preference>(R.string.preferences__device_info)?.apply {
            if (Build.MANUFACTURER != null) {
                title = Build.MANUFACTURER + " " + Build.MODEL
            }
            summary = Build.FINGERPRINT
        }
    }

    private fun initTranslatorPref() {
        val translatorsPreference = getPref<Preference>(R.string.preferences__translators)
        translatorsPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            SimpleStringAlertDialog.newInstance(R.string.translators, getString(R.string.translators_thanks, getString(R.string.translators_list))).show(parentFragmentManager, "tt")
            true
        }
    }

    private fun getVersionString(): String {
        val version = StringBuilder()
        version.append(ConfigUtils.getAppVersion(requireContext()))
        version.append(" Build ").append(ConfigUtils.getBuildNumber(context))
        version.append(" ").append(BuildFlavor.getName())
        if (BuildConfig.DEBUG) {
            version.append(" Commit ").append(BuildConfig.GIT_HASH)
        }
        return version.toString()
    }

    @SuppressLint("StaticFieldLeak")
    private fun checkForUpdates(licenseServiceSerial: LicenseServiceSerial) {
        if (!BuildFlavor.maySelfUpdate()) {
            logger.warn("Called checkForUpdate in a build variant without self-updating")
            return
        }
        object : AsyncTask<Void?, Void?, String?>() {
            @Deprecated("Deprecated in Java")
            override fun onPreExecute() {
                GenericProgressDialog.newInstance(R.string.check_updates, R.string.please_wait).show(activity!!.supportFragmentManager, DIALOG_TAG_CHECK_UPDATE)
            }

            @Deprecated("Deprecated in Java")
            override fun doInBackground(vararg voids: Void?): String? {
                return try {
                    // Validate license and check for updates
                    licenseServiceSerial.validate(false)

                    // If an update is available, then `updateUrl` and `updateMessage` will
                    // be set to a non-null value.
                    updateUrl = licenseServiceSerial.updateUrl
                    updateMessage = licenseServiceSerial.updateMessage
                    if (TestUtil.empty(updateUrl, updateMessage)) {
                        // No update available...
                        getString(R.string.no_update_available)
                    } else null

                    // Update available!
                } catch (x: Exception) {
                    String.format(getString(R.string.an_error_occurred_more), x.localizedMessage)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onPostExecute(error: String?) {
                DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_CHECK_UPDATE, true)
                if (error != null) {
                    SimpleStringAlertDialog.newInstance(R.string.check_updates, error).show(parentFragmentManager, "nu")
                } else {
                    val dialogIntent = IntentDataUtil.createActionIntentUpdateAvailable(updateMessage, updateUrl)
                    dialogIntent.putExtra(DownloadApkActivity.EXTRA_FORCE_UPDATE_DIALOG, true)
                    dialogIntent.setClass(requireContext(), DownloadApkActivity::class.java)
                    startActivity(dialogIntent)
                }
            }
        }.execute()
    }

    companion object {
        private const val ABOUT_REQUIRED_CLICKS = 10
        private const val DIALOG_TAG_CHECK_UPDATE = "checkup"
    }

}
