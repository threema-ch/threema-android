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
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.activities.DownloadApkActivity
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.LicenseServiceSerial
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.utils.showToast
import ch.threema.app.webviews.EulaActivity
import ch.threema.app.webviews.LicenseActivity
import ch.threema.app.webviews.PrivacyPolicyActivity
import ch.threema.app.webviews.TermsOfServiceActivity
import ch.threema.base.utils.LoggingUtil
import ch.threema.localcrypto.MasterKeyManager
import org.koin.android.ext.android.inject

private val logger = LoggingUtil.getThreemaLogger("SettingsAboutFragment")

@Suppress("unused")
class SettingsAboutFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private var aboutCounter = 0

    private val preferenceService: PreferenceService by inject()
    private val licenseService: LicenseService<*> by inject()
    private val masterKeyManager: MasterKeyManager by inject()

    override fun initializePreferences() {
        initLicensePref()
        initPrivacyPolicyPref()
        initTermsOfServicePref()
        initEndUserLicensePref()
        initVersionSection()
        initSelfUpdatePref()
        initServerConfigSection()
    }

    override fun getPreferenceTitleResource(): Int = R.string.menu_about

    override fun getPreferenceResource(): Int = R.xml.preference_about

    private fun initLicensePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__licenses)
        licensePreference.onClick {
            startActivity(LicenseActivity.createIntent(requireContext()))
        }
    }

    private fun initPrivacyPolicyPref() {
        val privacyPolicyPreference = getPref<Preference>(R.string.preferences__privacy_policy)
        if (ConfigUtils.isOnPremBuild() && !ConfigUtils.isDemoOPServer(preferenceService)) {
            privacyPolicyPreference.isVisible = false
        } else {
            privacyPolicyPreference.onClick {
                startActivity(PrivacyPolicyActivity.createIntent(requireContext()))
            }
        }
    }

    private fun initTermsOfServicePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__terms_of_service)
        if (BuildFlavor.current.licenseType == BuildFlavor.LicenseType.ONPREM) {
            licensePreference.isVisible = false
        } else {
            licensePreference.onClick {
                startActivity(TermsOfServiceActivity.createIntent(requireContext()))
            }
        }
    }

    private fun initEndUserLicensePref() {
        val licensePreference = getPref<Preference>(R.string.preferences__eula)
        if (BuildFlavor.current.licenseType == BuildFlavor.LicenseType.GOOGLE) {
            licensePreference.onClick {
                startActivity(EulaActivity.createIntent(requireContext()))
            }
        } else {
            licensePreference.isVisible = false
        }
    }

    private fun initVersionSection() {
        initVersionPref()
        initVersionCodePref()
        initDeviceInfoPref()
    }

    private fun initVersionPref() {
        val versionPreference = getPref<Preference>(R.string.preferences__version)
        versionPreference.title = getVersionNameWithBuildNumber()
        versionPreference.setSummary(R.string.about_copyright)
        versionPreference.onClick {
            aboutCounter++
            if (aboutCounter == 10) {
                onSecretUnlocked()
            }
        }
    }

    private fun onSecretUnlocked() {
        if (ConfigUtils.isDevBuild() && !preferenceService.showDeveloperMenu()) {
            preferenceService.setShowDeveloperMenu(true)
            showToast("Developer settings unlocked")
        }

        val installerPackage = ConfigUtils.getInstallerPackageName(requireContext())
        getPref<Preference>(R.string.preferences__installer_package).summary = installerPackage ?: "none"
        getPref<Preference>(R.string.preferences__hidden_info_section).isVisible = true
    }

    private fun initVersionCodePref() {
        val versionCodePreference = getPref<Preference>(R.string.preferences__version_code)
        versionCodePreference.title = buildString {
            append(getString(R.string.threema_version_code))
            append(" ")
            append(BuildConfig.VERSION_CODE)
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
        val shouldShowRemoteSecretActivated = masterKeyManager.isProtectedWithRemoteSecret() == true

        if (!shouldShowServer && !shouldShowUsername && !shouldShowRemoteSecretActivated) {
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

        getPref<Preference>(R.string.preferences__remote_secret_activated)
            .let { remoteSecretPreference ->
                if (shouldShowRemoteSecretActivated) {
                    remoteSecretPreference.onClick(::onRemoteSecretClicked)
                } else {
                    remoteSecretPreference.isVisible = false
                }
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

    private fun getVersionNameWithBuildNumber(): String = buildString {
        appendVersionName()
        appendBuildNumber()
        appendBuildFlavor()
    }

    private fun StringBuilder.appendVersionName() {
        append(getString(R.string.threema_version))
        append(" ")
        append(BuildConfig.VERSION_NAME)
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

    private fun onRemoteSecretClicked() {
        try {
            val learnMoreUrl = getString(R.string.remote_secret_learn_more_url).toUri()
            startActivity(Intent(Intent.ACTION_VIEW, learnMoreUrl))
        } catch (e: ActivityNotFoundException) {
            logger.warn("No activity found to open Remote Secret learn more URL")
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
        private const val DIALOG_TAG_CHECK_UPDATE = "checkup"
    }
}
