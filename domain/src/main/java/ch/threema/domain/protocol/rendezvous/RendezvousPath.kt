package ch.threema.domain.protocol.rendezvous

import kotlinx.coroutines.Deferred

internal interface RendezvousPath {
    val pid: UInt
    val closedSignal: Deferred<Unit>

    suspend fun connect()

    /**
     * Close this path. This must close all underlying connections.
     */
    fun close()

    /**
     * Write [bytes] to this path. This suspends until the bytes are sent.
     *
     * @throws java.io.IOException if writing is not possible
     */
    suspend fun write(bytes: ByteArray)

    /**
     * Read the next chunk of bytes from this path.
     *
     * @throws java.io.IOException if the path is closed while waiting for bytes
     */
    suspend fun read(): ByteArray
}
