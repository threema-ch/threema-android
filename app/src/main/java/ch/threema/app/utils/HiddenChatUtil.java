package ch.threema.app.utils;

import android.content.Intent;

import androidx.fragment.app.Fragment;
import ch.threema.app.activities.PinLockActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.preference.service.PreferenceService;

public class HiddenChatUtil {
    public static void launchLockCheckDialog(Fragment fragment, PreferenceService preferenceService) {
        launchLockCheckDialog(null, fragment, preferenceService, ThreemaActivity.ACTIVITY_ID_CHECK_LOCK);
    }

    public static void launchLockCheckDialog(ThreemaToolbarActivity activity, PreferenceService preferenceService) {
        launchLockCheckDialog(activity, null, preferenceService, ThreemaActivity.ACTIVITY_ID_CHECK_LOCK);
    }

    public static void launchLockCheckDialog(ThreemaToolbarActivity activity, Fragment fragment, PreferenceService preferenceService, int id) {
        if (preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_SYSTEM) ||
                preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_BIOMETRIC)) {
            BiometricUtil.showUnlockDialog(activity, fragment, true, id, null);
        } else if (preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_PIN)) {
            Intent intent = PinLockActivity.createIntent(activity != null ? activity : fragment.requireContext(), true);
            if (activity != null) {
                activity.startActivityForResult(intent, id);
            } else {
                fragment.startActivityForResult(intent, id);
            }
        }
    }
}
