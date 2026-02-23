package ch.threema.domain.protocol.connection

import androidx.annotation.WorkerThread

interface ReconnectableServerConnection {
    /**
     * Reconnect the [ServerConnection].
     * This is usually accomplished by stopping and then starting the connection but may
     * differ depending on the implementation.
     */
    @WorkerThread
    @Throws(InterruptedException::class)
    fun reconnect()
}
