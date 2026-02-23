package ch.threema.app.files

import ch.threema.common.files.FallbackFileHandle
import ch.threema.common.files.FileHandle
import ch.threema.common.files.SimpleFileHandle
import ch.threema.localcrypto.MasterKeyProvider
import java.io.File

fun File.fileHandle(name: String) =
    SimpleFileHandle(this, name)

fun File.fileHandle(directory: String, name: String) =
    SimpleFileHandle(File(this, directory), name)

fun FileHandle.withFallback(fileHandle: FileHandle) =
    FallbackFileHandle(this, fileHandle)

fun FileHandle.withEncryption(masterKeyProvider: MasterKeyProvider) =
    EncryptedFileHandle(masterKeyProvider, this)
