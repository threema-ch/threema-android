package ch.threema.app.systemupdates.updates;

import android.content.Context;
import android.content.SharedPreferences;

import org.koin.java.KoinJavaComponent;

import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.preference.service.PreferenceService;
import kotlin.Lazy;

/**
 * migrate locking prefs
 */
public class SystemUpdateToVersion54 implements SystemUpdate {

    private final Lazy<Context> appContextLazy = KoinJavaComponent.inject(Context.class);

    @Override
    public void run() {
        var appContext = appContextLazy.getValue();

        String lockMechanism = PreferenceService.LOCKING_MECH_NONE;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        if (sharedPreferences.contains(appContext.getString(R.string.preferences__lock_mechanism))) {
            lockMechanism = sharedPreferences.getString(appContext.getString(R.string.preferences__lock_mechanism), PreferenceService.LOCKING_MECH_NONE);
        }

        if (!PreferenceService.LOCKING_MECH_NONE.equals(lockMechanism)) {
            if (sharedPreferences.getBoolean("pref_key_system_lock_enabled", false) ||
                sharedPreferences.getBoolean("pref_key_pin_lock_enabled", false)) {

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("pref_app_lock_enabled", true);
                editor.commit();
            }
        }

        // clean up old prefs
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove("pref_key_system_lock_enabled");
        editor.remove("pref_key_pin_lock_enabled");
        editor.commit();
    }

    @Override
    public int getVersion() {
        return 54;
    }
}
