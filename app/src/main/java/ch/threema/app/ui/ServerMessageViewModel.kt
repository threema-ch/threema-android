package ch.threema.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ch.threema.storage.factories.ServerMessageModelFactory

class ServerMessageViewModel(
    private val serverMessageModelFactory: ServerMessageModelFactory,
) : ViewModel() {
    private val serverMessage = MutableLiveData<String?>()
    fun getServerMessage(): LiveData<String?> = serverMessage

    init {
        serverMessage.postValue(serverMessageModelFactory.popServerMessageModel()?.message)
    }

    fun markServerMessageAsRead() {
        // Delete currently shown message from database if the same message arrived again in the
        // meantime.
        serverMessage.value?.let {
            serverMessageModelFactory.delete(it)
        }
        // Post the next message. If it is null, then no server message is available
        serverMessage.postValue(serverMessageModelFactory.popServerMessageModel()?.message)
    }
}
