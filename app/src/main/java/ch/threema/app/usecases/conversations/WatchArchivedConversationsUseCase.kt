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

private val logger = getThreemaLogger("WatchArchivedConversationsUseCase")

class WatchArchivedConversationsUseCase(
    private val conversationService: ConversationService,
    private val dispatcherProvider: DispatcherProvider,
) : WatchConversationsUseCase {

    /**
     *  Creates a *cold* [Flow] of the most recent archived conversation models.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current list of all conversation models.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, any old unconsumed values are **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception that's not occurring inside the [ConversationListener] will flow downstream.
     *
     *  ##### Listener logic
     *  - This listener will also get any events from un-archived conversations
     *  - We skip unnecessary re-reads of the archived conversations from database and only emit a new list of archived conversations
     *  if the change(s) affected an archived conversation
     *  - In the event of [ConversationListener.onModifiedAll] all archived conversations must be read from database in every case
     *
     *  TODO(ANDR-4175): Rework the listener callbacks when they are called in a correct way
     */
    override fun call(): Flow<List<ConversationModel>> = callbackFlow {
        // Direct emit promise
        val currentArchivedConversations = getCurrentArchivedConversations()
        trySend(currentArchivedConversations)
            .onClosed {
                // Collection already ended
                return@callbackFlow
            }

        fun trySendCurrent() {
            val currentArchivedConversations = getCurrentArchivedConversations()
            trySend(currentArchivedConversations)
                .onClosed { throwable ->
                    logger.error("Tried to send a new value after channel was closed", throwable)
                }
        }

        val conversationListener = object : ConversationListener {

            /**
             *  TODO(ANDR-4175): Skip refreshes here if conversationModel is not archived
             *
             *  Right now we are not able to skip these events for an un-archived conversationModel, as this will be called if a conversation gets
             *  un-archived. The idea behind ANDR-4175 is to switch this event to onModified.
             */
            override fun onNew(conversationModel: ConversationModel) {
                trySendCurrent()
            }

            override fun onModified(conversationModel: ConversationModel) {
                if (conversationModel.isArchived) {
                    trySendCurrent()
                }
            }

            override fun onRemoved(conversationModel: ConversationModel) {
                if (conversationModel.isArchived) {
                    trySendCurrent()
                }
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
        .flowOn(context = dispatcherProvider.io)

    /**
     *  Reads all archived conversation models and turns the result into an immutable copy.
     */
    private fun getCurrentArchivedConversations(): List<ConversationModel> = conversationService
        .getArchived()
        .toList()
}
