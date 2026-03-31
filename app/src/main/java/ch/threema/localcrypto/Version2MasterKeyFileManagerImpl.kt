package ch.threema.localcrypto

import androidx.core.util.AtomicFile
import ch.threema.android.writeAtomically
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.deleteSecurely
import ch.threema.localcrypto.models.MasterKeyStorageData
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException

private val logger = getThreemaLogger("Version2MasterKeyFileManagerImpl")

/**
 * @param keyFile The file that stores the master key storage data, encrypted by [keyStoreCrypto]
 * @param unencryptedKeyFile The file that used to store the master key data in unencrypted format, before the introduction of [keyStoreCrypto]
 */
class Version2MasterKeyFileManagerImpl(
    private val keyFile: File,
    private val unencryptedKeyFile: File,
    private val encoder: Version2MasterKeyStorageEncoder,
    private val decoder: Version2MasterKeyStorageDecoder,
    private val keyStoreCrypto: KeyStoreCrypto,
) : Version2MasterKeyFileManager {
    override fun keyFileExists() = keyFile.exists() || unencryptedKeyFile.exists()

    @Throws(IOException::class)
    override fun readKeyFile(): MasterKeyStorageData.Version2 {
        val keyDataReadFromUnencryptedKeyFile = if (unencryptedKeyFile.exists()) {
            readUnencryptedFile()
        } else {
            null
        }
        if (keyDataReadFromUnencryptedKeyFile != null) {
            logger.info("Migrating master key file, protecting with Android key store")
            writeKeyFile(keyDataReadFromUnencryptedKeyFile)
        }

        val encryptedKeyData = readEncryptedKeyFile()
        val keyData = keyStoreCrypto.decryptWithExistingSecretKey(encryptedKeyData)
        val masterKeyStorageData = DataInputStream(ByteArrayInputStream(keyData)).use { dis ->
            decoder.decodeOuterKeyStorage(dis)
        }

        if (keyDataReadFromUnencryptedKeyFile != null) {
            // Perform a sanity check before deleting the old key file
            if (keyDataReadFromUnencryptedKeyFile != masterKeyStorageData) {
                error("Failed to migrate, old and new key data are not the same")
            }

            logger.info("Deleting unencrypted master key file")
            unencryptedKeyFile.deleteSecurely()
        }

        return masterKeyStorageData
    }

    private fun readUnencryptedFile(): MasterKeyStorageData.Version2 =
        DataInputStream(AtomicFile(unencryptedKeyFile).openRead()).use { dis ->
            decoder.decodeOuterKeyStorage(dis)
        }

    private fun readEncryptedKeyFile(): ByteArray =
        AtomicFile(keyFile).openRead().use { inputStream ->
            inputStream.readBytes()
        }

    @Throws(IOException::class)
    override fun writeKeyFile(masterKeyStorageData: MasterKeyStorageData.Version2) {
        val previousKeyAlias = if (keyFile.exists()) {
            keyStoreCrypto.extractSecretKeyAlias(readEncryptedKeyFile())
        } else {
            null
        }

        keyFile.writeAtomically { outputStream ->
            val keyData = encoder.encodeMasterKeyStorageData(masterKeyStorageData).toByteArray()
            val encryptedKeyData = keyStoreCrypto.encryptWithNewSecretKey(keyData, previousKeyAlias)
            outputStream.write(encryptedKeyData)
        }

        if (previousKeyAlias != null) {
            // It is important that the old secret key is deleted only AFTER the key file was written,
            // such that we could still revert to the old key file in case writing fails.
            keyStoreCrypto.deleteSecretKey(previousKeyAlias)
        }
    }
}
