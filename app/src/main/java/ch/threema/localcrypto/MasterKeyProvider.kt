package ch.threema.localcrypto

import ch.threema.common.awaitNonNull
import ch.threema.common.awaitNull
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides read-only access to the [MasterKey], if it is unlocked.
 * The [MasterKeyProvider] is agnostic to the types of locks, e.g., it does not know
 * if a passphrase or remote secret is used.
 */
class MasterKeyProvider(
    private val masterKeyFlow: StateFlow<MasterKey?>,
) {
    fun getMasterKeyOrNull(): MasterKey? =
        masterKeyFlow.value

    @Throws(MasterKeyLockedException::class)
    fun getMasterKey(): MasterKey =
        getMasterKeyOrNull()
            ?: throw MasterKeyLockedException()

    fun isLocked(): Boolean =
        getMasterKeyOrNull() == null

    suspend fun awaitUnlocked(): MasterKey =
        masterKeyFlow.awaitNonNull()

    suspend fun awaitLocked() {
        masterKeyFlow.awaitNull()
    }
}
