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

package ch.threema.app.drafts

import androidx.annotation.AnyThread
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.ConversationUniqueId

@AnyThread
interface DraftManager {
    /**
     * Returns the draft for a conversation, or null if there is no draft.
     * If there is a draft, its text is guaranteed to be non-blank.
     */
    fun get(conversationUniqueId: ConversationUniqueId): MessageDraft?

    fun set(conversationUniqueId: ConversationUniqueId, text: String?) {
        set(conversationUniqueId, text, quotedMessageId = null)
    }

    /**
     * Stores a draft for a conversation. If [text] is null or blank, the draft will be removed instead.
     */
    fun set(conversationUniqueId: ConversationUniqueId, text: String?, quotedMessageId: MessageId?)

    fun remove(conversationUniqueId: ConversationUniqueId)
}
