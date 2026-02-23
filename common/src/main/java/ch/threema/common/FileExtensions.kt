package ch.threema.common

import ch.threema.common.files.FileHandle
import java.io.File
import java.io.IOException
import java.io.InputStream

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
