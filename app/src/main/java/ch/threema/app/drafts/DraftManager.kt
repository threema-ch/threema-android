package ch.threema.app.drafts

import androidx.annotation.AnyThread
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.ConversationUID
import kotlinx.coroutines.flow.StateFlow

@AnyThread
interface DraftManager {

    val drafts: StateFlow<Map<ConversationUID, MessageDraft>>

    /**
     * Returns the draft for a conversation, or null if there is no draft.
     * If there is a draft, its text is guaranteed to be non-blank.
     */
    fun get(conversationUID: ConversationUID): MessageDraft?

    fun set(conversationUID: ConversationUID, text: String?) {
        set(conversationUID, text, quotedMessageId = null)
    }

    /**
     * Stores a draft for a conversation. If [text] is null or blank, the draft will be removed instead.
     */
    fun set(conversationUID: ConversationUID, text: String?, quotedMessageId: MessageId?)

    fun remove(conversationUID: ConversationUID)
}
