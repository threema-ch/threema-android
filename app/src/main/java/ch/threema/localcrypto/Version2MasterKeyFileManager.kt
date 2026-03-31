package ch.threema.localcrypto

import ch.threema.localcrypto.models.MasterKeyStorageData
import java.io.IOException

/**
 * Handles reading from and writing to the version 2 master key file,
 * i.e., the file format introduced with the Remote Secrets feature in app version 6.2.0.
 * It follows the specification in "key-storage.proto".
 */
interface Version2MasterKeyFileManager {
    fun keyFileExists(): Boolean

    @Throws(IOException::class)
    fun readKeyFile(): MasterKeyStorageData.Version2

    @Throws(IOException::class)
    fun writeKeyFile(masterKeyStorageData: MasterKeyStorageData.Version2)
}
