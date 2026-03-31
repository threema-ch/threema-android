package ch.threema.localcrypto

import androidx.core.util.AtomicFile
import ch.threema.android.writeAtomically
import ch.threema.localcrypto.models.MasterKeyStorageData
import java.io.DataInputStream
import java.io.File

/**
 * Android 7 does not support AES_256. Therefore, we forego encrypting the master key file
 * with the Android KeyStore and instead keep using the unencrypted legacy key file as-is.
 */
class Version2MasterKeyFileManagerAndroid7Impl(
    private val legacyKeyFile: File,
    private val encoder: Version2MasterKeyStorageEncoder,
    private val decoder: Version2MasterKeyStorageDecoder,
) : Version2MasterKeyFileManager {
    override fun keyFileExists() = legacyKeyFile.exists()

    override fun readKeyFile(): MasterKeyStorageData.Version2 {
        val atomicKeyFile = AtomicFile(legacyKeyFile)
        return DataInputStream(atomicKeyFile.openRead()).use { dis ->
            decoder.decodeOuterKeyStorage(dis)
        }
    }

    override fun writeKeyFile(masterKeyStorageData: MasterKeyStorageData.Version2) {
        legacyKeyFile.writeAtomically { outputStream ->
            encoder.encodeMasterKeyStorageData(masterKeyStorageData).writeTo(outputStream)
        }
    }
}
