/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

import android.content.Intent;
import android.os.Build;

import androidx.fragment.app.Fragment;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.PinLockActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.services.PreferenceService;

public class HiddenChatUtil {
    public static void launchLockCheckDialog(Fragment fragment, PreferenceService preferenceService) {
        launchLockCheckDialog(null, fragment, preferenceService, ThreemaActivity.ACTIVITY_ID_CHECK_LOCK);
    }

    public static void launchLockCheckDialog(ThreemaToolbarActivity activity, PreferenceService preferenceService) {
        launchLockCheckDialog(activity, null, preferenceService, ThreemaActivity.ACTIVITY_ID_CHECK_LOCK);
    }

    public static void launchLockCheckDialog(ThreemaToolbarActivity activity, Fragment fragment, PreferenceService preferenceService, int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            (preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_SYSTEM) ||
                preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_BIOMETRIC))) {
            BiometricUtil.showUnlockDialog(activity, fragment, true, id, null);
        } else if (preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_PIN)) {
            Intent intent = new Intent(activity != null ? activity : fragment.getActivity(), PinLockActivity.class);
            intent.putExtra(ThreemaApplication.INTENT_DATA_CHECK_ONLY, true);
            if (activity != null) {
                activity.startActivityForResult(intent, id);
            } else {
                fragment.startActivityForResult(intent, id);
            }
        }
    }
}
