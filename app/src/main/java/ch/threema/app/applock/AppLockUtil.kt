/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

class AppLockUtil(
    private val appContext: Context,
) {
    fun hasDeviceLock(): Boolean =
        appContext.getSystemService<KeyguardManager>()?.isDeviceSecure == true

    fun checkBiometrics(): BiometricsState {
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.USE_BIOMETRIC) != PackageManager.PERMISSION_GRANTED) {
            return BiometricsState.NO_PERMISSION
        }
        val biometricManager = BiometricManager.from(appContext)
        return when (biometricManager.canAuthenticate(BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricsState.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            -> BiometricsState.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricsState.NOT_ENROLLED
            else -> BiometricsState.OTHER
        }
    }

    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String, authType: AuthType): AuthenticationResult {
        logger.info("Trying to authenticate with authType={}", authType)
        val executor = ContextCompat.getMainExecutor(activity)
        val authenticators = when (authType) {
            AuthType.ANY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    when (checkBiometrics()) {
                        BiometricsState.AVAILABLE -> DEVICE_CREDENTIAL or BIOMETRIC_WEAK
                        else -> DEVICE_CREDENTIAL
                    }
                } else {
                    // Android SDK 29 and lower does not support using `DEVICE_CREDENTIALS` on its own
                    DEVICE_CREDENTIAL or BIOMETRIC_WEAK
                }
            }
            AuthType.BIOMETRIC -> BIOMETRIC_WEAK
        }
        return suspendCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        logger.info("successfully authenticated (auth type={})", result.authenticationType)
                        continuation.resume(AuthenticationResult.Success)
                    }

                    override fun onAuthenticationFailed() {
                        logger.info("biometric authentication failed")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            -> {
                                logger.info("user cancelled authentication")
                                continuation.resume(AuthenticationResult.CancelledByUser)
                            }
                            else -> {
                                logger.warn("authentication error (code={}, string={})", errorCode, errString)
                                continuation.resume(AuthenticationResult.SystemError(errString.toString(), errorCode))
                            }
                        }
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

    enum class BiometricsState {
        NO_PERMISSION,
        NO_HARDWARE,
        NOT_ENROLLED,
        AVAILABLE,
        OTHER,
    }

    enum class AuthType {
        ANY,
        BIOMETRIC,
    }

    sealed class AuthenticationResult {
        data object Success : AuthenticationResult()

        data object CancelledByUser : AuthenticationResult()

        data class SystemError(val errorMessage: String, val code: Int) : AuthenticationResult()
    }
}
