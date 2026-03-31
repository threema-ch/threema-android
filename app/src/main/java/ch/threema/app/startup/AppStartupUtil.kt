package ch.threema.app.startup

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyManager
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.android.ext.android.get

private val logger = getThreemaLogger("AppStartupUtil")

/**
 * Checks whether the app still needs to get ready (e.g. by wrapping up database migrations) before the
 * calling activity can be displayed.
 *
 * Must be called from an activity's onCreate method, as early as possibly but after the super class's onCreate was called.
 * If true is returned, the calling activity must immediately stop its own initialization and must not access any services.
 * In this case the current activity will be finished and instead the [AppStartupActivity] will be shown.
 * Once the app is ready, the calling activity will be recreated.
 */
fun Activity.finishAndRestartLaterIfNotReady(): Boolean {
    if (isFinishing) {
        // If the activity is already finishing, there's no point in waiting or recreating the activity later
        return true
    }
    if (get<AppStartupMonitor>().isReady()) {
        return false
    }

    startActivity(AppStartupActivity.createIntent(this, intent))
    finish()
    return true
}

/**
 * TODO(ANDR-4389): This is a workaround that should be used sparingly.
 * Can be called from an activity's onCreate method to wait for the app to become ready.
 * This can be used instead of [finishAndRestartLaterIfNotReady] in places where restarting the activity will lead to loss of functionality,
 * such as when sharing files, where read access would be lost upon finishing the original activity.
 * Activities that make use of this should implement the [AppStartupAware] interface.
 */
fun AppCompatActivity.waitUntilReady(onReady: () -> Unit) {
    val startupMonitor = get<AppStartupMonitor>()
    if (startupMonitor.isReady()) {
        onReady()
        return
    }
    lifecycleScope.launch {
        val masterKeyManager = get<MasterKeyManager>()
        if (!masterKeyManager.isProtected()) {
            try {
                // Wait for the app to become ready, but if it takes too long, we give up and fall through
                // such that AppStartupActivity takes over, which provides a better UX.
                logger.info("App not yet ready, waiting before proceeding")
                withTimeout(timeout = 5.seconds) {
                    startupMonitor.awaitAll()
                }
                logger.info("App is now ready")
                onReady()
                return@launch
            } catch (_: TimeoutCancellationException) {
                logger.warn("App took more than 5 seconds to become ready")
            }
        }

        // If the master key is protected (with a passphrase or remote secret), or if the app takes too long to become
        // ready (e.g. due to long-running database updates), we switch over to AppStartupActivity to display the appropriate UI
        finishAndRestartLaterIfNotReady()
    }
}
