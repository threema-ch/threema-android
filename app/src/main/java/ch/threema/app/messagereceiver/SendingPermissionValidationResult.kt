package ch.threema.app.messagereceiver

import androidx.annotation.StringRes

sealed class SendingPermissionValidationResult(val isValid: Boolean) {
    val isDenied: Boolean = !isValid

    data object Valid : SendingPermissionValidationResult(isValid = true)

    data class Denied(@StringRes val errorResId: Int? = null) :
        SendingPermissionValidationResult(isValid = false)
}
