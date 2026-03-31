package ch.threema.app.activities.starred.models

import androidx.compose.runtime.Immutable

@Immutable
data class StarredMessageListItemUiModel(
    val starredMessageUiModel: StarredMessageUiModel,
    val isSelected: Boolean,
)
