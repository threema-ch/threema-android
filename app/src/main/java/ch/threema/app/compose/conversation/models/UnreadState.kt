package ch.threema.app.compose.conversation.models

import androidx.compose.runtime.Stable

@Stable
sealed interface UnreadState {
    @Stable
    data class Messages(val count: Long) : UnreadState

    @Stable
    data object JustMarked : UnreadState
}
