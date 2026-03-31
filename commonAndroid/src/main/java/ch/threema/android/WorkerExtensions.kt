package ch.threema.android

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkRequest
import androidx.work.WorkRequest.Builder
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

inline fun <reified T : ListenableWorker> buildOneTimeWorkRequest(
    block: OneTimeWorkRequest.Builder.() -> Unit = {},
): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<T>()
        .apply(block)
        .build()

inline fun <reified T : ListenableWorker> buildPeriodicWorkRequest(
    repeatInterval: Duration,
    block: PeriodicWorkRequest.Builder.() -> Unit = {},
): PeriodicWorkRequest =
    PeriodicWorkRequestBuilder<T>(repeatInterval.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .apply(block)
        .build()

fun <B : Builder<B, *>, W : WorkRequest> Builder<B, W>.setInitialDelay(delay: Duration) {
    setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}

fun <B : Builder<B, *>, W : WorkRequest> Builder<B, W>.setBackoffCriteria(backoffPolicy: BackoffPolicy, backoffDelay: Duration) {
    setBackoffCriteria(backoffPolicy, backoffDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
}

inline fun <B : Builder<B, *>, W : WorkRequest> Builder<B, W>.setConstraints(block: Constraints.Builder.() -> Unit) {
    setConstraints(buildConstraints(block))
}

inline fun <B : Builder<B, *>, W : WorkRequest> Builder<B, W>.setInputData(block: Data.Builder.() -> Unit) {
    setInputData(buildData(block))
}

inline fun buildConstraints(block: Constraints.Builder.() -> Unit) =
    Constraints.Builder()
        .apply(block)
        .build()

inline fun buildData(block: Data.Builder.() -> Unit): Data =
    Data.Builder()
        .apply(block)
        .build()
