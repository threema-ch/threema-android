/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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
