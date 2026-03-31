package ch.threema.app.archive

import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.IdentityString

sealed interface ArchiveScreenEvent {
    data object ConversationsUnarchived : ArchiveScreenEvent

    data object ConversationsDeleted : ArchiveScreenEvent

    data class ShowReallyDeleteConversationsDialog(
        val content: ReallyDeleteConversationsDialogContent,
    ) : ArchiveScreenEvent

    data class OpenGroupConversation(
        val groupDatabaseId: GroupDatabaseId,
    ) : ArchiveScreenEvent

    data class OpenDistributionListConversation(
        val distributionListId: Long,
    ) : ArchiveScreenEvent

    data class OpenOneToOneConversation(
        val identity: IdentityString,
    ) : ArchiveScreenEvent
}
