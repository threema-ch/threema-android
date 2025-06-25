/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.archive

import androidx.lifecycle.asFlow
import ch.threema.app.activities.StateFlowViewModel
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ConversationModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class ArchiveViewModel : StateFlowViewModel() {
    private val archiveRepository = ArchiveRepository()

    private val conversationModels: StateFlow<List<ConversationModel>> =
        archiveRepository
            .conversationModels
            .asFlow()
            .stateInViewModel(initialValue = emptyList())

    private val checkedConversationUids = MutableStateFlow<Set<String>>(emptySet())

    val conversationUiModels: StateFlow<List<ConversationUiModel>> =
        combine(conversationModels, checkedConversationUids) { conversationModels, checkedConversations ->
            conversationModels.map { conversationModel ->
                ConversationUiModel(
                    conversation = conversationModel,
                    isChecked = checkedConversations.contains(conversationModel.uid),
                )
            }
        }.stateInViewModel(emptyList())

    val checkedCount: Int
        get() = checkedConversationUids.value.size

    val checkedConversationModels: List<ConversationModel>
        get() = conversationUiModels.value.mapNotNull { conversationUiModel ->
            when (conversationUiModel.isChecked) {
                true -> conversationUiModel.conversation
                false -> null
            }
        }

    fun refresh() {
        archiveRepository.onDataChanged()
    }

    fun onFilterQueryChanged(query: String?) {
        archiveRepository.setFilter(query)
        archiveRepository.onDataChanged()
    }

    /**
     *  @return True if at least one conversation is checked **after** this toggle action.
     */
    fun toggleConversationChecked(conversationUid: String): Boolean {
        if (checkedConversationUids.value.contains(conversationUid)) {
            checkedConversationUids.value -= conversationUid
        } else {
            checkedConversationUids.value += conversationUid
        }
        return checkedConversationUids.value.isNotEmpty()
    }

    /**
     *  @return True if at least one conversation is checked
     */
    fun checkAll(): Boolean {
        checkedConversationUids.value = conversationModels.value.map(ConversationModel::uid).toSet()
        return checkedConversationUids.value.isNotEmpty()
    }

    fun uncheckAll() {
        checkedConversationUids.value = emptySet()
    }

    fun conversationListenerOnNew(newConversationModel: ConversationModel) {
        if (!conversationModels.value.contains(newConversationModel)) {
            archiveRepository.onDataChanged()
        }
    }

    fun conversationListenerOnModified(modifiedConversationModel: ConversationModel) {
        if (conversationModels.value.contains(modifiedConversationModel)) {
            archiveRepository.onDataChanged()
        }
    }

    fun conversationListenerOnModifiedAll() {
        archiveRepository.onDataChanged()
    }

    fun conversationListenerOnRemoved(removedConversationModel: ConversationModel) {
        if (conversationModels.value.contains(removedConversationModel)) {
            archiveRepository.onDataChanged()
        }
    }

    fun messageListenerOnNew(newMessage: AbstractMessageModel) {
        if (!newMessage.isOutbox && !newMessage.isStatusMessage && !newMessage.isRead) {
            archiveRepository.onDataChanged()
        }
    }
}

data class ConversationUiModel(
    val conversation: ConversationModel,
    val isChecked: Boolean,
)
