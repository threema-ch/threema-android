package ch.threema.app.fragments.conversations

import androidx.compose.runtime.Immutable
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat

/**
 *  @param itemsState                 See [ItemsState.Loaded] and [ItemsState.Failed].
 *  @param hidePrivateConversations   Reflects the user setting `arePrivateChatsHidden`. If this is `true`, all private marked conversations are
 *  excluded from visible items.
 *  @param hasPrivateConversations    Is `true` if at least one private-marked conversation exist that is not archived.
 *  @param archivedConversationsCount The count of all archived conversations that match an optional [filterQuery]. The count respects the users
 *  @param availabilityStatus         The [AvailabilityStatus] of the user
 *  setting to hide private conversations. If private conversations should be hidden, these will never be counted.
 */
@Immutable
data class ConversationsViewState(
    val itemsState: ItemsState,
    val filterQuery: String?,
    val hidePrivateConversations: Boolean,
    val hasPrivateConversations: Boolean,
    val archivedConversationsCount: Long,
    val contactNameFormat: ContactNameFormat,
    val availabilityStatus: AvailabilityStatus,
)

@Immutable
sealed interface ItemsState {

    /**
     * The [items] loaded and are display-ready. Meaning that this list respects the filter-query and the user setting `arePrivateChatsHidden`
     */
    @Immutable
    data class Loaded(val items: List<ConversationListItemUiModel>) : ItemsState

    /**
     * Loading the items failed without the possibility to use reasonable fallback values
     */
    @Immutable
    data object Failed : ItemsState
}
