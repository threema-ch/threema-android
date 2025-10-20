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
import androidx.compose.runtime.Stable
import ch.threema.domain.models.IdentityState
import ch.threema.storage.models.ConversationModel

const val INACTIVE_CONTACT_ALPHA = 0.6f

@Immutable
data class ConversationNameStyle(
    val strikethrough: Boolean = false,
    val dimAlpha: Boolean = false,
) {
    companion object {
        @Stable
        fun inactiveContact() = ConversationNameStyle(
            strikethrough = false,
            dimAlpha = true,
        )

        @Stable
        fun invalidContact() = ConversationNameStyle(
            strikethrough = true,
            dimAlpha = false,
        )

        @Stable
        fun groupNotAMemberOf() = ConversationNameStyle(
            strikethrough = true,
            dimAlpha = false,
        )

        fun forConversationModel(conversationModel: ConversationModel): ConversationNameStyle =
            if (conversationModel.isContactConversation) {
                conversationModel.contact?.let { contact ->
                    when (contact.state) {
                        IdentityState.INACTIVE -> inactiveContact()
                        IdentityState.INVALID -> invalidContact()
                        else -> null
                    }
                } ?: ConversationNameStyle()
            } else {
                if (conversationModel.groupModel?.isMember() == false) {
                    groupNotAMemberOf()
                } else {
                    ConversationNameStyle()
                }
            }
    }
}
