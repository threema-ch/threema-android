package ch.threema.app.usecases

import ch.threema.app.listeners.ContactTypingListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ContactService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.IdentityString
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private val logger = getThreemaLogger("WatchTypingIdentitiesUseCase")

class WatchTypingIdentitiesUseCase(
    private val contactService: ContactService,
) {

    /**
     *  Creates a *cold* [Flow] that emits the latest typing contact identities.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current typing identities.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception that's not occurring inside the [ContactTypingListener] will flow downstream.
     */
    fun call(): Flow<Set<IdentityString>> = callbackFlow {
        // Direct emit promise
        val currentTypingIdentities = contactService.typingIdentities
        trySend(currentTypingIdentities)
            .onClosed {
                // Collection already ended while determining the current typing identities
                return@callbackFlow
            }

        val contactTypingListener = ContactTypingListener { _, _ ->
            val updatedTypingIdentities = contactService.typingIdentities
            trySend(updatedTypingIdentities)
                .onClosed { throwable ->
                    logger.error("Tried to send a new value after channel was closed", throwable)
                }
        }
        ListenerManager.contactTypingListeners.add(contactTypingListener)
        awaitClose {
            ListenerManager.contactTypingListeners.remove(contactTypingListener)
        }
    }
        .buffer(capacity = CONFLATED)
        .distinctUntilChanged()
}
