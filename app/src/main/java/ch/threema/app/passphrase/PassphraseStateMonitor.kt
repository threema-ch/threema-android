package ch.threema.app.passphrase

import android.content.Context
import ch.threema.app.services.PassphraseService
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.PassphraseLockState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map

class PassphraseStateMonitor(
    private val appContext: Context,
    private val masterKeyManager: MasterKeyManager,
) {
    suspend fun monitorPassphraseLock() {
        masterKeyManager.passphraseLockState
            .map { lockState ->
                when (lockState) {
                    PassphraseLockState.NO_PASSPHRASE,
                    PassphraseLockState.LOCKED,
                    -> false
                    PassphraseLockState.UNLOCKED -> true
                }
            }
            .distinctUntilChanged()
            .dropWhile { isUnlocked -> !isUnlocked }
            .collect { serviceNeeded ->
                if (serviceNeeded) {
                    PassphraseService.start(appContext)
                } else {
                    PassphraseService.stop(appContext)
                }
            }
    }
}
