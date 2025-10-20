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

import androidx.lifecycle.ViewModel
import ch.threema.common.mapState
import ch.threema.data.models.EditHistoryEntryData
import ch.threema.data.repositories.EditHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EditHistoryViewModel(
    editHistoryRepository: EditHistoryRepository,
    messageUid: String,
) : ViewModel() {
    val editHistoryUiState: StateFlow<EditHistoryUiState> =
        (editHistoryRepository.getByMessageUid(messageUid)?.dataFlow ?: MutableStateFlow(emptyList()))
            .mapState { editHistoryEntries ->
                EditHistoryUiState(editHistoryEntries ?: emptyList())
            }
}

data class EditHistoryUiState(
    val editHistoryEntries: List<EditHistoryEntryData>,
)
