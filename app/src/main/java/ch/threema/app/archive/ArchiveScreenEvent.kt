package ch.threema.app.archive

import ch.threema.domain.types.Identity

sealed interface ArchiveScreenEvent {
    data object ConversationsUnarchived : ArchiveScreenEvent

    data object ConversationsDeleted : ArchiveScreenEvent

    data class ShowReallyDeleteConversationsDialog(
        val content: ReallyDeleteConversationsDialogContent,
    ) : ArchiveScreenEvent

    data class OpenGroupConversation(
        val groupDbId: Int,
    ) : ArchiveScreenEvent

    data class OpenDistributionListConversation(
        val distributionListId: Long,
    ) : ArchiveScreenEvent

    data class OpenOneToOneConversation(
        val identity: Identity,
    ) : ArchiveScreenEvent
}
