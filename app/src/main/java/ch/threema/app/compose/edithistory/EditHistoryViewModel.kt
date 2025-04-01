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

package ch.threema.app.compose.edithistory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.StateFlowViewModel
import ch.threema.data.models.EditHistoryEntryData
import ch.threema.data.repositories.EditHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

class EditHistoryViewModel(
    savedStateHandle: SavedStateHandle,
    editHistoryRepository: EditHistoryRepository,
) : StateFlowViewModel() {

    companion object {

        private const val MESSAGE_UID = "messageUid"

        fun provideFactory(messageId: String) = viewModelFactory {
            initializer {
                EditHistoryViewModel(
                    this.createSavedStateHandle().apply {
                        set(MESSAGE_UID, messageId)
                    },
                    ThreemaApplication.requireServiceManager().modelRepositories.editHistory
                )
            }
        }
    }

    private val messageUid = checkNotNull(savedStateHandle[MESSAGE_UID]) as String

    val editHistoryUiState: StateFlow<EditHistoryUiState> =
        (editHistoryRepository.getByMessageUid(messageUid)?.data ?: MutableStateFlow(emptyList()))
            .map { editHistoryEntries ->
                EditHistoryUiState(editHistoryEntries ?: emptyList())
            }
            .stateInViewModel(
                initialValue = EditHistoryUiState(emptyList())
            )
}

data class EditHistoryUiState(
    val editHistoryEntries: List<EditHistoryEntryData>
)
