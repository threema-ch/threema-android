package ch.threema.app.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.services.UserService
import ch.threema.app.startup.finishAndRestartLaterIfNotReady
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.TriggerSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SMSVerificationLinkActivity")

class SMSVerificationLinkActivity : AppCompatActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val userService: UserService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishAndRestartLaterIfNotReady()) {
            return
        }

        lifecycleScope.launch {
            verifyMobileNumber()
            finish()
        }
    }

    private suspend fun verifyMobileNumber() {
        when (userService.getMobileLinkingState()) {
            UserService.LinkingState_PENDING -> {
                val code = intent.data?.getQueryParameter("code")
                if (code.isNullOrEmpty()) {
                    showToast(R.string.verify_failed_summary, ToastDuration.LONG)
                } else {
                    try {
                        withContext(dispatcherProvider.worker) {
                            userService.verifyMobileNumber(code, TriggerSource.LOCAL)
                        }
                        showToast(R.string.verify_success_text, ToastDuration.LONG)
                    } catch (e: Exception) {
                        logger.error("Failed to verify mobile number", e)
                        showToast(R.string.verify_failed_summary, ToastDuration.LONG)
                    }
                }
            }
            UserService.LinkingState_LINKED -> {
                showToast(R.string.verify_success_text, ToastDuration.LONG)
            }
            UserService.LinkingState_NONE -> {
                showToast(R.string.verify_failed_not_linked, ToastDuration.LONG)
            }
        }
    }
}
