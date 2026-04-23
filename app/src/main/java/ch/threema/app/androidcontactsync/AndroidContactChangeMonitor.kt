package ch.threema.app.androidcontactsync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import ch.threema.app.GlobalListeners
import ch.threema.app.androidcontactsync.usecases.UpdateContactNameUseCase
import ch.threema.app.di.injectNonBinding
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.repositories.ContactModelRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("AndroidContactChangeMonitor")

// TODO(ANDR-4752): Convert this to a proper monitor.
class AndroidContactChangeMonitor(
    private val appContext: Context,
    private val updateContactNameUseCase: UpdateContactNameUseCase,
    dispatcherProvider: DispatcherProvider,
) : KoinComponent {
    private val synchronizeContactsService: SynchronizeContactsService by injectNonBinding()
    private val synchronizedSettingsService: SynchronizedSettingsService by injectNonBinding()
    private val contactModelRepository: ContactModelRepository by injectNonBinding()

    private val coroutineScope = CoroutineScope(dispatcherProvider.io)
    private val mutex = Mutex()

    private val contentObserverChangeContactNames: ContentObserver = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            if (selfChange) {
                return
            }

            coroutineScope.launch {
                if (!mutex.tryLock()) {
                    return@launch
                }
                try {
                    delay(initialDelayDuration)
                    onContactChange()
                    delay(cooldownDuration)
                } finally {
                    mutex.unlock()
                }
            }
        }

        private suspend fun onContactChange() {
            logger.info("Contact name change observed")

            try {
                GlobalListeners.onAndroidContactChangeLock.lock()
                logger.info("Starting to update all contact names from android contacts")

                if (synchronizeContactsService.isSynchronizationInProgress()) {
                    logger.warn("Aborting contact name change observer as a contact synchronization is currently in progress")
                    return
                }

                if (!synchronizedSettingsService.isSyncContacts()) {
                    logger.warn("Contact synchronization is not enabled. Aborting.")
                    return
                }

                logger.info("Updating all contact names from android contacts")
                val contactModels = contactModelRepository
                    .getAll()
                    .filter { contactModel -> contactModel.data?.androidContactLookupInfo != null }
                    .toSet()
                updateContactNameUseCase.call(contactModels)
                logger.info("Finished updating contact names from android contacts")
            } catch (e: Exception) {
                logger.error("Contact name change observer could not be run successfully", e)
            } finally {
                GlobalListeners.onAndroidContactChangeLock.unlock()
            }
        }
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_CONTACTS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            appContext.contentResolver
                .registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    false,
                    contentObserverChangeContactNames,
                )
        } catch (e: Exception) {
            logger.error("Could not register content observer", e)
        }
    }

    fun stop() {
        try {
            appContext.contentResolver
                .unregisterContentObserver(contentObserverChangeContactNames)
        } catch (e: Exception) {
            logger.error("Could not unregister content observer", e)
        }
    }

    companion object {
        /**
         * Before starting to update the contact names after a contact change has been observed, we wait for the [initialDelayDuration]. On some
         * devices the content observer is triggered before the actual change is readable.
         */
        private val initialDelayDuration = 5.seconds

        /**
         * The cooldown duration is used to prevent querying contact name changes too often in case the contacts are modified frequently.
         */
        private val cooldownDuration = 10.seconds
    }
}
