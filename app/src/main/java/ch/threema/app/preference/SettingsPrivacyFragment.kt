package ch.threema.app.preference

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.BlockedIdentitiesActivity
import ch.threema.app.activities.ExcludedSyncIdentitiesActivity
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.listeners.SynchronizeContactsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.routines.SynchronizeContactsRoutine
import ch.threema.app.services.ContactService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.utils.*
import ch.threema.app.workers.ShareTargetUpdateWorker
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsPrivacyFragment")

class SettingsPrivacyFragment :
    ThreemaPreferenceFragment(),
    GenericAlertDialog.DialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val contactService: ContactService by inject()
    private val lockAppService: LockAppService by inject()
    private val synchronizeContactsService: SynchronizeContactsService by inject()
    private val preferenceService: PreferenceService by inject()
    private val synchronizedSettingsService: SynchronizedSettingsService by inject()
    private val appRestrictions: AppRestrictions by inject()

    private lateinit var disableScreenshot: CheckBoxPreference
    private var disableScreenshotChecked = false

    private lateinit var fragmentView: View

    private lateinit var contactSyncPreference: TwoStatePreference

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

        disableScreenshot = getPref(synchronizedSettingsService.getScreenshotPolicySetting().preferenceKey)
        disableScreenshotChecked = this.disableScreenshot.isChecked

        if (lockAppService.isLockingEnabled) {
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
        if (appRestrictions.isContactSyncEnabledOrNull() == null && !SynchronizeContactsUtil.isRestrictedProfile(activity)) {
            contactSyncPreference.apply {
                isEnabled = !synchronizeContactsService.isSynchronizationInProgress
            }
        }
    }

    private fun initContactSyncPref() {
        contactSyncPreference = getPref(synchronizedSettingsService.getContactSyncPolicySetting().preferenceKey)
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
                preferenceService.setEmailSyncHashCode(0)
                preferenceService.setPhoneNumberSyncHashCode(0)
                preferenceService.setTimeOfLastContactSync(null)

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
            if (appRestrictions.isBlockUnknownOrNull() != null) {
                val blockUnknown: CheckBoxPreference = getPref(synchronizedSettingsService.getUnknownContactPolicySetting().preferenceKey)
                blockUnknown.isEnabled = false
                blockUnknown.isSelectable = false
            }
            if (appRestrictions.isScreenshotsDisabledOrNull() != null) {
                disableScreenshot.isEnabled = false
                disableScreenshot.isSelectable = false
            }
            val value = appRestrictions.isContactSyncEnabledOrNull()
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
            if (synchronizeContactsService.enableSyncFromLocal() &&
                ConfigUtils.requestContactPermissions(
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
                contactService.resetReceiptsSettings()
                showToast(R.string.reset_successful, ToastDuration.SHORT)
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
