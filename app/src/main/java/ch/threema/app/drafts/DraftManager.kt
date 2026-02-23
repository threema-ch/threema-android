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
