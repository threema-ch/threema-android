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
