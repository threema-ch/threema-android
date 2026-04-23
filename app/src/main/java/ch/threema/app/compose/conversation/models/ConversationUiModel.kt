package ch.threema.app.compose.conversation.models

import androidx.compose.runtime.Immutable
import ch.threema.android.ResolvableString
import ch.threema.app.usecases.conversations.AvatarIteration
import ch.threema.app.utils.TextUtil
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.DistributionListReceiverIdentifier
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.domain.types.ConversationUID
import ch.threema.domain.types.Identity
import ch.threema.storage.models.MessageType
import java.util.Date

/**
 *  @property icon In case of a contact conversation, this icon indicates the state of the latest message. In case of a group- or distribution
 *  list conversation it just holds a static icon indicating the conversation type.
 *  See [ch.threema.app.usecases.conversations.WatchConversationListItemsUseCase.getConversationIconOrNull]
 *
 *  @property avatarIteration A simple integer that can be used to invalidate the currently drawn conversation avatar. The avatar images are stored
 *  with just the contacts identity or the groups id in the filename. So if the avatar changes while this [ConversationUiModel] is displayed, there
 *  is no compose-way of triggering a redraw, because the filename stayed the same. So in order to have the avatar composable redraw, we have to
 *  change this value. The actual number has no meaning.
 */
@Immutable
sealed interface ConversationUiModel {

    val conversationUID: ConversationUID
    val receiverIdentifier: ReceiverIdentifier
    val latestMessageData: LatestMessageData?
    val receiverDisplayName: String?
    val conversationName: String
    val conversationNameStyle: ConversationNameStyle
    val draftData: DraftData?
    val unreadState: UnreadState?
    val isPinned: Boolean
    val isPrivate: Boolean
    val icon: IconInfo?
    val muteStatusIcon: Int?
    val avatarIteration: AvatarIteration

    @Immutable
    data class ContactConversation(
        override val conversationUID: ConversationUID,
        override val receiverIdentifier: ContactReceiverIdentifier,
        override val latestMessageData: LatestMessageData?,
        override val receiverDisplayName: String?,
        override val conversationName: String,
        override val conversationNameStyle: ConversationNameStyle,
        override val draftData: DraftData?,
        override val unreadState: UnreadState?,
        override val isPinned: Boolean,
        override val isPrivate: Boolean,
        override val icon: IconInfo?,
        override val muteStatusIcon: Int?,
        override val avatarIteration: AvatarIteration,
        val showWorkBadge: Boolean,
        val isTyping: Boolean,
        val availabilityStatus: AvailabilityStatus?,
    ) : ConversationUiModel

    @Immutable
    data class GroupConversation(
        override val conversationUID: ConversationUID,
        override val receiverIdentifier: GroupReceiverIdentifier,
        override val latestMessageData: LatestMessageData?,
        override val receiverDisplayName: String?,
        override val conversationName: String,
        override val conversationNameStyle: ConversationNameStyle,
        override val draftData: DraftData?,
        override val unreadState: UnreadState?,
        override val isPinned: Boolean,
        override val isPrivate: Boolean,
        override val muteStatusIcon: Int?,
        override val icon: IconInfo?,
        override val avatarIteration: AvatarIteration,
        val latestMessageSenderName: ResolvableString?,
        val groupCall: GroupCallUiModel?,
    ) : ConversationUiModel

    @Immutable
    data class DistributionListConversation(
        override val conversationUID: ConversationUID,
        override val receiverIdentifier: DistributionListReceiverIdentifier,
        override val latestMessageData: LatestMessageData?,
        override val receiverDisplayName: String?,
        override val conversationName: String,
        override val conversationNameStyle: ConversationNameStyle,
        override val draftData: DraftData?,
        override val unreadState: UnreadState?,
        override val isPinned: Boolean,
        override val isPrivate: Boolean,
        override val muteStatusIcon: Int?,
        override val icon: IconInfo?,
        override val avatarIteration: AvatarIteration,
    ) : ConversationUiModel

    fun matchesFilterQuery(query: String?): Boolean {
        if (query == null) {
            return true
        }
        return TextUtil.matchesQueryDiacriticInsensitive(receiverDisplayName, query)
    }

    /**
     * Can be marked as [Immutable] because we guarantee that we never use deprecated setter functions from [java.util.Date] on [postedAt] and
     * [modifiedAt].
     *
     * @param postedAt     *Never* mutate value using the deprecated setter functions from [java.util.Date]
     * @param modifiedAt   *Never* mutate value using the deprecated setter functions from [java.util.Date]
     * @param mentionNames A map containing the [ResolvableString]s for all potential mentions in [body] or [caption]
     */
    @Immutable
    data class LatestMessageData(
        val type: MessageType,
        val body: String?,
        val caption: String?,
        val isOutbox: Boolean,
        val isDeleted: Boolean,
        val postedAt: Date?,
        val modifiedAt: Date?,
        val mentionNames: Map<Identity, ResolvableString>,
    )

    /**
     * @param mentionNames A map containing the [ResolvableString]s for all potential mentions in [draft].
     */
    @Immutable
    data class DraftData(
        val draft: String,
        val mentionNames: Map<Identity, ResolvableString>,
    )
}
