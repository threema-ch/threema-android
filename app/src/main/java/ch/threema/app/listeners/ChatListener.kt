package ch.threema.app.listeners

import ch.threema.domain.types.ConversationUID

fun interface ChatListener {
    fun onChatOpened(conversationUID: ConversationUID)
}
