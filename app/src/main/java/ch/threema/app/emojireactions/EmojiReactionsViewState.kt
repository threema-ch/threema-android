package ch.threema.app.emojireactions

import androidx.compose.runtime.Immutable
import ch.threema.data.models.EmojiReactionData

@Immutable
data class EmojiReactionsViewState(
    val emojiReactions: List<EmojiReactionData>,
)
