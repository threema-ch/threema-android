package ch.threema.domain.protocol.connection.socket

import androidx.annotation.WorkerThread
import ch.threema.domain.protocol.connection.PipeSink
import ch.threema.domain.protocol.connection.PipeSource
import ch.threema.domain.protocol.connection.ServerConnectionException
import java.net.Socket
import kotlinx.coroutines.Deferred

internal interface ServerSocket :
    PipeSource<ByteArray, ServerSocketCloseReason>,
    PipeSink<ByteArray, Unit> {
    val address: String?

    val closedSignal: Deferred<ServerSocketCloseReason>

    /**
     * Connect the underlying [Socket].
     * If the underlying [Socket] is already connected, it will be closed and a new [Socket] will be
     * created.
     */
    @WorkerThread
    @Throws(Exception::class)
    suspend fun connect()

    /**
     * Process io of the underlying socket.
     * This will read data received by the underlying socket and make it available via the output pipe.
     * [ByteArray]s written to the input pipe will be sent using the underlying socket.
     *
     * This Method will only return if the processing has either been cancelled exceptionally or by
     * calling [close]
     */
    @WorkerThread
    @Throws(Exception::class)
    suspend fun processIo()

    /**
     * Close the underlying socket. If the [ServerSocket] is reconnected, a new underlying socket will be created.
     */
    @WorkerThread
    @Throws(Exception::class)
    fun close(reason: ServerSocketCloseReason)
}

open class ServerSocketException(msg: String?) : ServerConnectionException(msg)
