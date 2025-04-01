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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

import org.slf4j.Logger;

import androidx.biometric.BiometricManager;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.BiometricLockActivity;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.activities.BiometricLockActivity.INTENT_DATA_AUTHENTICATION_TYPE;

public class BiometricUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("BiometricUtil");

    public static boolean isBiometricsSupported(Context context) {
        String toast = context.getString(R.string.biometrics_not_avilable);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_BIOMETRIC) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                toast = context.getString(R.string.biometrics_no_permission);
            } else {
                BiometricManager biometricManager = BiometricManager.from(context);
                switch (biometricManager.canAuthenticate()) {
                    case BiometricManager.BIOMETRIC_SUCCESS:
                        return true;
                    case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                        toast = context.getString(R.string.biometrics_not_enrolled);
                        break;
                    default:
                        break;
                }
            }
        }
        Toast.makeText(context, toast, Toast.LENGTH_LONG).show();
        return false;
    }

    public static boolean isHardwareSupported(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int result = biometricManager.canAuthenticate();

        return result != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE && result != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE;
    }

    public static void showUnlockDialog(Activity activity, boolean testOnly, int id, String authType) {
        showUnlockDialog(activity, null, testOnly, id, authType);
    }

    public static void showUnlockDialog(Activity activity, Fragment fragment, boolean testOnly, int id, String authType) {
        logger.debug("launch BiometricLockActivity");
        Intent intent = new Intent(activity != null ? activity : fragment.getActivity(), BiometricLockActivity.class);
        if (testOnly) {
            intent.putExtra(ThreemaApplication.INTENT_DATA_CHECK_ONLY, true);
        }
        if (authType != null) {
            intent.putExtra(INTENT_DATA_AUTHENTICATION_TYPE, authType);
        }
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
