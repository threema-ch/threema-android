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
