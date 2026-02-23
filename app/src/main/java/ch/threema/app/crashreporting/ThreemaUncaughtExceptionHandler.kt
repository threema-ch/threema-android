package ch.threema.app.crashreporting

import android.content.Context
import ch.threema.app.BuildConfig
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.UUIDGenerator

private val logger = getThreemaLogger("ThreemaUncaughtExceptionHandler")

class ThreemaUncaughtExceptionHandler(
    private val appContext: Context,
) : Thread.UncaughtExceptionHandler {
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(t: Thread, e: Throwable) {
        logger.error("Uncaught exception", e)

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.CRASH_REPORTING_SUPPORTED) {
            storeExceptionForCrashReporting(e)
        }

        defaultHandler?.uncaughtException(t, e)
    }

    private fun storeExceptionForCrashReporting(e: Throwable) {
        // Intentionally not using Koin here, as it might not be initialized yet at this point
        ExceptionRecordStore(
            recordsDirectory = ExceptionRecordStore.getRecordsDirectory(appContext),
            timeProvider = TimeProvider.default,
            uuidGenerator = UUIDGenerator.default,
        )
            .storeException(e)
    }
}
