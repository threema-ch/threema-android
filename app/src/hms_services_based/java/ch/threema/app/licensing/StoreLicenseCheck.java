package ch.threema.app.licensing;

import android.app.Activity;
import androidx.annotation.NonNull;

import com.DrmSDK.Drm;
import com.DrmSDK.DrmCheckCallback;

import org.slf4j.Logger;

import ch.threema.app.services.UserService;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class StoreLicenseCheck {
    private static final Logger logger = getThreemaLogger("StoreLicenseCheck");

    private static final String HMS_ID = "5190041000024384032";
    private static final String HMS_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA26ccdC7mLHomHTnKvSRGg7Vuex19xD3qv8CEOUj5lcT5Z81ARby5CVhM/ZM9zKCQcrKmenn1aih6X+uZoNsvBziDUySkrzXPTX/NfoFDQlHgyXan/xsoIPlE1v0D9dLV7fgPOllHxmN8wiwF+woACo3ao/ra2VY38PCZTmfMX/V+hOLHsdRakgWVshzeYTtzMjlLrnYOp5AFXEjFhF0dB92ozAmLzjFJtwyMdpbVD+yRVr+fnLJ6ADhBpoKLjvpn8A7PhpT5wsvogovdr16u/uKhPy5an4DXE0bjWc76bE2SEse/bQTvPoGRw5TjHVWi7uDMFSz3OOGUqLSygucPdwIDAQAB";

    private StoreLicenseCheck() {
    }

    public static void checkLicense(@NonNull Activity activity, UserService userService) {
        logger.info("Check HMS license");
        DrmCheckCallback callback = new DrmCheckCallback() {
            @Override
            public void onCheckSuccess(String signData, String signature) {
                logger.info("HMS License OK");
                userService.setPolicyResponse(
                    signData,
                    signature,
                    0
                );
            }

            @Override
            public void onCheckFailed(int errorCode) {
                logger.info("HMS License failed errorCode: {}", errorCode);
                userService.setPolicyResponse(
                    null,
                    null,
                    errorCode
                );
            }
        };
        Drm.check(activity, activity.getPackageName(), HMS_ID, HMS_PUBLIC_KEY, callback);
    }
}
