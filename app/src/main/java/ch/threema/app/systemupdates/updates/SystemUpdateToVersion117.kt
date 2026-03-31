package ch.threema.app.systemupdates.updates

import ch.threema.base.utils.getThreemaLogger
import java.security.KeyStore

private val logger = getThreemaLogger("SystemUpdateToVersion117")

class SystemUpdateToVersion117 : SystemUpdate {

    override fun run() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            keyStore.deleteEntry(KEY_NAME)
        } catch (e: Exception) {
            logger.warn("Failed to delete key", e)
        }
    }

    override val version = 117

    override fun getDescription() = "delete obsolete pinlock key from keystore"

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_NAME = "threema_pinlock_key"
    }
}
