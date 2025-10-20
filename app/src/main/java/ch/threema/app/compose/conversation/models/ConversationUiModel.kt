/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.compose.conversation.models

import androidx.compose.runtime.Immutable
import ch.threema.app.compose.common.ResolvableString
import ch.threema.app.utils.TextUtil
import ch.threema.domain.types.ConversationUID
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.ReceiverModel

/**
 *  While this interface uses the mutable classes [AbstractMessageModel] and [receiverModel], we mark it as [Immutable] nevertheless.
 *  This means, that we trust the user of this ui model for now, that there will be no mutations.
 *
 *  // TODO(ANDR-4088): Remove both the usage of AbstractMessageModel and ReceiverModel
 */
@Immutable
sealed interface ConversationUiModel {

    val conversationUID: ConversationUID
    val latestMessage: AbstractMessageModel?
    val receiverDisplayName: String?
    val conversationName: String
    val conversationNameStyle: ConversationNameStyle
    val draft: String?
    val latestMessageStateIcon: IconInfo?
    val unreadState: UnreadState?
    val isPinned: Boolean
    val isPrivate: Boolean
    val muteStatusIcon: Int?
    val receiverModel: ReceiverModel

    @Immutable
    data class ContactConversation(
        override val conversationUID: ConversationUID,
        override val latestMessage: AbstractMessageModel?,
        override val receiverModel: ContactModel,
        override val receiverDisplayName: String?,
        override val conversationName: String,
        override val conversationNameStyle: ConversationNameStyle,
        override val draft: String?,
        override val latestMessageStateIcon: IconInfo?,
        override val unreadState: UnreadState?,
        override val isPinned: Boolean,
        override val isPrivate: Boolean,
        override val muteStatusIcon: Int?,
        val showWorkBadge: Boolean,
        val isTyping: Boolean,
    ) : ConversationUiModel

    @Immutable
    data class GroupConversation(
        override val conversationUID: ConversationUID,
        override val latestMessage: AbstractMessageModel?,
        override val receiverModel: GroupModel,
        override val receiverDisplayName: String?,
        override val conversationName: String,
        override val conversationNameStyle: ConversationNameStyle,
        override val draft: String?,
        override val latestMessageStateIcon: IconInfo?,
        override val unreadState: UnreadState?,
        override val isPinned: Boolean,
        override val isPrivate: Boolean,
        override val muteStatusIcon: Int?,
        val latestMessageSenderName: ResolvableString?,
        val groupCall: GroupCallUiModel?,
    ) : ConversationUiModel

    @Immutable
    data class DistributionListConversation(
        override val conversationUID: ConversationUID,
        override val latestMessage: AbstractMessageModel?,
        override val receiverModel: DistributionListModel,
        override val receiverDisplayName: String?,
        override val conversationName: String,
        override val conversationNameStyle: ConversationNameStyle,
        override val draft: String?,
        override val latestMessageStateIcon: IconInfo?,
        override val unreadState: UnreadState?,
        override val isPinned: Boolean,
        override val isPrivate: Boolean,
        override val muteStatusIcon: Int?,
    ) : ConversationUiModel

    fun matchesFilterQuery(query: String): Boolean =
        TextUtil.matchesQueryDiacriticInsensitive(receiverDisplayName, query)
}
