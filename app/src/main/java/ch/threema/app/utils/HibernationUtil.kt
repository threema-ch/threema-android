package ch.threema.app.utils

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class HibernationUtil(
    private val appContext: Context,
) {
    /**
     * Checks whether the system supports the hibernation feature, i.e., whether it is able to
     * put apps into an inactive state and/or revoke its permissions if the app has not been used for a while.
     */
    suspend fun getHibernationStatus(): HibernationStatus = suspendCancellableCoroutine { continuation ->
        val future = PackageManagerCompat.getUnusedAppRestrictionsStatus(appContext)
        Futures.addCallback<Int?>(
            future,
            object : FutureCallback<Int> {
                override fun onSuccess(result: Int) {
                    when (result) {
                        UnusedAppRestrictionsConstants.DISABLED -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                continuation.resume(HibernationStatus.AVAILABLE_BUT_DISABLED)
                                return
                            } else {
                                continuation.resume(HibernationStatus.UNAVAILABLE)
                            }
                        }

                        UnusedAppRestrictionsConstants.ERROR,
                        UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE,
                        UnusedAppRestrictionsConstants.API_30_BACKPORT,
                        UnusedAppRestrictionsConstants.API_30,
                        ->
                            continuation.resume(HibernationStatus.UNAVAILABLE)

                        UnusedAppRestrictionsConstants.API_31 -> {
                            continuation.resume(HibernationStatus.AVAILABLE)
                        }
                    }
                }

                override fun onFailure(t: Throwable) {
                    continuation.resumeWithException(t)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    enum class HibernationStatus {
        AVAILABLE,
        AVAILABLE_BUT_DISABLED,
        UNAVAILABLE,
    }
}
