package ch.threema.app.systemupdates.updates;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public class SystemUpdateToVersion31 implements SystemUpdate {

    private final Context context;

    public SystemUpdateToVersion31(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        String key = "pref_key_routine_check_identity_states_time";

        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
        if (p != null) {
            SharedPreferences.Editor e = p.edit();
            e.remove(key);
            e.commit();
        }
    }

    @Override
    public int getVersion() {
        return 31;
    }
}
