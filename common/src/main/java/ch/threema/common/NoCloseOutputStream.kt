package ch.threema.common

import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * Can be used to wrap around another [outputStream] in cases where we need to prevent the stream from being closed.
 */
class NoCloseOutputStream(outputStream: OutputStream) : FilterOutputStream(outputStream) {
    override fun close() {
        // do nothing here
    }
}
