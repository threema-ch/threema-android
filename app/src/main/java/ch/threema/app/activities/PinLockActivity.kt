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

package ch.threema.app.activities

import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import ch.threema.android.buildActivityIntent
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.ThreemaApplication.Companion.getServiceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LockAppService
import ch.threema.app.ui.InsetSides.Companion.all
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.EditTextUtil
import ch.threema.app.utils.NavigationUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.consume
import ch.threema.common.minus
import ch.threema.common.plus
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("PinLockActivity")

class PinLockActivity : ThreemaActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val lockAppService: LockAppService by inject()
    private val preferenceService: PreferenceService by inject()
    private val timeProvider: TimeProvider by inject()

    private lateinit var passwordEntry: TextView
    private lateinit var errorTextView: TextView

    private val isCheckOnly by lazy(LazyThreadSafetyMode.NONE) {
        intent.getBooleanExtra(INTENT_DATA_CHECK_ONLY, false)
    }

    private var failedAttempts: Int
        get() = preferenceService.lockoutAttempts
        set(value) {
            preferenceService.lockoutAttempts = value
        }
    private var lockoutDeadline: Instant?
        get() = (preferenceService.lockoutDeadline)
            ?.let { deadline ->
                val now = timeProvider.get()
                deadline
                    .takeIf { it > now }
                    ?.coerceAtMost(now + LOCKOUT_TIMEOUT)
            }
        set(value) {
            preferenceService.lockoutDeadline = value
        }

    private var errorResetJob: Job? = null
    private var countdownJob: Job? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (getServiceManager() == null) {
            finish()
            return
        }

        if (!lockAppService.isLocked && !isCheckOnly) {
            finish()
            return
        }

        setContentView(R.layout.activity_pin_lock)
        findViewById<View>(R.id.topFrame).applyDeviceInsetsAsPadding(all())
        passwordEntry = findViewById(R.id.password_entry)
        errorTextView = findViewById(R.id.errorText)

        passwordEntry.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
            when (actionId) {
                EditorInfo.IME_NULL, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_NEXT -> consume {
                    onSubmit()
                }
                else -> false
            }
        }
        passwordEntry.setFilters(arrayOf<InputFilter>(LengthFilter(AppConstants.MAX_PIN_LENGTH)))
        passwordEntry.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        findViewById<Button>(R.id.cancelButton).setOnClickListener { quit() }
    }

    private fun onSubmit() {
        val pin = passwordEntry.text.toString()
        if (lockAppService.unlock(pin)) {
            EditTextUtil.hideSoftKeyboard(passwordEntry)

            setResult(RESULT_OK)
            failedAttempts = 0
            lockoutDeadline = null
            finish()
        } else {
            failedAttempts++

            if (failedAttempts > MAX_FAILED_ATTEMPTS) {
                lockoutDeadline = timeProvider.get() + LOCKOUT_TIMEOUT
                handleAttemptLockout()
            } else {
                showError(getString(R.string.pinentry_wrong_pin), resetTimeout = ERROR_MESSAGE_TIMEOUT)
            }

            if (isCheckOnly) {
                passwordEntry.isEnabled = false

                lifecycleScope.launch {
                    delay(1.seconds)
                    quit()
                }
            }
        }
    }

    public override fun onResume() {
        super.onResume()

        if (!lockAppService.isLocked && !isCheckOnly) {
            finish()
            return
        }

        handleAttemptLockout()
    }
    public override fun onPause() {
        super.onPause()
        countdownJob?.cancel()
        countdownJob = null
        overridePendingTransition(0, 0)
    }

    private fun handleAttemptLockout() {
        val deadline = lockoutDeadline ?: return
        passwordEntry.isEnabled = false

        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            var seconds = (deadline - timeProvider.get()).inWholeSeconds
            while (seconds > 0) {
                showError(getString(R.string.too_many_incorrect_attempts, seconds.toString()))
                delay(1.seconds)
                seconds--
            }

            passwordEntry.isEnabled = true
            errorTextView.text = ""
            failedAttempts = 0
        }
    }

    private fun showError(errorMessage: CharSequence, resetTimeout: Duration? = null) {
        errorTextView.text = errorMessage
        errorTextView.announceForAccessibility(errorTextView.text)
        passwordEntry.text = null

        errorResetJob?.cancel()
        resetTimeout?.let {
            errorResetJob = lifecycleScope.launch {
                delay(resetTimeout)
                errorTextView.text = ""
            }
        }
    }

    override fun isPinLockable() = false

    override fun enableOnBackPressedCallback() = true

    override fun handleOnBackPressed() {
        quit()
    }

    private fun quit() {
        EditTextUtil.hideSoftKeyboard(passwordEntry)

        if (isCheckOnly) {
            setResult(RESULT_CANCELED)
            finish()
        } else {
            NavigationUtil.navigateToLauncher(this)
        }
    }

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 3
        private val ERROR_MESSAGE_TIMEOUT = 3.seconds
        private val LOCKOUT_TIMEOUT = 30.seconds

        private const val INTENT_DATA_CHECK_ONLY = "check"

        @JvmStatic
        @JvmOverloads
        fun createIntent(context: Context, checkOnly: Boolean = false) = buildActivityIntent<PinLockActivity>(context) {
            putExtra(INTENT_DATA_CHECK_ONLY, checkOnly)
        }
    }
}
