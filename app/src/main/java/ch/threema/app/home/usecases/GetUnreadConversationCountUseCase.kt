package ch.threema.app.home.usecases

import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.ConversationTag
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("GetUnreadConversationCountUseCase")

class GetUnreadConversationCountUseCase(
    private val conversationService: ConversationService,
    private val conversationTagService: ConversationTagService,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call() = withContext(dispatcherProvider.worker) {
        val conversationModels = conversationService.getAll(
            false,
            object : ConversationService.Filter {
                override fun onlyUnread() = true
            },
        )

        var unread = conversationModels.sumOf { conversationModel ->
            conversationModel.unreadCount.toInt()
        }

        // First check whether there are some conversations that are marked as unread. This
        // check is expected to be fast, as usually there are not many chats that are marked
        // as unread.
        if (conversationTagService.getCount(ConversationTag.MARKED_AS_UNREAD) > 0) {
            // In case there is at least one unread tag, we create a set of all possible
            // conversation uids to efficiently check that the unread tags are valid.
            val shownConversationUids = conversationService.getAll(false)
                .map(ConversationModel::uid)
                .toSet()

            val unreadUids: List<String> = conversationTagService.getConversationUidsByTag(ConversationTag.MARKED_AS_UNREAD)
            for (unreadUid in unreadUids) {
                if (unreadUid in shownConversationUids) {
                    unread++
                } else {
                    logger.warn("Conversation '{}' is marked as unread but not shown. Deleting the unread flag.", unreadUid)
                    conversationTagService.removeTag(unreadUid, ConversationTag.MARKED_AS_UNREAD, TriggerSource.LOCAL)
                }
            }
        }

        unread
    }
}
