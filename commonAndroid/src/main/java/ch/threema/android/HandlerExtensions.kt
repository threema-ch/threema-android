package ch.threema.android

import android.os.Handler
import kotlin.time.Duration

fun Handler.postDelayed(delay: Duration, runnable: Runnable) {
    postDelayed(runnable, delay.inWholeMilliseconds)
}
