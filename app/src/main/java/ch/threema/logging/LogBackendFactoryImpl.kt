package ch.threema.logging

import android.util.Log
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import ch.threema.app.ThreemaApplication
import ch.threema.common.isClassAvailable
import ch.threema.logging.backend.DebugLogFileBackend
import ch.threema.logging.backend.DebugLogFileManager
import ch.threema.logging.backend.DebugToasterBackend
import ch.threema.logging.backend.LogBackend
import ch.threema.logging.backend.LogcatBackend

class LogBackendFactoryImpl : LogBackendFactory {
    override fun getBackends(minLogLevel: Int): List<LogBackend> =
        buildList {
            if ((BuildConfig.DEBUG || BuildFlavor.current.isSandbox) && (!isInTest || isInDeviceTest)) {
                add(LogcatBackend(Log.VERBOSE))
            }
            if (BuildConfig.DEBUG && !isInTest) {
                add(DebugToasterBackend(ThreemaApplication.getAppContext()))
            }
            if (!isInTest) {
                add(DebugLogFileBackend(DebugLogFileManager(ThreemaApplication.getAppContext()), minLogLevel))
            }
        }

    private val isInTest by lazy {
        isClassAvailable("org.junit.Test")
    }

    private val isInDeviceTest by lazy {
        isClassAvailable("ch.threema.app.ThreemaTestRunner")
    }
}
