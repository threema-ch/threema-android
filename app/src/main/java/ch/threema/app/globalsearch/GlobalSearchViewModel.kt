package ch.threema.app.globalsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.services.MessageService
import ch.threema.app.services.MessageService.MessageFilterFlags
import ch.threema.app.utils.DispatcherProvider
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.group.GroupMessageModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalSearchViewModel(
    private val messageService: MessageService,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {
    private var queryString: String? = ""
    private var filterFlags = 0

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _messageModels = MutableStateFlow<List<AbstractMessageModel>>(emptyList())
    val messageModels = _messageModels.asStateFlow()

    private var searchJob: Job? = null
        set(value) {
            field?.cancel()
            field = value
        }

    fun onQueryChanged(query: String?, filterFlags: Int) {
        this.queryString = query
        this.filterFlags = filterFlags
        if (query.isNullOrBlank()) {
            searchJob = null
            _messageModels.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            withLoading {
                _messageModels.value = getMessagesForText(query, filterFlags)
            }
        }
    }

    private suspend fun getMessagesForText(queryString: String?, @MessageFilterFlags filterFlags: Int): List<AbstractMessageModel> =
        withContext(dispatcherProvider.io) {
            messageService.getMessagesForText(queryString, filterFlags, false)
        }
            .filter { message ->
                if (message is GroupMessageModel) {
                    message.groupId > 0
                } else {
                    message.identity != null
                }
            }

    private suspend inline fun withLoading(block: suspend () -> Unit) {
        _isLoading.value = true
        try {
            block()
        } finally {
            _isLoading.value = false
        }
    }

    fun onDataChanged() {
        onQueryChanged(queryString, filterFlags)
    }
}
