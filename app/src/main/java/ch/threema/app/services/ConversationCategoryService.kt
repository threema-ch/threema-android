package ch.threema.app.services

import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.base.SessionScoped
import ch.threema.data.models.ContactModel
import ch.threema.domain.types.ConversationUID
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.protobuf.d2d.sync.MdD2DSync.ConversationCategory

/**
 * The conversation category service is used to keep track of private chats. It manages reflection if MD is enabled.
 *
 * TODO(ANDR-3010): Move the conversation category into the database.
 */
@SessionScoped
interface ConversationCategoryService {
    /* Contact related methods */

    fun markContactChatAsPrivate(contactModel: ContactModel)

    fun removePrivateMarkFromContactChat(contactModel: ContactModel)

    fun removePrivateMarkFromContactChat(contactModel: ch.threema.storage.models.ContactModel)

    /* Group related methods */

    fun markGroupChatAsPrivate(groupDatabaseId: GroupDatabaseId)

    fun removePrivateMarkFromGroupChat(groupDatabaseId: GroupDatabaseId)

    fun isPrivateGroupChat(groupDatabaseId: GroupDatabaseId): Boolean

    /* General methods */

    fun isPrivateChat(uniqueIdString: ConversationUID): Boolean

    fun getConversationCategory(uniqueIdString: ConversationUID): ConversationCategory

    /**
     * Returns true if the chat has been marked as private. If the chat is already private, then false is returned.
     */
    fun markAsPrivate(messageReceiver: MessageReceiver<*>): Boolean

    /**
     * Returns true if the private mark has been removed and false if there is nothing to do.
     */
    fun removePrivateMark(messageReceiver: MessageReceiver<*>): Boolean

    fun persistPrivateChat(uniqueIdString: ConversationUID)

    fun persistDefaultChat(uniqueIdString: ConversationUID)

    fun hasPrivateChats(): Boolean

    /**
     * Invalidates the cache. This is only required if the preferences are modified without using this service.
     */
    fun invalidateCache()
}
