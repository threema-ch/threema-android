package ch.threema.app.webclient.activities

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.distinctUntilChanged

class SessionsViewModel : ViewModel() {
    private val _showMultiDeviceBanner = MutableLiveData(true)
    val showMultiDeviceBanner: LiveData<Boolean> = _showMultiDeviceBanner.distinctUntilChanged()

    fun dismissMultiDeviceBanner() {
        _showMultiDeviceBanner.value = false
    }
}
