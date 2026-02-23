package ch.threema.logging

import android.util.Log
import androidx.annotation.IntDef

/**
 * All valid log levels.
 *
 * Maps directly to Android Logcat log levels.
 */
@IntDef(
    Log.VERBOSE,
    Log.DEBUG,
    Log.INFO,
    Log.WARN,
    Log.ERROR,
)
annotation class LogLevel
