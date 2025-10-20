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

package ch.threema.app.passphrase

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import ch.threema.app.AppConstants
import ch.threema.app.GlobalAppState
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.managers.ServiceManager
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.models.AppSystem
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.SimpleTextWatcher
import ch.threema.app.ui.SpacingValues
import ch.threema.app.ui.ThreemaTextInputEditText
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.EditTextUtil
import ch.threema.app.utils.bindExtra
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.buildIntent
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import ch.threema.common.DispatcherProvider
import ch.threema.common.consume
import ch.threema.localcrypto.MasterKeyManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = LoggingUtil.getThreemaLogger("PassphraseUnlockActivity")

// Note: This should NOT extend ThreemaToolbarActivity
class PassphraseUnlockActivity : ThreemaActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val masterKeyManager: MasterKeyManager by inject()
    private val startupMonitor: AppStartupMonitor by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private lateinit var passphraseText: ThreemaTextInputEditText
    private lateinit var passphraseLayout: TextInputLayout
    private lateinit var unlockButton: MaterialButton

    private val isCheckOnly by bindExtra { isCheckOnly() }
    private val shouldReturnPassphrase by bindExtra { shouldReturnPassphrase() }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_unlock_masterkey)
        findViewById<View>(R.id.top_view).applyDeviceInsetsAsPadding(InsetSides.all(), SpacingValues.horizontal(R.dimen.grid_unit_x2))

        findViewById<TextView>(R.id.unlock_info).let { infoText ->
            val array = theme.obtainStyledAttributes(intArrayOf(R.attr.colorOnSurface))
            infoText.compoundDrawables[0].setColorFilter(array.getColor(0, -1), PorterDuff.Mode.SRC_IN)
            array.recycle()
        }

        passphraseLayout = findViewById(R.id.passphrase_layout)
        passphraseText = findViewById(R.id.passphrase)
        passphraseText.addTextChangedListener(PasswordWatcher())
        passphraseText.setOnKeyListener { _: View?, keyCode: Int, event: KeyEvent ->
            when {
                event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER && isValidEntry(passphraseText) -> consume {
                    doUnlock()
                }
                else -> false
            }
        }
        passphraseText.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            when {
                actionId == EditorInfo.IME_ACTION_GO && isValidEntry(passphraseText) -> consume {
                    doUnlock()
                }
                else -> false
            }
        }

        unlockButton = findViewById(R.id.unlock_button)
        unlockButton.setOnClickListener { doUnlock() }
        unlockButton.isClickable = false
        unlockButton.setEnabled(false)
    }

    override fun onStart() {
        super.onStart()
        if (!isCheckOnly && !masterKeyManager.isLockedWithPassphrase()) {
            finish()
        }
    }

    private fun doUnlock() {
        unlockButton.isEnabled = false
        unlockButton.isClickable = false

        // Hide keyboard to make error message visible on low resolution displays
        EditTextUtil.hideSoftKeyboard(this.passphraseText)

        unlock(passphraseText.passphrase)
    }

    private inner class PasswordWatcher : SimpleTextWatcher() {
        override fun afterTextChanged(editable: Editable) {
            if (!passphraseText.text.isNullOrEmpty()) {
                passphraseLayout.error = null
            }
            val isValid = isValidEntry(passphraseText)
            unlockButton.isEnabled = isValid
            unlockButton.isClickable = isValid
        }
    }

    private fun isValidEntry(passphraseText: EditText) = passphraseText.text.length >= PASSPHRASE_MIN_LENGTH

    private fun unlock(passphrase: CharArray) {
        if (!isCheckOnly && !masterKeyManager.isLockedWithPassphrase()) {
            finish()
            return
        }
        showUnlockingDialog()

        lifecycleScope.launch {
            val isValid = withContext(dispatcherProvider.worker) {
                if (isCheckOnly) {
                    try {
                        masterKeyManager.checkPassphrase(passphrase)
                    } catch (e: Exception) {
                        logger.error("Failed to check master key passphrase", e)
                        false
                    }
                } else {
                    try {
                        masterKeyManager.unlockWithPassphrase(passphrase)
                    } catch (e: Exception) {
                        logger.error("Failed to unlock master key with passphrase", e)
                        false
                    }
                }
            }

            if (!isValid) {
                passphraseLayout.error = getString(R.string.invalid_passphrase)
                passphraseText.setText("")
            } else {
                if (!isCheckOnly) {
                    NotificationManagerCompat.from(applicationContext).cancelAll()
                    triggerConnection()
                }

                if (shouldReturnPassphrase) {
                    setResult(
                        RESULT_OK,
                        buildIntent {
                            putExtra(RESULT_PASSPHRASE, passphrase)
                        },
                    )
                } else {
                    // Clear the passphrase from memory
                    passphrase.fill(' ')
                    setResult(RESULT_OK)
                }
                finish()
            }
            hideUnlockingDialog()
        }
    }

    private fun showUnlockingDialog() {
        GenericProgressDialog.newInstance(R.string.masterkey_unlocking, R.string.please_wait).show(supportFragmentManager, DIALOG_TAG_UNLOCKING)
    }

    private fun hideUnlockingDialog() {
        DialogUtil.dismissDialog(supportFragmentManager, DIALOG_TAG_UNLOCKING, true)
    }

    private fun triggerConnection() {
        lifecycleScope.launch(dispatcherProvider.worker) {
            val lifetimeService = awaitServiceManager().lifetimeService
            if (GlobalAppState.isAppResumed) {
                lifetimeService.acquireConnection(AppConstants.ACTIVITY_CONNECTION_TAG)
            } else {
                lifetimeService.ensureConnection()
            }
        }
    }

    private suspend fun awaitServiceManager(): ServiceManager {
        startupMonitor.awaitSystem(AppSystem.SERVICE_MANAGER)
        return ThreemaApplication.requireServiceManager()
    }

    companion object {
        private const val DIALOG_TAG_UNLOCKING = "dtu"

        private const val PASSPHRASE_MIN_LENGTH = 8

        private const val EXTRA_PASSPHRASE_CHECK = "check"
        private const val EXTRA_RETURN_PASSPHRASE = "return_passphrase"

        private const val RESULT_PASSPHRASE = "passphrase_result"

        private fun Intent.isCheckOnly(): Boolean =
            getBooleanExtra(EXTRA_PASSPHRASE_CHECK, false)

        private fun Intent.shouldReturnPassphrase(): Boolean =
            getBooleanExtra(EXTRA_RETURN_PASSPHRASE, false)

        @JvmStatic
        fun createIntent(context: Context, checkOnly: Boolean = false, returnPassphrase: Boolean = false) =
            buildActivityIntent<PassphraseUnlockActivity>(context) {
                putExtra(EXTRA_PASSPHRASE_CHECK, checkOnly)
                putExtra(EXTRA_RETURN_PASSPHRASE, returnPassphrase)
            }

        @JvmStatic
        fun getPassphrase(intent: Intent): CharArray? =
            intent.getCharArrayExtra(RESULT_PASSPHRASE)
    }
}
