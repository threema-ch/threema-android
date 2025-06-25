/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.systemupdates.updates;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.preference.service.PreferenceService;

/**
 * migrate locking prefs
 */
public class SystemUpdateToVersion54 implements SystemUpdate {

    private @NonNull final Context context;

    public SystemUpdateToVersion54(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        // Note: PreferenceService is not available at this time if a passphrase has been set!
        String lockMechanism = PreferenceService.LockingMech_NONE;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sharedPreferences.contains(context.getString(R.string.preferences__lock_mechanism))) {
            lockMechanism = sharedPreferences.getString(context.getString(R.string.preferences__lock_mechanism), PreferenceService.LockingMech_NONE);
        }

        if (!PreferenceService.LockingMech_NONE.equals(lockMechanism)) {
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
