package ch.threema.app.usecases.contacts

import ch.threema.app.listeners.ContactSettingsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactNameFormat
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private val logger = getThreemaLogger("WatchContactNameFormatSettingUseCase")

class WatchContactNameFormatSettingUseCase(
    private val preferenceService: PreferenceService,
) {

    /**
     *  Creates a *cold* flow that emits the most recent distinct value of the contact name format setting stored in the shared preferences
     *  (`preferences__contact_format`).
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current value. If the current value could not be determined due to an internal error, a
     *  fallback value is emitted.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  In the unlikely case that we fail to determine the current value, the fallback value [ContactNameFormat.DEFAULT] is emitted.
     *
     *  @see ContactSettingsListener
     */
    fun call(): Flow<ContactNameFormat> = callbackFlow {
        // Direct emit promise
        val currentValue = getCurrentValueOrFallback()
        trySend(currentValue)
            .onClosed {
                // Collection already ended
                return@callbackFlow
            }

        val listener = object : ContactSettingsListener {
            override fun onNameFormatChanged(nameFormat: ContactNameFormat) {
                trySend(nameFormat)
                    .onClosed { throwable ->
                        logger.error("Tried to send a new value after channel was closed", throwable)
                    }
            }
        }
        ListenerManager.contactSettingsListeners.add(listener)
        awaitClose {
            ListenerManager.contactSettingsListeners.remove(listener)
        }
    }
        .buffer(capacity = CONFLATED)
        .distinctUntilChanged()

    private fun getCurrentValueOrFallback(): ContactNameFormat =
        runCatching {
            preferenceService.getContactNameFormat()
        }.getOrElse { throwable ->
            logger.error("Failed to read contact name format setting from preferences", throwable)
            ContactNameFormat.DEFAULT
        }
}
