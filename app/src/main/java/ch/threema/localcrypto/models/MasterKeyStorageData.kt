package ch.threema.localcrypto.models

/**
 * Represents the master key related data that is stored locally, including its different versions and the hierarchy in which their data is stored.
 * It does not reflect whether the master key is locked, only whether and how it is protected.
 */
sealed class MasterKeyStorageData {
    /**
     * Version 2 was introduced in app version 6.2.0. Primarily, it adds support for Remote Secrets.
     */
    data class Version2(
        val outerData: Version2MasterKeyStorageOuterData,
    ) : MasterKeyStorageData()

    /**
     * Version 1 was used prior to app version 6.2.0. It supports protecting the master key with an optional passphrase.
     */
    data class Version1(
        val data: Version1MasterKeyStorageData,
    ) : MasterKeyStorageData()
}
