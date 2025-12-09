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
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.BlockedIdentitiesActivity
import ch.threema.app.activities.ExcludedSyncIdentitiesActivity
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.listeners.SynchronizeContactsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.routines.SynchronizeContactsRoutine
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.utils.*
import ch.threema.app.workers.ShareTargetUpdateWorker
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.lazy
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar

private val logger = getThreemaLogger("SettingsPrivacyFragment")

class SettingsPrivacyFragment :
    ThreemaPreferenceFragment(),
    GenericAlertDialog.DialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val serviceManager by lazy { ThreemaApplication.requireServiceManager() }
    private val preferenceService by lazy { serviceManager.preferenceService }

    private lateinit var disableScreenshot: CheckBoxPreference
    private var disableScreenshotChecked = false

    private lateinit var fragmentView: View

    private lateinit var contactSyncPreference: TwoStatePreference

    private val synchronizeContactsService: SynchronizeContactsService by lazy { serviceManager.synchronizeContactsService }

    private val synchronizeContactsListener: SynchronizeContactsListener =
        object : SynchronizeContactsListener {
            override fun onStarted(startedRoutine: SynchronizeContactsRoutine) {
                RuntimeUtil.runOnUiThread {
                    updateView()
                    GenericProgressDialog.newInstance(
                        R.string.wizard1_sync_contacts,
                        R.string.please_wait,
                    ).show(parentFragmentManager, DIALOG_TAG_SYNC_CONTACTS)
                }
            }

            override fun onFinished(finishedRoutine: SynchronizeContactsRoutine) {
                RuntimeUtil.runOnUiThread {
                    updateView()
                    if (this@SettingsPrivacyFragment.isAdded) {
                        DialogUtil.dismissDialog(
                            parentFragmentManager,
                            DIALOG_TAG_SYNC_CONTACTS,
                            true,
                        )
                    }
                }
            }

            override fun onError(finishedRoutine: SynchronizeContactsRoutine) {
                RuntimeUtil.runOnUiThread {
                    updateView()
                    if (this@SettingsPrivacyFragment.isAdded) {
                        DialogUtil.dismissDialog(
                            parentFragmentManager,
                            DIALOG_TAG_SYNC_CONTACTS,
                            true,
                        )
                    }
                }
            }
        }

    override fun initializePreferences() {
        super.initializePreferences()

        disableScreenshot = getPref(preferenceService.screenshotPolicySetting.preferenceKey)
        disableScreenshotChecked = this.disableScreenshot.isChecked

        if (ConfigUtils.getScreenshotsDisabled(preferenceService, serviceManager.lockAppService)) {
            disableScreenshot.isEnabled = false
            disableScreenshot.isSelectable = false
        }

        initContactSyncPref()

        initWorkRestrictedPrefs()

        initExcludedSyncIdentitiesPref()

        initBlockedContactsPref()

        initResetReceiptsPref()

        initDirectSharePref()

        updateView()
    }

    override fun getPreferenceTitleResource(): Int = R.string.prefs_privacy

    override fun getPreferenceResource(): Int = R.xml.preference_privacy

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        ListenerManager.synchronizeContactsListeners.add(synchronizeContactsListener)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fragmentView = view
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        ListenerManager.synchronizeContactsListeners.remove(synchronizeContactsListener)
        if (isAdded) {
            DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_VALIDATE, true)
            DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_SYNC_CONTACTS, true)
            DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_DISABLE_SYNC, true)
        }
        super.onDestroyView()
    }

    override fun onDetach() {
        super.onDetach()
        if (disableScreenshot.isChecked != disableScreenshotChecked) {
            ConfigUtils.recreateActivity(activity)
        }
    }

    private fun updateView() {
        if (!AppRestrictionUtil.hasBooleanRestriction(getString(R.string.restriction__contact_sync)) && !SynchronizeContactsUtil.isRestrictedProfile(
                activity,
            )
        ) {
            contactSyncPreference.apply {
                isEnabled = !synchronizeContactsService.isSynchronizationInProgress
            }
        }
    }

    private fun initContactSyncPref() {
        contactSyncPreference = getPref(preferenceService.contactSyncPolicySetting.preferenceKey)
        contactSyncPreference.summaryOn =
            getString(R.string.prefs_sum_sync_contacts_on, getString(R.string.app_name))
        contactSyncPreference.summaryOff =
            getString(R.string.prefs_sum_sync_contacts_off, getString(R.string.app_name))

        if (SynchronizeContactsUtil.isRestrictedProfile(activity)) {
            // restricted android profile (e.g. guest user)
            contactSyncPreference.isChecked = false
            contactSyncPreference.isEnabled = false
            contactSyncPreference.isSelectable = false
        } else {
            contactSyncPreference.onChange<Boolean> { enabled ->
                preferenceService.emailSyncHashCode = 0
                preferenceService.phoneNumberSyncHashCode = 0
                preferenceService.timeOfLastContactSync = null

                // Note that the change here can be triggered from sync. However, as this only happens while the preferences are shown, this is
                // considered acceptable.
                if (enabled) {
                    enableSyncFromLocal()
                } else {
                    disableSyncFromLocal()
                }
            }
        }
    }

    private fun initWorkRestrictedPrefs() {
        if (ConfigUtils.isWorkRestricted()) {
            var value =
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__block_unknown))
            if (value != null) {
                val blockUnknown: CheckBoxPreference = getPref(preferenceService.unknownContactPolicySetting.preferenceKey)
                blockUnknown.isEnabled = false
                blockUnknown.isSelectable = false
            }
            value =
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_screenshots))
            if (value != null) {
                disableScreenshot.isEnabled = false
                disableScreenshot.isSelectable = false
            }
            value =
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__contact_sync))
            if (value != null) {
                contactSyncPreference.isEnabled = false
                contactSyncPreference.isSelectable = false
                contactSyncPreference.isChecked = value
            }
        }
    }

    private fun initExcludedSyncIdentitiesPref() {
        getPref<Preference>("pref_excluded_sync_identities").onClick {
            startActivity(ExcludedSyncIdentitiesActivity.createIntent(requireContext()))
        }
    }

    private fun initBlockedContactsPref() {
        getPref<Preference>("pref_blocked_contacts").onClick {
            startActivity(BlockedIdentitiesActivity.createIntent(requireContext()))
        }
    }

    private fun initResetReceiptsPref() {
        getPref<Preference>("pref_reset_receipts").onClick {
            val dialog = GenericAlertDialog.newInstance(
                R.string.prefs_title_reset_receipts,
                // TODO(ANDR-3686)
                getString(R.string.prefs_sum_reset_receipts) + "?",
                R.string.yes,
                R.string.no,
            )
            dialog.targetFragment = this@SettingsPrivacyFragment
            dialog.show(parentFragmentManager, DIALOG_TAG_RESET_RECEIPTS)
        }
    }

    private fun initDirectSharePref() {
        getPrefOrNull<TwoStatePreference>(R.string.preferences__direct_share)?.onChange<Boolean> { enabled ->
            if (enabled) {
                ShareTargetUpdateWorker.scheduleShareTargetShortcutUpdate(requireContext())
            } else {
                ShareTargetUpdateWorker.cancelScheduledShareTargetShortcutUpdate(requireContext())
                ShortcutUtil.deleteAllShareTargetShortcuts(preferenceService)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val preferenceCategory = getPref<PreferenceCategory>("pref_key_other")
            preferenceCategory.removePreference(getPref(resources.getString(R.string.preferences__disable_smart_replies)))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray,
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_CONTACTS -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchContactsSync()
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                disableSyncFromLocal()
                ConfigUtils.showPermissionRationale(
                    context,
                    fragmentView,
                    R.string.permission_contacts_sync_required,
                    object : BaseCallback<Snackbar?>() {},
                )
            } else {
                disableSyncFromLocal()
            }
        }
    }

    private fun launchContactsSync() {
        // start a Sync
        synchronizeContactsService.instantiateSynchronizationAndRun()
    }

    private fun enableSyncFromLocal() {
        try {
            if (synchronizeContactsService.enableSyncFromLocal() && ConfigUtils.requestContactPermissions(
                    requireActivity(),
                    this@SettingsPrivacyFragment,
                    PERMISSION_REQUEST_CONTACTS,
                )
            ) {
                launchContactsSync()
            }
        } catch (e: MasterKeyLockedException) {
            logger.error("Exception", e)
        }
    }

    private fun disableSyncFromLocal() {
        GenericProgressDialog.newInstance(R.string.app_name, R.string.please_wait).show(parentFragmentManager, DIALOG_TAG_DISABLE_SYNC)
        Thread(
            {
                synchronizeContactsService.disableSyncFromLocal {
                    RuntimeUtil.runOnUiThread {
                        DialogUtil.dismissDialog(
                            parentFragmentManager,
                            DIALOG_TAG_DISABLE_SYNC,
                            true,
                        )
                        contactSyncPreference.isChecked = false
                    }
                }
            },
            "DisableSync",
        ).start()
    }

    private fun resetReceipts() {
        Thread(
            {
                serviceManager.contactService.resetReceiptsSettings()
                RuntimeUtil.runOnUiThread {
                    Toast.makeText(
                        context,
                        R.string.reset_successful,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            "ResetReceiptSettings",
        ).start()
    }

    override fun onYes(tag: String?, data: Any?) {
        resetReceipts()
    }

    companion object {
        private const val DIALOG_TAG_VALIDATE = "vali"
        private const val DIALOG_TAG_SYNC_CONTACTS = "syncC"
        private const val DIALOG_TAG_DISABLE_SYNC = "dissync"
        private const val DIALOG_TAG_RESET_RECEIPTS = "rece"

        private const val PERMISSION_REQUEST_CONTACTS = 1
    }
}
