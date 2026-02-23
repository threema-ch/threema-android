package ch.threema.app.compose.conversation.models

import androidx.compose.runtime.Immutable
import ch.threema.app.voip.groupcall.LocalGroupId
import ch.threema.app.voip.groupcall.sfu.CallId
import ch.threema.common.now
import ch.threema.domain.types.TimestampUTC
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class GroupCallUiModel(
    val id: CallId,
    val groupId: LocalGroupId,
    val startedAt: TimestampUTC,
    val processedAt: TimestampUTC,
    val isJoined: Boolean,
) {

    /**
     * Get the duration the group call is running for
     */
    fun getCallDurationNow(): Duration = getDurationSinceStarted() ?: getDurationSinceProcessed()

    /**
     * @return The [Duration] since the group call has started, or `null` if the timestamp is in the future.
     */
    private fun getDurationSinceStarted(): Duration? {
        val currentTime: Long = now().time
        return if (currentTime >= startedAt) {
            (currentTime - startedAt).milliseconds
        } else {
            null
        }
    }

    /**
     * @return The [Duration] since the group call has been processed, or [Duration.ZERO] if the timestamp is in the future.
     */
    private fun getDurationSinceProcessed(): Duration {
        val currentTime: Long = now().time
        return if (currentTime >= processedAt) {
            (currentTime - processedAt).milliseconds
        } else {
            Duration.ZERO
        }
    }
}
