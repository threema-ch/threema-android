package ch.threema.app.fragments.composemessage

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.MessageService
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.SingleLiveEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeMessageViewModel(
    private val messageService: MessageService,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _events = SingleLiveEvent<ComposeMessageEvent>()
    val events: LiveData<ComposeMessageEvent> = _events

    fun loadNextRecords(
        messageReceiver: MessageReceiver<*>,
        filter: MessageService.MessageFilter,
    ) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                val messageModels = messageService.getMessagesForReceiver(messageReceiver, filter)
                _events.postValue(
                    ComposeMessageEvent.NextRecordsLoaded(
                        messageModels = messageModels,
                        hasMoreRecords = messageModels.size >= filter.pageSize,
                    ),
                )
            }
        }
    }
}
