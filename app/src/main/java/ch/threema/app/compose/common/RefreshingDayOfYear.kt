package ch.threema.app.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import ch.threema.common.TimeProvider
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

typealias LocalDayOfYear = Int

/**
 *  Produces a new state value if the current local day of the year changes.
 */
@Composable
fun rememberRefreshingLocalDayOfYear(timeProvider: TimeProvider = TimeProvider.default): State<LocalDayOfYear> =
    produceState(
        initialValue = timeProvider.getLocal().dayOfYear,
    ) {
        while (isActive) {
            // Tick on every new minute
            delay((60 - (timeProvider.get().epochSecond % 60)).seconds)
            value = timeProvider.getLocal().dayOfYear
        }
    }
