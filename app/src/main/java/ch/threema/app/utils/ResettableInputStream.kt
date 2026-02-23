package ch.threema.app.utils

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.Throws

/**
 * This class allows using an input stream which is normally not resettable (i.e., does not implement `reset()`) in places
 * where resetting is required. This is achieved by closing and then recreating the underlying stream using the provided factory,
 * whenever a reset is requested.
 */
class ResettableInputStream(
    private val streamFactory: StreamFactory,
) : InputStream() {

    fun interface StreamFactory {
        @Throws(IOException::class)
        fun createStream(): InputStream
    }

    private var inputStream = streamFactory.createStream()

    override fun read(): Int =
        inputStream.read()

    override fun read(b: ByteArray?): Int = inputStream.read(b)

    override fun read(b: ByteArray?, off: Int, len: Int): Int = inputStream.read(b, off, len)

    override fun close() {
        inputStream.close()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun readAllBytes(): ByteArray = inputStream.readAllBytes()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun readNBytes(len: Int): ByteArray = inputStream.readNBytes(len)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun readNBytes(b: ByteArray?, off: Int, len: Int): Int = inputStream.readNBytes(b, off, len)

    override fun skip(n: Long): Long = inputStream.skip(n)

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun skipNBytes(n: Long) = inputStream.skipNBytes(n)

    override fun available(): Int = inputStream.available()

    override fun mark(readlimit: Int) = inputStream.mark(readlimit)

    override fun reset() {
        inputStream.close()
        inputStream = streamFactory.createStream()
    }

    override fun markSupported(): Boolean = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun transferTo(out: OutputStream?): Long = inputStream.transferTo(out)
}
