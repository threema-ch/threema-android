/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
