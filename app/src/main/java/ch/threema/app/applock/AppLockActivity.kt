package ch.threema.app.applock

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import ch.threema.android.ToastDuration
import ch.threema.android.buildActivityIntent
import ch.threema.android.disableExitTransition
import ch.threema.android.navigateToLauncher
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.di.DIJavaCompat.isSessionScopeReady
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LockAppService
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
                PreferenceService.LOCKING_MECH_BIOMETRIC -> tryAuthenticateWithBiometrics()
                PreferenceService.LOCKING_MECH_SYSTEM -> tryAuthenticateWithSystemLock()
                else -> finish()
            }
        }
    }

    private suspend fun tryAuthenticateWithBiometrics() {
        when (val result = tryAuthenticate(authType = AppLockUtil.AuthType.BIOMETRIC)) {
            AppLockUtil.AuthenticationResult.Success -> finishWithSuccess()
            AppLockUtil.AuthenticationResult.CancelledByUser -> finishWithoutSuccess()
            is AppLockUtil.AuthenticationResult.Error.MissingDeviceLock -> {
                // TODO(ANDR-4317): Consider this case for the fallback behaviour
                onError(result)
            }
            is AppLockUtil.AuthenticationResult.Error.Other -> {
                // TODO(ANDR-4317): Consider this case for the fallback behaviour
                logger.warn(
                    "Failed to authenticate with biometrics (code={}, message={}), falling back to system lock",
                    result.code,
                    result.message,
                )
                tryAuthenticateWithSystemLock()
            }
        }
    }

    private suspend fun tryAuthenticateWithSystemLock() {
        when (val result = tryAuthenticate(authType = AppLockUtil.AuthType.ANY)) {
            AppLockUtil.AuthenticationResult.Success -> finishWithSuccess()
            AppLockUtil.AuthenticationResult.CancelledByUser -> finishWithoutSuccess()
            is AppLockUtil.AuthenticationResult.Error -> onError(result)
        }
    }

    private fun onError(error: AppLockUtil.AuthenticationResult.Error) {
        when (error) {
            AppLockUtil.AuthenticationResult.Error.MissingDeviceLock -> {
                showToast(
                    message = R.string.no_lockscreen_set,
                    duration = ToastDuration.LONG,
                )
                if (isCheckOnly) {
                    finishWithoutSuccess()
                } else {
                    logger.warn("Disabling lock because the device is missing a system lock")
                    lockAppService.unlock(null)
                    preferenceService.setLockMechanism(PreferenceService.LOCKING_MECH_NONE)
                    preferenceService.setArePrivateChatsHidden(false)
                    finishWithSuccess()
                }
            }
            is AppLockUtil.AuthenticationResult.Error.Other -> {
                showToast("${error.message} (${error.message})")
                finishWithoutSuccess()
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
            navigateToLauncher()
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
        @JvmOverloads
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
