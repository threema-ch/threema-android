package ch.threema.app.home

import androidx.lifecycle.lifecycleScope
import ch.threema.app.utils.DispatcherProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.localcrypto.MasterKeyManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("MasterKeyPersisting")

class MasterKeyPersisting(
    private val masterKeyManager: MasterKeyManager,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun persistMasterKeyIfNeeded(activity: HomeActivity, onError: Runnable) {
        activity.lifecycleScope.launch(dispatcherProvider.io) {
            try {
                masterKeyManager.persistKeyDataIfNeeded()
            } catch (e: Exception) {
                logger.error("Failed to persist master key", e)
                withContext(dispatcherProvider.main) {
                    onError.run()
                }
            }
        }
    }
}
