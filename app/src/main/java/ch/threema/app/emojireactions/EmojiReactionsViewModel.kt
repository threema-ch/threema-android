/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.threema.app.activities.StateFlowViewModel
import ch.threema.app.services.MessageService
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier.TargetMessageType
import ch.threema.storage.models.AbstractMessageModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class EmojiReactionsViewModel(
    savedStateHandle: SavedStateHandle,
    emojiReactionsRepository: EmojiReactionsRepository,
    private val messageService: MessageService
) : StateFlowViewModel() {
    private var emojiReactionsModel: EmojiReactionsModel? = null

    companion object {
        private const val MESSAGE_ID = "messageId"
        private const val MESSAGE_TYPE = "messageType"

        fun provideFactory(
            emojiReactionsRepository: EmojiReactionsRepository,
            messageService: MessageService,
            reactionMessageIdentifier: ReactionMessageIdentifier
        ) = viewModelFactory {
            initializer {
                EmojiReactionsViewModel(
                    this.createSavedStateHandle().apply {
                        set(MESSAGE_ID, reactionMessageIdentifier.messageId)
                        set(MESSAGE_TYPE, reactionMessageIdentifier.messageType)
                    },
                    emojiReactionsRepository,
                    messageService
                )
            }
        }
    }

    private val reactionMessageIdentifier by lazy {
        ReactionMessageIdentifier(
            messageId = checkNotNull(savedStateHandle.get<Int>(MESSAGE_ID)),
            messageType = checkNotNull(savedStateHandle.get<TargetMessageType>(MESSAGE_TYPE)),
        )
    }

    val emojiReactionsUiState: StateFlow<EmojiReactionsUiState> =
        getMessageModel()?.let {
            emojiReactionsModel = emojiReactionsRepository.getReactionsByMessage(it)
            (emojiReactionsModel?.data ?: MutableStateFlow(emptyList()))
                .map { reactions -> EmojiReactionsUiState(reactions ?: emptyList()) }
                .stateInViewModel(initialValue = EmojiReactionsUiState(emptyList()))
        } ?: MutableStateFlow(EmojiReactionsUiState(emptyList()))


    private fun getMessageModel(): AbstractMessageModel? =
        when (reactionMessageIdentifier.messageType) {
            TargetMessageType.ONE_TO_ONE -> messageService.getContactMessageModel(
                reactionMessageIdentifier.messageId
            )

            TargetMessageType.GROUP -> messageService.getGroupMessageModel(reactionMessageIdentifier.messageId)
        }

    data class EmojiReactionsUiState(
        val emojiReactions: List<EmojiReactionData>
    )
}
