package ch.threema.app.applock

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import ch.threema.app.R
import ch.threema.base.utils.getThreemaLogger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private val logger = getThreemaLogger("AppLockUtil")

class AppLockUtil(private val appContext: Context) {

    /**
     *  Returns `true` if at least one device lock method (pin, pattern, face, fingerprint, ...) is currently configured.
     */
    fun hasDeviceLock(): Boolean =
        appContext.getSystemService<KeyguardManager>()?.isDeviceSecure == true

    fun getBiometricsAuthenticatorState(): BiometricsState {
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.USE_BIOMETRIC) != PackageManager.PERMISSION_GRANTED) {
            logger.info("Biometrics permission not granted")
            return BiometricsState.NO_PERMISSION
        }
        val biometricManager = BiometricManager.from(appContext)
        val authenticationStatus: Int = biometricManager.canAuthenticate(BIOMETRIC_WEAK)
        if (authenticationStatus != BiometricManager.BIOMETRIC_SUCCESS) {
            logger.info("Biometrics unavailable ({})", authenticationStatus)
        }
        return when (authenticationStatus) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricsState.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricsState.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricsState.NONE_ENROLLED
            else -> BiometricsState.OTHER
        }
    }

    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String, authType: AuthType): AuthenticationResult {
        if (!hasDeviceLock()) {
            logger.warn("No device lock configured")
            // While the biometric prompt callback offers the error codes ERROR_NO_DEVICE_CREDENTIAL or ERROR_NO_BIOMETRICS, we noticed that in some
            // cases the onAuthenticationError function was never called. So we check this here before launching the prompt in an invalid state
            return AuthenticationResult.Error.MissingDeviceLock
        }
        logger.info("Trying to authenticate with authType={}", authType)
        val executor = ContextCompat.getMainExecutor(activity)
        val authenticators: Int = getEffectiveAuthenticators(
            desiredAuthType = authType,
        )

        return suspendCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        logger.info("Successfully authenticated (auth type={})", result.authenticationType)
                        continuation.resume(AuthenticationResult.Success)
                    }

                    override fun onAuthenticationFailed() {
                        logger.info("Authentication failed")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            -> {
                                logger.info("User cancelled the authentication")
                                AuthenticationResult.CancelledByUser
                            }
                            else -> {
                                logger.error("Authentication error (code={}, string={})", errorCode, errString)
                                AuthenticationResult.Error.Other(
                                    message = errString,
                                    code = errorCode,
                                )
                            }
                        }
                        continuation.resume(result)
                    }
                },
            )

            val promptInfo = PromptInfo.Builder().apply {
                setTitle(title)
                setSubtitle(subtitle)
                setConfirmationRequired(false)
                setAllowedAuthenticators(authenticators)
                if ((authenticators and DEVICE_CREDENTIAL) == 0) {
                    setNegativeButtonText(activity.getString(R.string.cancel))
                }
            }
                .build()

            prompt.authenticate(promptInfo)
        }
    }

    private fun getEffectiveAuthenticators(desiredAuthType: AuthType): Int {
        val biometricsAuthenticatorState: BiometricsState = getBiometricsAuthenticatorState()
        return when (desiredAuthType) {
            AuthType.ANY ->
                if (biometricsAuthenticatorState == BiometricsState.AVAILABLE) {
                    DEVICE_CREDENTIAL or BIOMETRIC_WEAK
                } else {
                    getDeviceCredentialAuthenticatorCompat()
                }
            AuthType.BIOMETRIC ->
                if (biometricsAuthenticatorState == BiometricsState.AVAILABLE) {
                    BIOMETRIC_WEAK
                } else {
                    logger.warn("Biometrics not available, fallback to credentials")
                    getDeviceCredentialAuthenticatorCompat()
                }
        }
    }

    private fun getDeviceCredentialAuthenticatorCompat(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            DEVICE_CREDENTIAL
        } else {
            // Android SDK 29 and lower does not support using `DEVICE_CREDENTIALS` on its own
            DEVICE_CREDENTIAL or BIOMETRIC_WEAK
        }
    }

    enum class BiometricsState {
        NO_PERMISSION,
        NO_HARDWARE,
        NONE_ENROLLED,
        AVAILABLE,
        OTHER,
    }

    enum class AuthType {
        ANY,
        BIOMETRIC,
    }

    sealed interface AuthenticationResult {
        data object Success : AuthenticationResult

        data object CancelledByUser : AuthenticationResult

        sealed interface Error : AuthenticationResult {

            data object MissingDeviceLock : Error

            data class Other(val message: CharSequence, val code: Int) : Error
        }
    }
}
