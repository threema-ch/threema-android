package ch.threema.app.systemupdates.updates

import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.common.takeUnlessEmpty
import org.koin.core.component.inject

class SystemUpdateToVersion119 : SystemUpdate {

    private val preferenceStore: PreferenceStore by inject()
    private val encryptedPreferenceStore: EncryptedPreferenceStore by inject()

    override fun run() {
        val oldKey = "pref_onprem_server"
        val newKey = "pref_oppf_url"
        val value = encryptedPreferenceStore.getString(oldKey)
            ?: return
        preferenceStore.save(newKey, value.takeUnlessEmpty())
        encryptedPreferenceStore.remove(oldKey)
    }

    override val version = 119

    override fun getDescription() = "store oppf url in unencrypted format"
}
