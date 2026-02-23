package ch.threema.app.systemupdates.updates

import android.content.Context
import ch.threema.app.R
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * In the past, the credentials were stored in plain text. When read, they would be migrated to the encrypted storage and
 * removed from the plain text store. This migrate-upon-read logic no longer exists, therefore now this system update exists
 * to ensure that users, who may not have had their credentials migrated yet, won't lose them.
 */
class SystemUpdateToVersion118 : SystemUpdate, KoinComponent {

    private val appContext: Context by inject()
    private val preferenceStore: PreferenceStore by inject()
    private val encryptedPreferenceStore: EncryptedPreferenceStore by inject()

    override fun run() {
        KEY_IDS.forEach { keyId ->
            migrate(appContext.getString(keyId))
        }
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

    override fun getVersion() = VERSION

    override fun getDescription() = "ensure credentials are encrypted"

    companion object {
        const val VERSION = 118

        private val KEY_IDS = arrayOf(
            R.string.preferences__license_username,
            R.string.preferences__license_password,
            R.string.preferences__onprem_server,
        )
    }
}
