package ch.threema.app.usecases.availabilitystatus

import ch.threema.app.listeners.ContactListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.IdentityString
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

private val logger = getThreemaLogger("WatchAllContactAvailabilityStatusesUseCase")

class WatchAllContactAvailabilityStatusesUseCase(
    private val contactModelRepository: ContactModelRepository,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     *  If the feature is supported by this build, a *cold* [Flow] that emits the most recent [AvailabilityStatus.Set] values for all contacts is
     *  returned. Otherwise, a flow emitting exactly one empty map is returned.
     *
     *  ##### Direct emit promise
     *  This flow fulfills the promise to directly emit the current values.
     *
     *  ##### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ##### Error strategy
     *  Every exception that's not occurring inside the [ContactListener] will flow downstream.
     */
    fun call(): Flow<Map<IdentityString, AvailabilityStatus>> {
        if (!ConfigUtils.supportsAvailabilityStatus()) {
            return flowOf(emptyMap())
        }
        return callbackFlow {
            // Direct emit promise
            val currentAvailabilityStatuses = getCurrentAvailabilityStatuses()
            trySend(currentAvailabilityStatuses)
                .onClosed {
                    // Collection already ended
                    return@callbackFlow
                }

            fun trySendCurrent() {
                val currentAvailabilityStatuses = getCurrentAvailabilityStatuses()
                trySend(currentAvailabilityStatuses)
                    .onClosed { throwable ->
                        logger.error("Tried to send a new value after channel was closed", throwable)
                    }
            }

            val contactListener = object : ContactListener {
                override fun onNew(identity: String) {
                    trySendCurrent()
                }

                override fun onModified(identity: String) {
                    trySendCurrent()
                }

                override fun onRemoved(identity: String) {
                    trySendCurrent()
                }

                override fun onAvatarChanged(identity: String) {
                    trySendCurrent()
                }
            }
            ListenerManager.contactListeners.add(contactListener)
            awaitClose {
                ListenerManager.contactListeners.remove(contactListener)
            }
        }
            .buffer(capacity = CONFLATED)
            .flowOn(context = dispatcherProvider.io)
    }

    private fun getCurrentAvailabilityStatuses(): Map<IdentityString, AvailabilityStatus> {
        return contactModelRepository
            .getAll()
            .mapNotNull { contactModel ->
                contactModel.data?.availabilityStatus?.let { availabilityStatus ->
                    contactModel.identity to availabilityStatus
                }
            }.toMap()
    }
}
