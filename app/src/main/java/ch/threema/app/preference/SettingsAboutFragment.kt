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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.activities.*
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.LicenseServiceSerial
import ch.threema.app.utils.*
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("SettingsAboutFragment")

@Suppress("unused")
class SettingsAboutFragment : ThreemaPreferenceFragment() {
    private var aboutCounter = 0

    private val preferenceService: PreferenceService = requirePreferenceService()
    private val licenseService: LicenseService<*> = requireLicenceService()

    override fun initializePreferences() {
        initLicensePref()
        initPrivacyPolicyPref()
        initTermsOfServicePref()
        initEndUserLicensePref()
        initAboutPref()
        initSelfUpdatePref()
        initServerConfigSection()
        initDeviceInfoPref()
        initTranslatorPref()
    }

    override fun getPreferenceTitleResource(): Int = R.string.menu_about

    override fun getPreferenceResource(): Int = R.xml.preference_about

    private fun initLicensePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__licenses)
        licensePreference.onClick {
            startActivity(Intent(context, LicenseActivity::class.java))
        }
    }

    private fun initPrivacyPolicyPref() {
        val privacyPolicyPreference = getPref<Preference>(R.string.preferences__privacy_policy)
        if (ConfigUtils.isOnPremBuild() && !ConfigUtils.isDemoOPServer(preferenceService)) {
            privacyPolicyPreference.isVisible = false
        } else {
            privacyPolicyPreference.onClick {
                startActivity(Intent(context, PrivacyPolicyActivity::class.java))
            }
        }
    }

    private fun initTermsOfServicePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__terms_of_service)
        if (BuildFlavor.current.licenseType == BuildFlavor.LicenseType.ONPREM) {
            licensePreference.isVisible = false
        } else {
            licensePreference.onClick {
                startActivity(Intent(context, TermsOfServiceActivity::class.java))
            }
        }
    }

    private fun initEndUserLicensePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__eula)
        if (BuildFlavor.current.licenseType == BuildFlavor.LicenseType.GOOGLE) {
            licensePreference.onClick {
                startActivity(Intent(context, EulaActivity::class.java))
            }
        } else {
            licensePreference.isVisible = false
        }
    }

    private fun initAboutPref() {
        val aboutPreference = getPref<Preference>(R.string.preferences__about)
        aboutPreference.title = getVersionNameWithBuildNumber()
        aboutPreference.setSummary(R.string.about_copyright)
        aboutPreference.onClick {
            if (aboutCounter % 2 == 0) {
                aboutPreference.title = getVersionNameWithVersionCode()
            } else {
                aboutPreference.title = getVersionNameWithBuildNumber()
            }

            if (aboutCounter == ABOUT_REQUIRED_CLICKS) {
                aboutCounter++
                val intent = Intent(requireActivity().applicationContext, AboutActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
                startActivity(intent)
                activity?.finish()
            } else {
                aboutCounter++
            }
        }
    }

    private fun initSelfUpdatePref() {
        val checkUpdatePreference = getPref<Preference>(R.string.preferences__check_updates)

        if (BuildFlavor.current.maySelfUpdate) {
            checkUpdatePreference.onClick {
                checkForUpdates(licenseService as LicenseServiceSerial)
            }
        } else {
            val aboutCategory = getPref<PreferenceCategory>("pref_key_about_header")
            aboutCategory.removePreference(checkUpdatePreference)
        }
    }

    private fun initServerConfigSection() {
        val shouldShowServer = ConfigUtils.isOnPremBuild()
        val shouldShowUsername = shouldShowUsername()

        if (!shouldShowServer && !shouldShowUsername) {
            getPref<Preference>(R.string.preferences__server_config).isVisible = false
            return
        }

        getPref<Preference>(R.string.preferences__work_license_name)
            .let { workLicensePreference ->
                if (shouldShowUsername) {
                    workLicensePreference.summary = preferenceService.licenseUsername
                }
                workLicensePreference.isVisible = shouldShowUsername
            }

        getPref<Preference>(R.string.preferences__onprem_server)
            .let { serverConfigPreference ->
                if (shouldShowServer) {
                    serverConfigPreference.summary = getServerInfo()
                }
                serverConfigPreference.isVisible = shouldShowServer
            }
    }

    private fun shouldShowUsername(): Boolean {
        return when {
            !ConfigUtils.isWorkBuild() -> false
            ConfigUtils.isWorkRestricted() -> {
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__readonly_profile)) != true
            }
            else -> true
        }
    }

    private fun getServerInfo(): String {
        return preferenceService.onPremServer
            ?.toUri()
            ?.authority
            ?: "?"
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
        translatorsPreference.onClick {
            SimpleStringAlertDialog.newInstance(
                R.string.translators,
                getString(R.string.translators_thanks, getString(R.string.translators_list)),
            ).show(parentFragmentManager, "tt")
        }
    }

    private fun getVersionNameWithBuildNumber(): String = buildString {
        appendVersionName()
        appendBuildNumber()
        appendBuildFlavor()
    }

    private fun getVersionNameWithVersionCode(): String = buildString {
        appendVersionName()
        appendVersionCode()
        appendBuildFlavor()
    }

    private fun StringBuilder.appendVersionName() {
        append(getString(R.string.threema_version))
        append(" ")
        append(BuildConfig.VERSION_NAME)
    }

    private fun StringBuilder.appendVersionCode() {
        append(" ")
        append(getString(R.string.threema_version_code))
        append(" ")
        append(BuildConfig.VERSION_CODE)
    }

    private fun StringBuilder.appendBuildNumber() {
        append(" Build ")
        append(ConfigUtils.getBuildNumber(context))
    }

    private fun StringBuilder.appendBuildFlavor() {
        append(" ")
        append(BuildFlavor.current.fullDisplayName)
        if (BuildConfig.DEBUG) {
            append(" Commit ")
            append(BuildConfig.GIT_HASH)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StaticFieldLeak")
    private fun checkForUpdates(licenseServiceSerial: LicenseServiceSerial) {
        if (!BuildFlavor.current.maySelfUpdate) {
            logger.warn("Called checkForUpdate in a build variant without self-updating")
            return
        }
        object : AsyncTask<Void?, Void?, String?>() {
            private var updateUrl: String? = null

            @Deprecated("Deprecated in Java")
            override fun onPreExecute() {
                GenericProgressDialog.newInstance(R.string.check_updates, R.string.please_wait)
                    .show(activity!!.supportFragmentManager, DIALOG_TAG_CHECK_UPDATE)
            }

            @Deprecated("Deprecated in Java")
            override fun doInBackground(vararg voids: Void?): String? {
                return try {
                    licenseServiceSerial.validate(false)
                    updateUrl = licenseServiceSerial.updateUrl
                    if (updateUrl.isNullOrEmpty()) getString(R.string.no_update_available) else null
                } catch (e: Exception) {
                    getString(R.string.an_error_occurred_more, e.localizedMessage)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onPostExecute(error: String?) {
                DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_CHECK_UPDATE, true)
                if (error != null) {
                    SimpleStringAlertDialog.newInstance(R.string.check_updates, error)
                        .show(parentFragmentManager, "nu")
                } else {
                    val updateMessage = getString(R.string.update_available_message)
                    val dialogIntent =
                        IntentDataUtil.createActionIntentUpdateAvailable(updateMessage, updateUrl)
                            .putExtra(DownloadApkActivity.EXTRA_FORCE_UPDATE_DIALOG, true)
                            .setClass(requireContext(), DownloadApkActivity::class.java)
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
