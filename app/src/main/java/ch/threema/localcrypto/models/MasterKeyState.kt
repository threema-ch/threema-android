package ch.threema.localcrypto.models

import ch.threema.common.models.CryptographicByteArray

sealed interface MasterKeyState {
    data class WithPassphrase(val protection: PassphraseProtection) : MasterKeyState {
        sealed interface PassphraseProtection {
            data class Version1(
                val protectedKey: CryptographicByteArray,
                val salt: CryptographicByteArray,
                val verification: CryptographicByteArray,
            ) : PassphraseProtection

            data class Version2(
                val argonVersion: Argon2Version,
                val encryptedData: CryptographicByteArray,
                val nonce: CryptographicByteArray,
                val memoryBytes: Int,
                val salt: CryptographicByteArray,
                val iterations: Int,
                val parallelism: Int,
            ) : PassphraseProtection {
                fun toOuterData() = Version2MasterKeyStorageOuterData.PassphraseProtected(
                    argonVersion = argonVersion,
                    encryptedData = encryptedData,
                    nonce = nonce,
                    memoryBytes = memoryBytes,
                    salt = salt,
                    iterations = iterations,
                    parallelism = parallelism,
                )
            }
        }
    }

    data class WithRemoteSecret(
        val parameters: RemoteSecretParameters,
        val encryptedData: CryptographicByteArray,
    ) : MasterKeyState, WithoutPassphrase {
        override fun toInnerData() = Version2MasterKeyStorageInnerData.RemoteSecretProtected(
            parameters = parameters,
            encryptedData = encryptedData,
        )
    }

    data class Plain(
        val masterKeyData: MasterKeyData,
        val wasMigrated: Boolean = false,
    ) : MasterKeyState, WithoutPassphrase {
        override fun toInnerData() = Version2MasterKeyStorageInnerData.Unprotected(
            masterKeyData = masterKeyData,
        )
    }

    sealed interface WithoutPassphrase : MasterKeyState {
        fun toInnerData(): Version2MasterKeyStorageInnerData
    }
}
