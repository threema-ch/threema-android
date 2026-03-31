package ch.threema.app.home.usecases

import ch.threema.app.services.MessageService
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("GetStarredMessagesCountUseCase")

class GetStarredMessagesCountUseCase(
    private val messageService: MessageService,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(): Int = withContext(dispatcherProvider.worker) {
        try {
            messageService.countStarredMessages().toInt()
        } catch (e: Exception) {
            logger.error("Failed to count starred messages", e)
            0
        }
    }
}
