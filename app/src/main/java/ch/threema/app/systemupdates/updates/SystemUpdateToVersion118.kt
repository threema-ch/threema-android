package ch.threema.app.systemupdates.updates

import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import org.koin.core.component.inject

/**
 * In the past, the credentials were stored in plain text. When read, they would be migrated to the encrypted storage and
 * removed from the plain text store. This migrate-upon-read logic no longer exists, therefore now this system update exists
 * to ensure that users, who may not have had their credentials migrated yet, won't lose them.
 */
class SystemUpdateToVersion118 : SystemUpdate {

    private val preferenceStore: PreferenceStore by inject()
    private val encryptedPreferenceStore: EncryptedPreferenceStore by inject()

    override fun run() {
        KEYS.forEach(::migrate)
    }

    private fun migrate(key: String) {
        if (encryptedPreferenceStore.containsKey(key)) {
            return
        }
        val value = preferenceStore.getString(key)
            ?: return
        encryptedPreferenceStore.save(key, value)
        preferenceStore.remove(key)
    }

    override val version = 118

    override fun getDescription() = "ensure credentials are encrypted"

    companion object {
        private val KEYS = arrayOf(
            "pref_license_username",
            "pref_license_password",
            "pref_onprem_server",
        )
    }
}
