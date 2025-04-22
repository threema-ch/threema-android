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

package ch.threema.app.services

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.data.models.ContactModel
import ch.threema.protobuf.d2d.sync.MdD2DSync.ConversationCategory

/**
 * The conversation category service is used to keep track of private chats. It manages reflection if MD is enabled.
 *
 * TODO(ANDR-3010): Move the conversation category into the database.
 */
interface ConversationCategoryService {
    /* Contact related methods */

    fun markContactChatAsPrivate(contactModel: ContactModel)

    fun removePrivateMarkFromContactChat(contactModel: ContactModel)

    fun removePrivateMarkFromContactChat(contactModel: ch.threema.storage.models.ContactModel)

    /* Group related methods */

    fun markGroupChatAsPrivate(groupDatabaseId: Long)

    fun removePrivateMarkFromGroupChat(groupDatabaseId: Long)

    fun isPrivateGroupChat(groupDatabaseId: Long): Boolean

    /* General methods */

    fun isPrivateChat(uniqueIdString: String): Boolean

    fun getConversationCategory(uniqueIdString: String): ConversationCategory

    /**
     * Returns true if the chat has been marked as private. If the chat is already private, then false is returned.
     */
    fun markAsPrivate(messageReceiver: MessageReceiver<*>): Boolean

    /**
     * Returns true if the private mark has been removed and false if there is nothing to do.
     */
    fun removePrivateMark(messageReceiver: MessageReceiver<*>): Boolean

    fun persistPrivateChat(uniqueIdString: String)

    fun persistDefaultChat(uniqueIdString: String)

    fun hasPrivateChats(): Boolean

    /**
     * Invalidates the cache. This is only required if the preferences are modified without using this service.
     */
    fun invalidateCache()
}
