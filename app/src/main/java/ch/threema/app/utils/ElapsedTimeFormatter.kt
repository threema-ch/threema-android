package ch.threema.app.utils

import android.content.Context
import ch.threema.app.R
import ch.threema.common.toHMMSS
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object ElapsedTimeFormatter {
    @JvmStatic
    fun secondsToString(seconds: Long): String =
        seconds.seconds.toHMMSS()

    @JvmStatic
    fun millisecondsToString(milliseconds: Long): String =
        milliseconds.milliseconds.toHMMSS()

    @JvmStatic
    @Deprecated("Does not take locale's grammar such as word order or plurality into account, thus may produce wrong results.")
    fun getDurationStringHuman(context: Context, fullSeconds: Long): String =
        with(context) {
            fullSeconds.seconds.toComponents { minutes, seconds, _ ->
                if (minutes == 0L) {
                    "$seconds ${getString(R.string.seconds)}"
                } else {
                    "$minutes ${getString(R.string.minutes)} ${getString(R.string.and)} $seconds ${getString(R.string.seconds)}"
                }
            }
        }
}
