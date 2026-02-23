package ch.threema.app.startup

import ch.threema.common.await
import kotlinx.coroutines.flow.MutableStateFlow

object RemoteSecretMonitorRetryController {
    private val retryRequestFlow = MutableStateFlow(false)

    suspend fun awaitRetryRequest() {
        retryRequestFlow.await(true)
        retryRequestFlow.value = false
    }

    fun requestRetry() {
        retryRequestFlow.value = true
    }
}
