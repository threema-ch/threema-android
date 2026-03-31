package ch.threema.app.systemupdates.updates

import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.base.utils.getThreemaLogger
import java.io.IOException
import javax.crypto.IllegalBlockSizeException
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion121")

/**
 * In the past, we did not distinguish between null and empty strings or byte arrays when writing encrypted preferences.
 * Now we do, so we need to correct previously written empty values by removing them, at least in the cases where we
 * know that an empty string/byte array is not a valid value.
 */
class SystemUpdateToVersion121 : SystemUpdate {
    private val encryptedPreferenceStore: EncryptedPreferenceStore by inject()

    override fun run() {
        arrayOf(
            "pref_key_pin_lock_code",
            "pref_custom_support_url",
            "pref_push_token",
            "pref_threema_safe_server_name",
            "pref_threema_safe_server_username",
            "pref_threema_safe_server_password",
            "pref_work_safe_mdm_config",
            "pref_threema_safe_masterkey",
        )
            .forEach { key ->
                encryptedPreferenceStore.removeIfEmpty(key)
            }
    }

    private fun EncryptedPreferenceStore.removeIfEmpty(key: String) {
        try {
            val value = getBytes(key)
            if (value != null && value.isEmpty()) {
                remove(key)
            }
        } catch (e: IOException) {
            // If the value can not be decrypted, we remove it entirely
            if (e.cause is IllegalBlockSizeException) {
                remove(key)
                return
            }
            logger.error("Failed to remove encrypted preference {}", key, e)
        }
    }

    override val version = 121

    override fun getDescription() = "remove empty encrypted preferences"
}
