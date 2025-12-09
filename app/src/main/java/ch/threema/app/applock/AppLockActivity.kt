/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.applock

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import ch.threema.android.Toaster
import ch.threema.android.buildActivityIntent
import ch.threema.android.disableExitTransition
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.di.DIJavaCompat.isSessionScopeReady
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LockAppService
import ch.threema.app.utils.NavigationUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("AppLockActivity")

class AppLockActivity : ThreemaAppCompatActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val lockAppService: LockAppService by inject()
    private val appLockUtil: AppLockUtil by inject()

    private val isCheckOnly by lazy {
        intent.getBooleanExtra(INTENT_DATA_CHECK_ONLY, false)
    }
    private val authenticationType: String? by lazy {
        intent.getStringExtra(INTENT_DATA_AUTHENTICATION_TYPE)
            ?: preferenceService.getLockMechanism()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isSessionScopeReady()) {
            finish()
            return
        }
        setContentView(R.layout.activity_biometric_lock)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (!lockAppService.isLocked() && !isCheckOnly) {
            finish()
            return
        }

        lifecycleScope.launch {
            when (authenticationType) {
                PreferenceService.LockingMech_BIOMETRIC -> tryAuthenticateWithBiometrics()
                PreferenceService.LockingMech_SYSTEM -> tryAuthenticateWithSystemLock()
                else -> finish()
            }
        }
    }

    private suspend fun tryAuthenticateWithBiometrics() {
        when (val result = tryAuthenticate(authType = AppLockUtil.AuthType.BIOMETRIC)) {
            AppLockUtil.AuthenticationResult.Success -> finishWithSuccess()
            AppLockUtil.AuthenticationResult.CancelledByUser -> finishWithoutSuccess()
            is AppLockUtil.AuthenticationResult.SystemError -> {
                logger.warn(
                    "Failed to authenticate with biometrics (code={}, message={}), falling back to system lock",
                    result.code,
                    result.errorMessage,
                )
                tryAuthenticateWithSystemLock()
            }
        }
    }

    private suspend fun tryAuthenticateWithSystemLock() {
        when (val result = tryAuthenticate(authType = AppLockUtil.AuthType.ANY)) {
            AppLockUtil.AuthenticationResult.Success -> finishWithSuccess()
            AppLockUtil.AuthenticationResult.CancelledByUser -> finishWithoutSuccess()
            is AppLockUtil.AuthenticationResult.SystemError -> {
                if (!isCheckOnly && !appLockUtil.hasDeviceLock()) {
                    logger.warn("no lock screen available, disabling lock")
                    showToast(R.string.no_lockscreen_set, duration = Toaster.Duration.LONG)
                    lockAppService.unlock(null)
                    preferenceService.setLockMechanism(PreferenceService.LockingMech_NONE)
                    preferenceService.setPrivateChatsHidden(false)
                    finishWithSuccess()
                } else {
                    showToast("${result.errorMessage} (${result.code})")
                    finishWithoutSuccess()
                }
            }
        }
    }

    private suspend fun tryAuthenticate(authType: AppLockUtil.AuthType): AppLockUtil.AuthenticationResult =
        appLockUtil.authenticate(
            activity = this,
            title = getString(R.string.prefs_title_access_protection),
            subtitle = getString(R.string.biometric_enter_authentication),
            authType = authType,
        )

    private fun finishWithSuccess() {
        if (!isCheckOnly) {
            lockAppService.unlock(null)
        }
        setResult(RESULT_OK)
        finish()
    }

    private fun finishWithoutSuccess() {
        if (!isCheckOnly) {
            NavigationUtil.navigateToLauncher(this)
        }
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun finish() {
        super.finish()
        disableExitTransition()
    }

    companion object {

        @JvmStatic
        fun createIntent(
            context: Context,
            checkOnly: Boolean = false,
            authType: String? = null,
        ) = buildActivityIntent<AppLockActivity>(context) {
            if (authType != null) {
                putExtra(INTENT_DATA_AUTHENTICATION_TYPE, authType)
            }
            putExtra(INTENT_DATA_CHECK_ONLY, checkOnly)
        }

        private const val INTENT_DATA_AUTHENTICATION_TYPE = "auth_type"
        private const val INTENT_DATA_CHECK_ONLY = "check"
    }
}
