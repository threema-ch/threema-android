package ch.threema.app.fragments.composemessage

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.MessageService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.SingleLiveEvent
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.repositories.ContactModelRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeMessageViewModel(
    private val messageService: MessageService,
    private val dispatcherProvider: DispatcherProvider,
    private val contactModelRepository: ContactModelRepository,
) : ViewModel() {

    private val _events = SingleLiveEvent<ComposeMessageEvent>()
    val events: LiveData<ComposeMessageEvent> = _events

    private val _contactAvailabilityStatus = MutableLiveData<AvailabilityStatus>(AvailabilityStatus.None)
    val contactAvailabilityStatus: LiveData<AvailabilityStatus> = _contactAvailabilityStatus

    fun onResume(messageReceiver: MessageReceiver<*>) {
        if (ConfigUtils.supportsAvailabilityStatus() && messageReceiver is ContactMessageReceiver) {
            viewModelScope.launch {
                contactModelRepository
                    .getByIdentity(messageReceiver.contact.identity)
                    ?.data
                    ?.availabilityStatus
                    ?.let { availabilityStatus ->
                        _contactAvailabilityStatus.postValue(availabilityStatus)
                    }
            }
        }
    }

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
