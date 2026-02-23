package ch.threema.app.multidevice.linking

import ch.threema.common.toHexString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

sealed interface DeviceLinkingStatus {
    class Connected(
        private val rph: ByteArray,
    ) : DeviceLinkingStatus {
        /**
         *  This [Deferred] will complete when the emoji verification was successful
         */
        private val rphConfirmedSignal = CompletableDeferred<Unit>()

        val emojiIndices: Triple<Int, Int, Int> by lazy {
            Triple(
                (rph[0].toUByte() % 128U).toInt(),
                (rph[1].toUByte() % 128U).toInt(),
                (rph[2].toUByte() % 128U).toInt(),
            )
        }

        suspend fun awaitRendezvousPathConfirmation() {
            rphConfirmedSignal.await()
        }

        fun confirmRendezvousPath() {
            rphConfirmedSignal.complete(Unit)
        }

        // TODO(ANDR-2487): Remove if not needed
        fun declineRendezvousPath() {
            rphConfirmedSignal.completeExceptionally(
                DeviceLinkingException("Rendezvous path declined (rph=${rph.toHexString()})"),
            )
        }
    }

    data class Failed(val throwable: Throwable?) : DeviceLinkingStatus

    data object Completed : DeviceLinkingStatus
}
