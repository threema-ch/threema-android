package ch.threema.common.files

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FallbackFileHandle(
    private val primaryFile: FileHandle,
    private val fallbackFile: FileHandle,
) : FileHandle {
    override fun exists() = primaryFile.exists() || fallbackFile.exists()

    override fun isEmpty() =
        when {
            primaryFile.exists() -> primaryFile.isEmpty()
            fallbackFile.exists() -> fallbackFile.isEmpty()
            else -> false
        }

    @Throws(IOException::class)
    override fun read(): InputStream? {
        if (primaryFile.exists()) {
            return primaryFile.read()
        }
        if (fallbackFile.exists()) {
            return fallbackFile.read()
        }
        return null
    }

    @Throws(IOException::class)
    override fun create() {
        tryDeletingFallbackFile()
        primaryFile.create()
    }

    @Throws(IOException::class)
    override fun write(): OutputStream {
        tryDeletingFallbackFile()
        return primaryFile.write()
    }

    override fun delete() {
        primaryFile.delete()
        tryDeletingFallbackFile()
    }

    private fun tryDeletingFallbackFile() {
        try {
            fallbackFile.delete()
        } catch (_: IOException) {
            // If the fallback file can not be deleted, we just ignore it. It might generally not be possible to access it, and we don't
            // want that to stop us from writing or deleting the primary file.
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FallbackFileHandle
        if (primaryFile != other.primaryFile) return false
        if (fallbackFile != other.fallbackFile) return false

        return true
    }

    override fun hashCode(): Int {
        var result = primaryFile.hashCode()
        result = 31 * result + fallbackFile.hashCode()
        return result
    }

    override fun toString() = "Fallback[$primaryFile, $fallbackFile]"
}
