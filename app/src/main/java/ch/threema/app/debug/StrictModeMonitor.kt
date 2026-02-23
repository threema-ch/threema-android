package ch.threema.app.debug

import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.os.strictmode.Violation
import ch.threema.app.BuildConfig
import ch.threema.base.utils.getThreemaLogger
import java.util.concurrent.Executors

private val logger = getThreemaLogger("StrictModeMonitor")

object StrictModeMonitor {
    @JvmStatic
    fun enableIfNeeded() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }
        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyListener(Executors.newSingleThreadExecutor()) { v: Violation ->
                    logger.warn("STRICTMODE VMPolicy", v.cause)
                }
                .build(),
        )
    }
}
