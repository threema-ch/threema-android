/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import ch.threema.android.showToast
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.activities.PinLockActivity
import ch.threema.app.applock.AppLockActivity
import ch.threema.app.applock.AppLockUtil
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.PasswordEntryDialog
import ch.threema.app.dialogs.PasswordEntryDialog.PasswordEntryDialogClickListener
import ch.threema.app.dialogs.ThreemaDialogFragment
import ch.threema.app.passphrase.PassphraseUnlockActivity
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.usecases.RemoveAllPrivateMarksUseCase
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsSecurityFragment")

class SettingsSecurityFragment : ThreemaPreferenceFragment(), PasswordEntryDialogClickListener, DialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val masterKeyManager: MasterKeyManager by inject()
    private val preferenceService: PreferenceService by inject()
    private val conversationCategoryService: ConversationCategoryService by inject()
    private val appLockUtil: AppLockUtil by inject()
    private val dispatcherProvider: DispatcherProvider by inject()
    private val removeAllPrivateMarksUseCase: RemoveAllPrivateMarksUseCase by inject()

    private lateinit var pinPreference: Preference
    private lateinit var uiLockSwitchPreference: TwoStatePreference
    private lateinit var graceTimePreference: DropDownPreference
    private lateinit var lockMechanismPreference: DropDownPreference
    private lateinit var masterKeyPreference: Preference
    private lateinit var masterKeySwitchPreference: TwoStatePreference

    private lateinit var fragmentView: View

    private val unlockLauncher = registerForActivityResult<Intent, ActivityResult?>(StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlocked()
        } else {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private val setSystemLockLauncher = registerForActivityResult<Intent, ActivityResult?>(StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            lockMechanismPreference.value = PreferenceService.LockingMech_SYSTEM
            if (uiLockSwitchPreference.isChecked) {
                preferenceService.setAppLockEnabled(true)
            }
            updateLockPreferences()
        }
    }

    private val removePassphraseLauncher = registerForActivityResult<Intent, ActivityResult?>(StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val passphrase = PassphraseUnlockActivity.getPassphrase(result.data!!)
            if (passphrase != null) {
                lifecycleScope.launch {
                    // optimistically set the switch's state to false. It will get corrected later if the operation fails
                    masterKeySwitchPreference.setChecked(false)
                    try {
                        withContext(dispatcherProvider.worker) {
                            masterKeyManager.removePassphrase(passphrase)
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to remove passphrase", e)
                        showToast(R.string.an_error_occurred)
                    }
                    updateMasterKeyPreferences()
                }
            } else {
                logger.error("Passphrase not received, cannot remove")
            }
        }
    }

    private val unlockPassphraseForChangeLauncher =
        registerForActivityResult<Intent, ActivityResult?>(StartActivityForResult()) { result: ActivityResult? ->
            if (result?.resultCode == Activity.RESULT_OK && result.data != null) {
                val oldPassphrase = PassphraseUnlockActivity.getPassphrase(result.data!!)
                if (oldPassphrase != null) {
                    startChangePassphraseActivity(oldPassphrase)
                } else {
                    logger.error("Passphrase not received, cannot change")
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fragmentView = view

        // Initially, the complete view is made invisible, until the user unlocks the settings using the configured locking mechanism.
        fragmentView.visibility = View.INVISIBLE

        super.onViewCreated(view, savedInstanceState)
        val lockingMechanism = preferenceService.getLockMechanism()

        // TODO(ANDR-4317): Rethink and improve the fallback mechanism
        if (lockingMechanism == PreferenceService.LockingMech_PIN && !preferenceService.isPinSet()) {
            logger.warn("Locking mechanism was set to PIN but no PIN is set, thus disabling locking mechanism")
            removeAccessProtection()
            onUnlocked()
            return
        }

        when (lockingMechanism) {
            PreferenceService.LockingMech_NONE -> onUnlocked()
            PreferenceService.LockingMech_PIN -> if (savedInstanceState == null) {
                unlockLauncher.launch(
                    PinLockActivity.createIntent(requireContext(), checkOnly = true),
                )
            }
            PreferenceService.LockingMech_SYSTEM,
            PreferenceService.LockingMech_BIOMETRIC,
            -> if (savedInstanceState == null) {
                unlockLauncher.launch(
                    AppLockActivity.createIntent(requireContext(), checkOnly = true),
                )
            }
        }
    }

    private fun onUnlocked() {
        fragmentView.isVisible = true

        uiLockSwitchPreference = getPref(getString(R.string.preferences__lock_ui_switch))
        lockMechanismPreference = getPref(getString(R.string.preferences__lock_mechanism))
        pinPreference = getPref(getString(R.string.preferences__pin_lock_code))
        graceTimePreference = getPref(getString(R.string.preferences__pin_lock_grace_time))

        uiLockSwitchPreference.setChecked(preferenceService.isAppLockEnabled())

        val entries = lockMechanismPreference.entries

        if (!isBiometricLockSupported()) {
            // TODO(ANDR-4317): Rethink and improve the fallback mechanism
            if (preferenceService.getLockMechanism() == PreferenceService.LockingMech_BIOMETRIC) {
                if (appLockUtil.hasDeviceLock()) {
                    preferenceService.setLockMechanism(PreferenceService.LockingMech_SYSTEM)
                } else {
                    removeAccessProtection()
                }
            }

            // Remove the "Biometric" option from the dropdown
            lockMechanismPreference.setEntries(entries.copyOf(3))
        }

        lockMechanismPreference.setValueIndex(
            when (preferenceService.getLockMechanism()) {
                PreferenceService.LockingMech_PIN -> 1
                PreferenceService.LockingMech_SYSTEM -> 2
                PreferenceService.LockingMech_BIOMETRIC -> 3
                else -> 0
            },
        )

        updateLockPreferences()

        setGraceTime()

        lockMechanismPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            when (newValue as String?) {
                PreferenceService.LockingMech_NONE -> onNoLockingMechanismSelected()
                PreferenceService.LockingMech_PIN -> onPinLockingMechanismSelected()
                PreferenceService.LockingMech_SYSTEM -> onSystemLockingMechanismSelected()
                PreferenceService.LockingMech_BIOMETRIC -> onBiometricLockingMechanismSelected()
            }
            updateLockPreferences()
            false
        }

        uiLockSwitchPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val newCheckedValue = newValue == true
            val oldCheckedValue = (preference as TwoStatePreference).isChecked
            if (oldCheckedValue != newCheckedValue) {
                preferenceService.setAppLockEnabled(false)
                if (newCheckedValue) {
                    val lockMechanism = lockMechanismPreference.value ?: PreferenceService.LockingMech_NONE
                    if (lockMechanism == PreferenceService.LockingMech_NONE) {
                        return@OnPreferenceChangeListener false
                    }
                    setGraceTime()
                    preferenceService.setAppLockEnabled(true)
                }
            }
            true
        }

        pinPreference.onClick {
            if (preferenceService.isPinSet()) {
                showSetPinDialog()
            }
        }

        updateGraceTimePreferenceSummary(graceTimePreference.value)
        graceTimePreference.onChange<String> { graceTime ->
            updateGraceTimePreferenceSummary(graceTime)
        }

        masterKeyPreference = getPref(getString(R.string.preferences__masterkey_passphrase))
        masterKeyPreference.onClick {
            unlockPassphraseForChangeLauncher.launch(
                PassphraseUnlockActivity.createIntent(requireContext(), checkOnly = true, returnPassphrase = true),
            )
        }

        masterKeySwitchPreference = getPref(getString(R.string.preferences__masterkey_switch))
        updateMasterKeyPreferences()

        masterKeySwitchPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                startSetPassphraseActivity()
            } else {
                removePassphraseLauncher.launch(
                    PassphraseUnlockActivity.createIntent(requireContext(), checkOnly = true, returnPassphrase = true),
                )
            }
            false
        }
    }

    private fun onNoLockingMechanismSelected() {
        if (conversationCategoryService.hasPrivateChats()) {
            showPrivateChatsConfirmDialog()
        } else {
            removeAccessProtection()
        }
    }

    private fun onPinLockingMechanismSelected() {
        val dialog = GenericAlertDialog.newInstance(
            /* title = */
            R.string.warning,
            /* messageString = */
            getString(R.string.password_remember_warning, getString(R.string.app_name)),
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
        )
        dialog.setTargetFragment(this@SettingsSecurityFragment, 0)
        dialog.show(parentFragmentManager, DIALOG_TAG_PASSWORD_REMINDER_PIN)
    }

    private fun isBiometricLockSupported(): Boolean =
        when (appLockUtil.checkBiometrics()) {
            AppLockUtil.BiometricsState.AVAILABLE -> true
            AppLockUtil.BiometricsState.OTHER,
            AppLockUtil.BiometricsState.NO_PERMISSION,
            AppLockUtil.BiometricsState.NO_HARDWARE,
            AppLockUtil.BiometricsState.NOT_ENROLLED,
            -> false
        }

    private fun updateGraceTimePreferenceSummary(graceTime: String?) {
        resources.getStringArray(R.array.list_pin_grace_time_values)
            .indexOfFirst { it == graceTime }
            .takeUnless { it == -1 }
            ?.let { index -> resources.getStringArray(R.array.list_pin_grace_time)[index] }
            ?.let { summary ->
                graceTimePreference.setSummary(summary)
            }
    }

    private fun updateLockPreferences() {
        pinPreference.setSummary(
            if (preferenceService.isPinSet()) {
                getString(R.string.click_here_to_change_pin)
            } else {
                getString(R.string.prefs_title_pin_code)
            },
        )
        lockMechanismPreference.setSummary(lockMechanismPreference.getEntry())

        when (lockMechanismPreference.value) {
            PreferenceService.LockingMech_NONE -> {
                pinPreference.isEnabled = false
                graceTimePreference.isEnabled = false
                uiLockSwitchPreference.setChecked(false)
                uiLockSwitchPreference.isEnabled = false
                preferenceService.setPin(null)
                preferenceService.setAppLockEnabled(false)
            }
            PreferenceService.LockingMech_PIN -> {
                pinPreference.isEnabled = true
                graceTimePreference.isEnabled = true
                uiLockSwitchPreference.isEnabled = true
            }
            PreferenceService.LockingMech_SYSTEM, PreferenceService.LockingMech_BIOMETRIC -> {
                pinPreference.isEnabled = false
                graceTimePreference.isEnabled = true
                uiLockSwitchPreference.isEnabled = true
                preferenceService.setPin(null)
            }
        }

        lockMechanismPreference.refresh()
    }

    private fun showSetPinDialog() {
        val dialogFragment: DialogFragment = PasswordEntryDialog.newInstance(
            /* title = */
            R.string.set_pin_menu_title,
            /* message = */
            R.string.set_pin_summary_intro,
            /* hint = */
            R.string.set_pin_hint,
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
            /* minLength = */
            AppConstants.MIN_PIN_LENGTH,
            /* maxLength = */
            AppConstants.MAX_PIN_LENGTH,
            /* confirmHint = */
            R.string.set_pin_again_summary,
            /* inputType = */
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            /* checkboxText = */
            0,
            /* showForgotPwHint = */
            PasswordEntryDialog.ForgotHintType.NONE,
        )
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_PIN)
    }

    private fun onSystemLockingMechanismSelected() {
        if (appLockUtil.hasDeviceLock()) {
            setSystemLockLauncher.launch(
                AppLockActivity.createIntent(requireContext(), checkOnly = true, authType = PreferenceService.LockingMech_SYSTEM),
            )
        } else {
            val snackbar = Snackbar.make(fragmentView, R.string.no_lockscreen_set, Snackbar.LENGTH_LONG)
            snackbar.setAction(R.string.configure) {
                startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            snackbar.show()
        }
    }

    private fun onBiometricLockingMechanismSelected() {
        when (appLockUtil.checkBiometrics()) {
            AppLockUtil.BiometricsState.NO_PERMISSION -> showToast(R.string.biometrics_no_permission)
            AppLockUtil.BiometricsState.NOT_ENROLLED -> showToast(R.string.biometrics_not_enrolled)
            AppLockUtil.BiometricsState.NO_HARDWARE,
            AppLockUtil.BiometricsState.OTHER,
            -> showToast(R.string.biometrics_not_avilable)
            AppLockUtil.BiometricsState.AVAILABLE -> setBiometricLock()
        }
    }

    private fun setBiometricLock() {
        lifecycleScope.launch {
            val result = appLockUtil.authenticate(
                activity = requireActivity(),
                title = getString(R.string.prefs_title_access_protection),
                subtitle = getString(R.string.biometric_enter_authentication),
                authType = AppLockUtil.AuthType.BIOMETRIC,
            )

            when (result) {
                AppLockUtil.AuthenticationResult.Success -> {
                    Snackbar.make(fragmentView, R.string.biometric_authentication_successful, Snackbar.LENGTH_LONG).show()

                    lockMechanismPreference.value = PreferenceService.LockingMech_BIOMETRIC
                    if (uiLockSwitchPreference.isChecked) {
                        preferenceService.setLockMechanism(PreferenceService.LockingMech_BIOMETRIC)
                    }
                    updateLockPreferences()
                }
                is AppLockUtil.AuthenticationResult.SystemError -> {
                    showToast("${result.errorMessage} (${result.code})")
                }
                AppLockUtil.AuthenticationResult.CancelledByUser -> Unit
            }
        }
    }

    private fun setGraceTime() {
        val graceTime = graceTimePreference.value.toInt()
        if (graceTime >= 0 && graceTime < 30) {
            graceTimePreference.setValue(GRACE_TIME_NEVER)
            updateGraceTimePreferenceSummary(graceTimePreference.value)
        }
    }

    private fun startSetPassphraseActivity() {
        val dialog = GenericAlertDialog.newInstance(
            /* title = */
            R.string.warning,
            /* messageString = */
            getString(R.string.password_remember_warning, getString(R.string.app_name)),
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
        )
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, DIALOG_TAG_PASSWORD_REMINDER_PASSPHRASE)
    }

    private fun startChangePassphraseActivity(oldPassphrase: CharArray?) {
        val dialogFragment: ThreemaDialogFragment = PasswordEntryDialog.newInstance(
            /* title = */
            R.string.masterkey_passphrase_title,
            /* message = */
            R.string.masterkey_passphrase_summary,
            /* hint = */
            R.string.masterkey_passphrase_hint,
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
            /* minLength = */
            8,
            /* maxLength = */
            0,
            /* confirmHint = */
            R.string.masterkey_passphrase_again_summary,
            /* inputType = */
            0,
            /* checkboxText = */
            0,
            /* showForgotPwHint = */
            PasswordEntryDialog.ForgotHintType.NONE,
        )
        dialogFragment.setTargetFragment(this, 0)
        if (oldPassphrase != null) {
            dialogFragment.setData(oldPassphrase)
        }
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_PASSPHRASE)
    }

    private fun updateMasterKeyPreferences() {
        val hasPassphrase = masterKeyManager.isProtectedWithPassphrase()
        masterKeyPreference.setSummary(
            if (hasPassphrase) {
                getString(R.string.click_here_to_change_passphrase)
            } else {
                getString(R.string.prefs_masterkey_passphrase)
            },
        )
        masterKeySwitchPreference.isChecked = hasPassphrase
    }

    private fun showPrivateChatsConfirmDialog() {
        val dialog = GenericAlertDialog.newInstance(
            /* title = */
            R.string.hide_chat,
            /* messageString = */
            getString(R.string.access_protection_private_chats_warning),
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
        )
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, DIALOG_TAG_UNHIDE_CHATS_CONFIRM)
    }

    override fun onYes(tag: String, text: String, isChecked: Boolean, data: Any?) {
        when (tag) {
            DIALOG_TAG_PIN -> if (preferenceService.setPin(text)) {
                lockMechanismPreference.setValue(PreferenceService.LockingMech_PIN)
                if (uiLockSwitchPreference.isChecked) {
                    preferenceService.setAppLockEnabled(true)
                }
                updateLockPreferences()
            } else {
                showToast(R.string.pin_invalid_not_set)
            }
            DIALOG_TAG_PASSPHRASE -> {
                val oldPassphrase = if (data != null) data as CharArray else null
                lifecycleScope.launch {
                    showProgressDialog(title = getString(R.string.setting_masterkey_passphrase))
                    // optimistically set the switch's state to true. It will get corrected later if the operation fails
                    masterKeySwitchPreference.isChecked = true
                    try {
                        withContext(dispatcherProvider.worker) {
                            masterKeyManager.setPassphrase(text.toCharArray(), oldPassphrase)
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to set passphrase", e)
                        showToast(R.string.an_error_occurred)
                    } finally {
                        hideProgressDialog()
                    }
                    updateMasterKeyPreferences()
                }
            }
        }
    }

    private fun showProgressDialog(title: String? = null) {
        GenericProgressDialog.newInstance(title, getString(R.string.please_wait))
            .show(parentFragmentManager, DIALOG_TAG_PROGRESS)
    }

    private fun hideProgressDialog() {
        DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_PROGRESS, true)
    }

    override fun onYes(tag: String, data: Any?) {
        when (tag) {
            DIALOG_TAG_PASSWORD_REMINDER_PIN -> showSetPinDialog()
            DIALOG_TAG_PASSWORD_REMINDER_PASSPHRASE -> startChangePassphraseActivity(oldPassphrase = null)
            DIALOG_TAG_UNHIDE_CHATS_CONFIRM -> removeAccessProtection()
        }
    }

    private fun removeAccessProtection() {
        logger.info("Removing locking mechanism and private marks")
        preferenceService.setPrivateChatsHidden(false)
        lifecycleScope.launch {
            try {
                if (conversationCategoryService.hasPrivateChats()) {
                    showProgressDialog()
                    withContext(dispatcherProvider.worker) {
                        removeAllPrivateMarksUseCase.call()
                    }
                }
                lockMechanismPreference.setValue(PreferenceService.LockingMech_NONE)
                updateLockPreferences()
            } catch (e: Exception) {
                logger.error("Failed to remove private marks", e)
                showToast(R.string.an_error_occurred)
            } finally {
                hideProgressDialog()
            }
        }
    }

    override fun getPreferenceTitleResource() = R.string.prefs_security

    override fun getPreferenceResource() = R.xml.preference_security

    companion object {
        private const val DIALOG_TAG_PASSPHRASE = "passphrase"
        private const val DIALOG_TAG_PROGRESS = "progress"
        private const val DIALOG_TAG_PIN = "pin"
        private const val DIALOG_TAG_UNHIDE_CHATS_CONFIRM = "unhide"
        private const val DIALOG_TAG_PASSWORD_REMINDER_PIN = "pin_reminder"
        private const val DIALOG_TAG_PASSWORD_REMINDER_PASSPHRASE = "passphrase_reminder"

        private const val GRACE_TIME_NEVER = "-1"
    }
}
