/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
