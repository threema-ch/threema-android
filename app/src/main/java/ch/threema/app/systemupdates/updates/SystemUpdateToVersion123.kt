package ch.threema.app.systemupdates.updates

import ch.threema.app.stores.PreferenceStore
import java.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SystemUpdateToVersion123 : SystemUpdate, KoinComponent {

    private val preferenceStore: PreferenceStore by inject()

    override fun run() {
        val debugLogEnabled = preferenceStore.getBoolean("pref_key_message_log_switch")
        if (debugLogEnabled) {
            preferenceStore.save("pref_key_debug_log_enable_time", Instant.now())
        }
        preferenceStore.remove("pref_key_message_log_switch")
    }

    override val version = 123

    override fun getDescription() = "store debug log enable timestamp"
}
