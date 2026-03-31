package ch.threema.app.mediagallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.common.toggle
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.data.MessageContentsType
import kotlinx.coroutines.launch

class MediaGalleryViewModel(
    private val preferenceService: PreferenceService,
    private val mediaGalleryRepository: MediaGalleryRepository,
    private val messageReceiver: MessageReceiver<*>,
) : ViewModel() {
    val messages: LiveData<List<AbstractMessageModel>?> = mediaGalleryRepository.messages

    private val _selectedContentTypes: MutableLiveData<Set<Int>> = MutableLiveData()
    val selectedContentTypes: LiveData<Set<Int>> = _selectedContentTypes

    init {
        initializeSelectedContentTypes()
        loadSelectedMessages()
    }

    private fun initializeSelectedContentTypes() {
        val storedSelectedContentTypes = preferenceService.getMediaGalleryContentTypes()
        val selectedContentTypes: MutableSet<Int> = mutableSetOf()
        storedSelectedContentTypes.forEachIndexed { index, isSelected ->
            if (isSelected) {
                selectedContentTypes.add(SELECTABLE_CONTENT_TYPES[index])
            }
        }
        _selectedContentTypes.value = selectedContentTypes.toSet()
    }

    private fun persistSelectedContentTypes(@MessageContentsType selectedContentTypes: Set<Int>) {
        val persistedSelectedContentTypes = BooleanArray(SELECTABLE_CONTENT_TYPES.size)
        SELECTABLE_CONTENT_TYPES.forEachIndexed { index, contentType ->
            persistedSelectedContentTypes[index] = selectedContentTypes.contains(contentType)
        }
        preferenceService.setMediaGalleryContentTypes(persistedSelectedContentTypes)
    }

    fun toggleSelectedContentType(@MessageContentsType contentType: Int) {
        val updatedSelectedContentTypes: Set<Int> = _selectedContentTypes.value?.toggle(contentType) ?: return
        if (updatedSelectedContentTypes.isEmpty()) {
            _selectedContentTypes.value = setOf(contentType)
            return
        }
        persistSelectedContentTypes(updatedSelectedContentTypes)
        _selectedContentTypes.value = updatedSelectedContentTypes
        loadSelectedMessages()
    }

    private fun loadSelectedMessages() {
        val currentlySelectedContentTypes: Set<Int> = _selectedContentTypes.value ?: return
        viewModelScope.launch {
            mediaGalleryRepository.loadMessages(
                messageReceiver = messageReceiver,
                contentTypes = currentlySelectedContentTypes,
            )
        }
    }

    companion object {

        // Do not change the order, as the PreferenceService relies on it being stable
        @MessageContentsType
        val SELECTABLE_CONTENT_TYPES: IntArray = intArrayOf(
            MessageContentsType.IMAGE,
            MessageContentsType.GIF,
            MessageContentsType.VIDEO,
            MessageContentsType.VOICE_MESSAGE,
            MessageContentsType.AUDIO,
            MessageContentsType.FILE,
        )
    }
}
