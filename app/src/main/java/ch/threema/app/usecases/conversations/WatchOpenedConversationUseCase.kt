package ch.threema.app.usecases.conversations

import ch.threema.app.listeners.ChatListener
import ch.threema.app.managers.ListenerManager
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.ConversationUID
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

private val logger = getThreemaLogger("WatchOpenedConversationUseCase")

class WatchOpenedConversationUseCase {

    /**
     *  Creates a *cold* flow that emits the latest opened conversation.
     *  There is no way to replay the last opened [ConversationUID], as these are fire and forget events.
     *
     *  ##### Overflow strategy
     *  If the flow reaches its buffer capacity, every unconsumed value from [ChatListener.onChatOpened] will be dropped in favor for the most recent
     *  value.
     */
    fun call(): Flow<ConversationUID?> = callbackFlow {
        val chatListener = ChatListener { conversationUID ->
            trySend(conversationUID)
                .onClosed { throwable ->
                    logger.error("Tried to send a new value after channel was closed", throwable)
                }
        }
        ListenerManager.chatListeners.add(chatListener)
        awaitClose {
            ListenerManager.chatListeners.remove(chatListener)
        }
    }
        .buffer(capacity = CONFLATED)
}
