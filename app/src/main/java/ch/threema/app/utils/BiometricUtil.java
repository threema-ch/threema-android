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

package ch.threema.app.utils;

import android.app.Activity;
import android.content.Intent;

import androidx.fragment.app.Fragment;
import ch.threema.app.applock.AppLockActivity;

public class BiometricUtil {
    @Deprecated
    public static void showUnlockDialog(Activity activity, Fragment fragment, boolean testOnly, int id, String authType) {
        Intent intent = AppLockActivity.createIntent(activity != null ? activity : fragment.getActivity(), testOnly, authType);
        if (activity != null) {
            if (id == 0) {
                activity.startActivity(intent);
            } else {
                activity.startActivityForResult(intent, id);
            }
        } else {
            if (id == 0) {
                fragment.startActivity(intent);
            } else {
                fragment.startActivityForResult(intent, id);
            }
            activity = fragment.getActivity();
        }
        if (activity != null) {
            activity.overridePendingTransition(0, 0);
        }
    }
}
