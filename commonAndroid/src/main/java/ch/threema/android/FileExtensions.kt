package ch.threema.android

import androidx.core.util.AtomicFile
import ch.threema.annotation.SameThread
import ch.threema.common.NoCloseOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Writes a file atomically, i.e., it is either fully written or not at all.
 * If the file or its parent directory did not previously exist, it will be created.
 * If the file did previously exist, it will be replaced.
 *
 * The output stream passed into [performWrite] does not need to be closed.
 */
@SameThread
fun File.writeAtomically(performWrite: (OutputStream) -> Unit) {
    val atomicKeyFile = AtomicFile(this)
    val fos = atomicKeyFile.startWrite()
    try {
        // Note: stream *must not* be closed explicitly (see AtomicFile documentation)
        val dos = NoCloseOutputStream(DataOutputStream(fos))
        performWrite(dos)
        dos.flush()
    } catch (e: IOException) {
        atomicKeyFile.failWrite(fos)
        throw e
    }
    atomicKeyFile.finishWrite(fos)
}
