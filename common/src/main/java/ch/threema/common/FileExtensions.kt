package ch.threema.common

import ch.threema.common.files.FileHandle
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.time.Instant

/**
 * Deletes all files and directories inside a directory without deleting the directory itself.
 */
fun File.clearDirectoryRecursively() {
    listFiles()
        ?.forEach { file ->
            file.deleteRecursively()
        }
}

/**
 * Deletes all files inside a directory but leaves directories and their contents, and does not delete the directory itself.
 */
fun File.clearDirectoryNonRecursively() {
    listFiles()
        ?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
}

/**
 * Deletes a file in a more secure manner by first overwriting it with zeroes.
 * @throws IOException If the file could not be deleted, e.g. because it is a directory
 */
@Throws(IOException::class)
fun File.deleteSecurely() {
    if (!exists()) {
        return
    }

    if (isDirectory) {
        throw IOException("Cannot securely delete a directory")
    }

    val length = length()
    RandomAccessFile(this, "rw").use { randomAccessFile ->
        randomAccessFile.seek(0)
        val zeroesBuffer = ByteArray(16384)
        var position: Long = 0
        while (position < length) {
            val writeLength = zeroesBuffer.size.toLong().coerceAtMost(length - position).toInt()
            randomAccessFile.write(zeroesBuffer, 0, writeLength)
            position += writeLength.toLong()
        }
    }

    if (!delete()) {
        throw IOException("Failed to securely delete file")
    }
}

/**
 * @return The combined size in kb of all files in the directory, or 0 if it is not a directory
 */
fun File.getTotalSize(): Long =
    walkTopDown().sumOf { file ->
        if (file.isFile) file.length() else 0
    }

@Throws(IOException::class)
fun File.copyTo(destination: FileHandle) {
    inputStream().copyTo(destination)
}

@Throws(IOException::class)
fun InputStream.copyTo(destination: FileHandle) {
    use { input ->
        destination.write().use { output ->
            input.copyTo(output)
        }
    }
}

fun File.lastModifiedTime(): Instant =
    Instant.ofEpochMilli(lastModified())

val File.isEmptyDirectory: Boolean
    get() = isDirectory && listFiles().isNullOrEmpty()
