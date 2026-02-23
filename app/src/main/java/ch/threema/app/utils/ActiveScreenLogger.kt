package ch.threema.app.utils

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.slf4j.Logger

private class ActiveScreenLogger(private val logger: Logger) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        logger.info("Now visible")
    }

    override fun onStop(owner: LifecycleOwner) {
        logger.info("No longer visible")
    }
}

fun LifecycleOwner.logScreenVisibility(logger: Logger) {
    lifecycle.addObserver(ActiveScreenLogger(logger))
}
