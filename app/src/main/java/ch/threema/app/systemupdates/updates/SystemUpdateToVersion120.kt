package ch.threema.app.systemupdates.updates

import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import org.koin.core.component.inject

class SystemUpdateToVersion120 : SystemUpdate {

    private val preferenceStore: PreferenceStore by inject()
    private val encryptedPreferenceStore: EncryptedPreferenceStore by inject()

    override fun run() {
        KEYS.forEach { key ->
            preferenceStore.remove(key)
            encryptedPreferenceStore.remove(key)
        }
    }

    override val version = 120

    override fun getDescription() = "remove obsolete preferences"

    companion object {
        private val KEYS = arrayOf(
            "pref_app_logo_light_url",
            "pref_app_logo_dark_url",
        )
    }
}
