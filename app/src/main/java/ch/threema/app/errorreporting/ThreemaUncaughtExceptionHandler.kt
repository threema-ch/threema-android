package ch.threema.app.errorreporting

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

        @Suppress("KotlinConstantConditions", "SimplifyBooleanWithConstants")
        if (BuildConfig.ERROR_REPORTING_SUPPORTED && BuildConfig.SENTRY_PUBLIC_API_KEY.isNotEmpty()) {
            storeExceptionForErrorReporting(e)
        }

        defaultHandler?.uncaughtException(t, e)
    }

    private fun storeExceptionForErrorReporting(e: Throwable) {
        // Intentionally not using Koin here, as it might not be initialized yet at this point
        ErrorRecordStore(
            recordsDirectory = ErrorRecordStore.getRecordsDirectory(appContext),
            timeProvider = TimeProvider.default,
            uuidGenerator = UUIDGenerator.default,
        )
            .storeErrorForUnhandledException(e)
    }
}
