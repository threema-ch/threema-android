package ch.threema.app.usecases

import ch.threema.app.listeners.ContactTypingListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ContactService
import ch.threema.domain.types.Identity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 *  Creates a *cold* [Flow] that publishes any changes to the currently typing contact identities.
 */
class WatchTypingIdentitiesUseCase(
    private val contactService: ContactService,
) {

    fun call(): Flow<Set<Identity>> = callbackFlow {
        fun trySendCurrent() {
            trySend(contactService.typingIdentities)
        }

        trySendCurrent()

        val contactTypingListener = ContactTypingListener { _, _ -> trySendCurrent() }

        ListenerManager.contactTypingListeners.add(contactTypingListener)

        awaitClose {
            ListenerManager.contactTypingListeners.remove(contactTypingListener)
        }
    }
}
