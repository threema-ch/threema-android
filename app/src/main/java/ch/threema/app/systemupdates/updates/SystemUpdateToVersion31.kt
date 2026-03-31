package ch.threema.app.systemupdates.updates

import android.content.SharedPreferences
import androidx.core.content.edit
import org.koin.core.component.inject

class SystemUpdateToVersion31 : SystemUpdate {

    private val sharedPreferences: SharedPreferences by inject()

    override fun run() {
        sharedPreferences.edit {
            remove("pref_key_routine_check_identity_states_time")
        }
    }

    override val version = 31
}
