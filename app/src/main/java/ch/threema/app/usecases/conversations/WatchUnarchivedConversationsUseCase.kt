package ch.threema.app.usecases.conversations

import ch.threema.app.listeners.ConversationListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ConversationService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.storage.models.ConversationModel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

private val logger = getThreemaLogger("WatchUnarchivedConversationsUseCase")

class WatchUnarchivedConversationsUseCase(
    private val conversationService: ConversationService,
    private val dispatcherProvider: DispatcherProvider,
) : WatchConversationsUseCase {

    /**
     *  Creates a *cold* [Flow] of the latest non-archived conversation models.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current list of all non-archived conversation models.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, any unconsumed values are **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception that's not occurring inside the [ConversationListener] will flow downstream.
     */
    override fun call(): Flow<List<ConversationModel>> = callbackFlow {
        // Direct emit promise
        val currentConversations = getCurrentConversations()
        trySend(currentConversations)
            .onClosed {
                // Collection already ended
                return@callbackFlow
            }

        fun trySendCurrent() {
            trySend(getCurrentConversations())
                .onClosed { throwable ->
                    logger.error("Tried to send a new value after channel was closed", throwable)
                }
        }

        val conversationListener = object : ConversationListener {

            override fun onNew(conversationModel: ConversationModel) {
                trySendCurrent()
            }

            override fun onModified(conversationModel: ConversationModel) {
                trySendCurrent()
            }

            override fun onRemoved(conversationModel: ConversationModel) {
                trySendCurrent()
            }

            override fun onModifiedAll() {
                trySendCurrent()
            }
        }
        ListenerManager.conversationListeners.add(conversationListener)
        awaitClose {
            ListenerManager.conversationListeners.remove(conversationListener)
        }
    }
        .buffer(capacity = CONFLATED)
        .flowOn(dispatcherProvider.io)

    /**
     *  Reads all conversation models without forcing a reload from database.
     */
    private fun getCurrentConversations(): List<ConversationModel> = conversationService.getAll(false)
}
