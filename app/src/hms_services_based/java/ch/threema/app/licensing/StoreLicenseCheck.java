/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.app.licensing;

import android.app.Activity;
import android.content.Context;

import com.DrmSDK.Drm;
import com.DrmSDK.DrmCheckCallback;

import org.slf4j.Logger;

import ch.threema.app.routines.CheckLicenseRoutine;
import ch.threema.app.services.UserService;
import ch.threema.base.utils.LoggingUtil;

public class StoreLicenseCheck implements CheckLicenseRoutine.StoreLicenseChecker {
    private static final Logger logger = LoggingUtil.getThreemaLogger("StoreLicenseCheck");

    private static final String HMS_ID = "5190041000024384032";
    private static final String HMS_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA26ccdC7mLHomHTnKvSRGg7Vuex19xD3qv8CEOUj5lcT5Z81ARby5CVhM/ZM9zKCQcrKmenn1aih6X+uZoNsvBziDUySkrzXPTX/NfoFDQlHgyXan/xsoIPlE1v0D9dLV7fgPOllHxmN8wiwF+woACo3ao/ra2VY38PCZTmfMX/V+hOLHsdRakgWVshzeYTtzMjlLrnYOp5AFXEjFhF0dB92ozAmLzjFJtwyMdpbVD+yRVr+fnLJ6ADhBpoKLjvpn8A7PhpT5wsvogovdr16u/uKhPy5an4DXE0bjWc76bE2SEse/bQTvPoGRw5TjHVWi7uDMFSz3OOGUqLSygucPdwIDAQAB";

    private StoreLicenseCheck() {
    }

    public static void checkLicense(Context context, UserService userService) {
        logger.debug("Check HMS license");
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
                logger.debug("HMS License failed errorCode: {}", errorCode);
                userService.setPolicyResponse(
                    null,
                    null,
                    errorCode
                );
            }
        };
        Drm.check((Activity) context, context.getPackageName(), HMS_ID, HMS_PUBLIC_KEY, callback);
    }
}
