package ch.threema.localcrypto

import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.exceptions.CryptoException
import ch.threema.localcrypto.models.MasterKeyState
import ch.threema.localcrypto.models.RemoteSecret
import ch.threema.localcrypto.models.RemoteSecretParameters

private val logger = getThreemaLogger("MasterKeyCrypto")

class MasterKeyCrypto(
    private val converter: MasterKeyStorageStateConverter,
    private val version2Crypto: Version2MasterKeyCrypto,
    private val version1Crypto: Version1MasterKeyCrypto,
) {
    fun verifyPassphrase(keyState: MasterKeyState.WithPassphrase, passphrase: CharArray): Boolean =
        when (val protection = keyState.protection) {
            is MasterKeyState.WithPassphrase.PassphraseProtection.Version1 -> {
                val masterKeyData = version1Crypto.decryptPassphraseProtectedMasterKey(protection, passphrase)
                version1Crypto.checkVerification(masterKeyData, protection.verification)
            }
            is MasterKeyState.WithPassphrase.PassphraseProtection.Version2 -> {
                try {
                    version2Crypto.decryptWithPassphrase(protection.toOuterData(), passphrase)
                    true
                } catch (e: CryptoException) {
                    logger.warn("Check passphrase failed due to wrong passphrase", e)
                    false
                }
            }
        }

    @Throws(CryptoException::class)
    fun decryptWithPassphrase(
        keyState: MasterKeyState.WithPassphrase,
        passphrase: CharArray,
    ): MasterKeyState.WithoutPassphrase {
        when (val protection = keyState.protection) {
            is MasterKeyState.WithPassphrase.PassphraseProtection.Version1 -> {
                val masterKeyData = version1Crypto.decryptPassphraseProtectedMasterKey(protection, passphrase)
                if (!version1Crypto.checkVerification(masterKeyData, protection.verification)) {
                    logger.warn("Failed to unlock with version 1 passphrase due to wrong passphrase")
                    throw CryptoException()
                }
                return MasterKeyState.Plain(masterKeyData, wasMigrated = true)
            }
            is MasterKeyState.WithPassphrase.PassphraseProtection.Version2 -> {
                val innerData = version2Crypto.decryptWithPassphrase(protection.toOuterData(), passphrase)
                return converter.toKeyState(innerData)
            }
        }
    }

    @Throws(CryptoException::class)
    fun encryptWithPassphrase(
        keyState: MasterKeyState.WithoutPassphrase,
        passphrase: CharArray,
    ): MasterKeyState.WithPassphrase =
        converter.toKeyState(
            version2Crypto.encryptWithPassphrase(keyState.toInnerData(), passphrase),
        )

    @Throws(CryptoException::class)
    fun decryptWithRemoteSecret(
        keyState: MasterKeyState.WithRemoteSecret,
        remoteSecret: RemoteSecret,
    ): MasterKeyState.Plain {
        val unprotected = version2Crypto.decryptWithRemoteSecret(remoteSecret, keyState.toInnerData())
        return MasterKeyState.Plain(unprotected.masterKeyData)
    }

    @Throws(CryptoException::class)
    fun encryptWithRemoteSecret(
        keyState: MasterKeyState.Plain,
        remoteSecret: RemoteSecret,
        parameters: RemoteSecretParameters,
    ): MasterKeyState.WithRemoteSecret =
        converter.toKeyState(
            version2Crypto.encryptWithRemoteSecret(remoteSecret, parameters, keyState.toInnerData()),
        )
}
