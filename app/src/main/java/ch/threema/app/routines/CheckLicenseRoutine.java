/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.routines;

import android.content.Context;
import android.content.ReceiverCallNotAllowedException;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ch.threema.app.BuildFlavor;
import ch.threema.app.licensing.StoreLicenseCheck;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceThreema;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.IntentDataUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.stores.IdentityStore;

/**
 * Checking the License of current Threema and send a not allowed broadcast
 */
public class CheckLicenseRoutine implements Runnable {
    private static final Logger logger = getThreemaLogger("CheckLicenseRoutine");

    @NonNull
    private final Context context;
    @NonNull
    private final APIConnector apiConnector;
    @NonNull
    private final UserService userService;
    @NonNull
    private final DeviceService deviceService;
    @NonNull
    private final LicenseService<?> licenseService;
    @NonNull
    private final IdentityStore identityStore;

    public CheckLicenseRoutine(
        @NonNull
        Context context,
        @NonNull
        APIConnector apiConnector,
        @NonNull
        UserService userService,
        @NonNull
        DeviceService deviceService,
        @NonNull
        LicenseService<?> licenseService,
        @NonNull
        IdentityStore identityStore
    ) {
        this.context = context;
        this.apiConnector = apiConnector;
        this.userService = userService;
        this.deviceService = deviceService;
        this.licenseService = licenseService;
        this.identityStore = identityStore;
    }

    private void invalidLicense(String message) {
        try {
            LocalBroadcastManager.getInstance(this.context).sendBroadcast(IntentDataUtil.createActionIntentLicenseNotAllowed(message));
        } catch (ReceiverCallNotAllowedException x) {
            logger.error("Exception", x);
        }
    }

    @Override
    public void run() {
        switch (BuildFlavor.getCurrent().getLicenseType()) {
            case GOOGLE:
            case HMS:
                StoreLicenseCheck.checkLicense(context, userService);
                break;
            case SERIAL:
            case GOOGLE_WORK:
            case HMS_WORK:
            case ONPREM:
                this.checkSerial();
                break;
        }
    }

    private void checkSerial() {
        logger.info("Checking serial license");

        String error = licenseService.validate(true);
        if (error != null) {
            invalidLicense(error);
        } else {
            userService.setCredentials(licenseService.loadCredentials());

            if (licenseService instanceof LicenseServiceThreema && BuildFlavor.getCurrent().getMaySelfUpdate()) {
                LicenseServiceThreema<?> licenseServiceThreema = (LicenseServiceThreema<?>) licenseService;
                if (licenseServiceThreema.getUpdateMessage() != null && !licenseServiceThreema.isUpdateMessageShown()) {
                    try {
                        LocalBroadcastManager.getInstance(this.context).sendBroadcast(
                            IntentDataUtil.createActionIntentUpdateAvailable(
                                licenseServiceThreema.getUpdateMessage(),
                                licenseServiceThreema.getUpdateUrl()
                            )
                        );
                        licenseServiceThreema.setUpdateMessageShown(true);
                    } catch (ReceiverCallNotAllowedException x) {
                        logger.error("Exception", x);
                    }
                }
            }

            //run update work info route on the work build
            if (ConfigUtils.isWorkBuild()) {
                (new UpdateWorkInfoRoutine(
                    this.context,
                    this.apiConnector,
                    this.identityStore,
                    this.deviceService,
                    this.licenseService
                )).run();
            }
        }
    }
}
