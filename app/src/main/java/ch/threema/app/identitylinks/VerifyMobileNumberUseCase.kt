package ch.threema.app.identitylinks

import ch.threema.android.ResolvableString
import ch.threema.android.ResourceIdString
import ch.threema.app.R
import ch.threema.app.services.UserService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.domain.taskmanager.TriggerSource
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("VerifyMobileNumberUseCase")

class VerifyMobileNumberUseCase(
    private val userService: UserService,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(verificationCode: String?): VerificationResult {
        when (userService.getMobileLinkingState()) {
            UserService.LinkingState_PENDING -> {
                if (!isValidCodeFormat(verificationCode)) {
                    return VerificationResult.Failure(errorMessage = ResourceIdString(R.string.verify_failed_summary))
                }
                try {
                    withContext(dispatcherProvider.worker) {
                        userService.verifyMobileNumber(verificationCode, TriggerSource.LOCAL)
                    }
                    return VerificationResult.Success
                } catch (e: Exception) {
                    logger.error("Failed to verify mobile number", e)
                    return VerificationResult.Failure(errorMessage = ResourceIdString(R.string.verify_failed_summary))
                }
            }

            UserService.LinkingState_LINKED -> {
                return VerificationResult.Failure(errorMessage = ResourceIdString(resId = R.string.verify_success_text))
            }

            UserService.LinkingState_NONE -> {
                return VerificationResult.Failure(errorMessage = ResourceIdString(resId = R.string.verify_failed_not_linked))
            }
        }

        return VerificationResult.Success
    }

    private fun isValidCodeFormat(verificationCode: String?): Boolean {
        if (verificationCode.isNullOrBlank()) {
            return false
        }

        return verificationCode.length <= MAX_DIGITS && verificationCode.all(Char::isDigit)
    }

    sealed interface VerificationResult {
        object Success : VerificationResult
        data class Failure(val errorMessage: ResolvableString) : VerificationResult
    }

    companion object {
        private const val MAX_DIGITS = 8
    }
}
