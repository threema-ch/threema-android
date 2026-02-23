package ch.threema.app.licensing;

import android.content.Context;

import ch.threema.app.routines.CheckLicenseRoutine;
import ch.threema.app.services.UserService;

public class StoreLicenseCheck {

    private StoreLicenseCheck() {
    }

    public static void checkLicense(Context context, UserService userService) {
        // stub, no platform store license check in foss based builds
    }
}
