package ch.threema.app.emojireactions

import ch.threema.app.framework.BaseViewModel
import ch.threema.app.services.MessageService
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier.TargetMessageType
import ch.threema.storage.models.AbstractMessageModel

class EmojiReactionsViewModel(
    private val emojiReactionsRepository: EmojiReactionsRepository,
    private val messageService: MessageService,
    private val reactionMessageIdentifier: ReactionMessageIdentifier,
) : BaseViewModel<EmojiReactionsViewState, Unit>() {
    private var emojiReactionsModel: EmojiReactionsModel? = null

    override fun initialize() = runInitialization {
        emojiReactionsModel = getMessageModel()
            ?.let { messageModel ->
                emojiReactionsRepository.getReactionsByMessage(messageModel)
            }

        EmojiReactionsViewState(
            emojiReactions = emojiReactionsModel?.data ?: emptyList(),
        )
    }

    override suspend fun onActive() = runAction {
        emojiReactionsModel?.dataFlow?.collect { reactions ->
            updateViewState {
                copy(emojiReactions = reactions ?: emptyList())
            }
        }
    }

    private fun getMessageModel(): AbstractMessageModel? =
        when (reactionMessageIdentifier.messageType) {
            TargetMessageType.ONE_TO_ONE -> messageService.getContactMessageModel(
                reactionMessageIdentifier.messageId,
            )

            TargetMessageType.GROUP -> messageService.getGroupMessageModel(reactionMessageIdentifier.messageId)
        }
}
