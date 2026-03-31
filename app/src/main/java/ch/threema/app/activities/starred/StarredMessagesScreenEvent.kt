package ch.threema.app.activities.starred

sealed interface StarredMessagesScreenEvent {

    data object SelectedStarsRemoved : StarredMessagesScreenEvent

    data class ListItemSelectedToggled(
        val updatedSelectedListItemsCount: Int,
        val initiatedByLongClick: Boolean,
    ) : StarredMessagesScreenEvent
}
