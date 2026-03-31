package ch.threema.app.activities.starred.models

import androidx.compose.runtime.Immutable
import ch.threema.android.ResolvableString
import ch.threema.app.usecases.groups.GroupDisplayName
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.types.Identity
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.AbstractMessageModel

/**
 *  Can be marked as immutable because we never mutate [messageModel].
 */
@Immutable
sealed interface StarredMessageUiModel {

    val uid: MessageUid
    val messageModel: AbstractMessageModel
    val messageContent: ResolvableString?
    val mentionNames: Map<Identity, ResolvableString>
    val sender: ConversationParticipant
    val isPrivate: Boolean

    @Immutable
    data class StarredContactMessage(
        override val uid: MessageUid,
        override val messageModel: AbstractMessageModel,
        override val messageContent: ResolvableString?,
        override val mentionNames: Map<Identity, ResolvableString>,
        override val sender: ConversationParticipant,
        override val isPrivate: Boolean,
        val receiver: ConversationParticipant,
        val showWorkBadge: Boolean,
    ) : StarredMessageUiModel

    @Immutable
    data class StarredGroupMessage(
        override val uid: MessageUid,
        override val messageModel: AbstractMessageModel,
        override val messageContent: ResolvableString?,
        override val mentionNames: Map<Identity, ResolvableString>,
        override val sender: ConversationParticipant,
        override val isPrivate: Boolean,
        val groupIdentifier: GroupReceiverIdentifier,
        val groupDisplayName: GroupDisplayName?,
    ) : StarredMessageUiModel
}

sealed interface ConversationParticipant {

    val identity: Identity

    data class Contact(
        override val identity: Identity,
        val firstname: String?,
        val lastname: String?,
    ) : ConversationParticipant

    data class Me(
        override val identity: Identity,
    ) : ConversationParticipant
}
