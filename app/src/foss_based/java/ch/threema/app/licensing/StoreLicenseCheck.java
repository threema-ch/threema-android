package ch.threema.app.licensing;

import android.app.Activity;
import androidx.annotation.NonNull;

import ch.threema.app.routines.CheckLicenseRoutine;
import ch.threema.app.services.UserService;

public class StoreLicenseCheck {

    private StoreLicenseCheck() {
    }

    public static void checkLicense(@NonNull Activity activity, UserService userService) {
        // stub, no platform store license check in foss based builds
    }
}
