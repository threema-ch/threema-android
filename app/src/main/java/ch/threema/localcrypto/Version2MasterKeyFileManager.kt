package ch.threema.localcrypto

import androidx.core.util.AtomicFile
import ch.threema.android.writeAtomically
import ch.threema.localcrypto.models.MasterKeyStorageData
import java.io.DataInputStream
import java.io.File
import java.io.IOException

/**
 * Handles reading from and writing to the version 2 master key file,
 * i.e., the file format introduced with the Remote Secrets feature in app version 6.2.0.
 * It follows the specification in "key-storage.proto".
 */
class Version2MasterKeyFileManager(
    private val keyFile: File,
    private val encoder: Version2MasterKeyStorageEncoder,
    private val decoder: Version2MasterKeyStorageDecoder,
) {
    fun keyFileExists() = keyFile.exists()

    @Throws(IOException::class)
    fun readKeyFile(): MasterKeyStorageData {
        val atomicKeyFile = AtomicFile(keyFile)
        return DataInputStream(atomicKeyFile.openRead()).use { dis ->
            decoder.decodeOuterKeyStorage(dis)
        }
    }

    @Throws(IOException::class)
    fun writeKeyFile(masterKeyStorageData: MasterKeyStorageData.Version2) {
        keyFile.writeAtomically { outputStream ->
            encoder.encodeMasterKeyStorageData(masterKeyStorageData).writeTo(outputStream)
        }
    }
}
