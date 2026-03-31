package ch.threema.app.usecases.conversations

import ch.threema.storage.models.ConversationModel
import kotlinx.coroutines.flow.Flow

interface WatchConversationsUseCase {

    fun call(): Flow<List<ConversationModel>>
}
