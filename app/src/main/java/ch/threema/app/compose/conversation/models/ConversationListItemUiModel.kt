package ch.threema.app.compose.conversation.models

import androidx.compose.runtime.Immutable

@Immutable
data class ConversationListItemUiModel(
    val model: ConversationUiModel,
    val isChecked: Boolean = false,
    val isHighlighted: Boolean = false,
)
