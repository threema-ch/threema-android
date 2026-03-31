package ch.threema.app.activities.starred.models

import androidx.compose.runtime.Immutable
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.data.datatypes.ContactNameFormat

@Immutable
data class StarredMessagesViewState(
    val isLoading: Boolean,
    val query: String?,
    @PreferenceService.StarredMessagesSortOrder val sortOrder: Int,
    val contactNameFormat: ContactNameFormat,
    @EmojiStyle val emojiStyle: Int,
    val listItems: List<StarredMessageListItemUiModel>,
)
