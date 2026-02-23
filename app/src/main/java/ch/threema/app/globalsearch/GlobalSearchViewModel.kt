package ch.threema.app.globalsearch

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel

class GlobalSearchViewModel(
    private val repository: GlobalSearchRepository,
) : ViewModel() {
    val messageModels: LiveData<List<AbstractMessageModel>> = repository.messageModels.map { messageModels ->
        messageModels.filter { message ->
            if (message is GroupMessageModel) {
                message.groupId > 0
            } else {
                message.identity != null
            }
        }
    }

    fun onQueryChanged(
        query: String?,
        filterFlags: Int,
        allowEmpty: Boolean,
        sortAscending: Boolean,
    ) {
        repository.onQueryChanged(query, filterFlags, allowEmpty, sortAscending)
    }

    val isLoading: LiveData<Boolean>
        get() = repository.isLoading

    fun onDataChanged() {
        repository.onDataChanged()
    }
}
