package ch.threema.app.startup

import android.app.Activity
import org.koin.android.ext.android.get

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
