package ch.threema.localcrypto

import ch.threema.common.secureContentEquals
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.MasterKeyStorageData
import ch.threema.localcrypto.models.Version1MasterKeyStorageData
import java.io.IOException
import kotlin.Throws

/**
 * Provides access to the master key storage, independently of the storage version.
 */
class MasterKeyStorageManager(
    private val version2KeyFileManager: Version2MasterKeyFileManager,
    private val version1KeyFileManager: Version1MasterKeyFileManager,
    private val storageStateConverter: MasterKeyStorageStateConverter,
) {
    fun keyExists() = version2KeyFileManager.keyFileExists() || version1KeyFileManager.keyFileExists()

    @Throws(IOException::class)
    fun readKey(): MasterKeyState =
        storageStateConverter.toKeyState(readStorageData())

    private fun readStorageData(): MasterKeyStorageData {
        if (version2KeyFileManager.keyFileExists()) {
            return version2KeyFileManager.readKeyFile()
        }
        return version1KeyFileManager.readKeyFile()
    }

    @Throws(IOException::class)
    fun writeKey(data: MasterKeyState) {
        if (version1KeyFileManager.keyFileExists() && data is MasterKeyState.Plain) {
            val oldKeyData = version1KeyFileManager.readKeyFile().data
            if (oldKeyData is Version1MasterKeyStorageData.Unprotected) {
                if (!oldKeyData.masterKeyData.value.secureContentEquals(data.masterKeyData.value)) {
                    error("Migrated master key differs from original master key")
                }
            }
        }

        version2KeyFileManager.writeKeyFile(storageStateConverter.toStorageData(data))
        version1KeyFileManager.deleteFile()
    }
}
