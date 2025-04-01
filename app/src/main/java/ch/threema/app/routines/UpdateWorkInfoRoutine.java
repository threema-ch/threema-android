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

package ch.threema.app.routines;

import android.content.Context;

import org.slf4j.Logger;

import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.AppRestrictionService;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.app.services.license.UserCredentials;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.stores.IdentityStoreInterface;

/***
 * Send (only in work build) the infos to the work info resource
 */
public class UpdateWorkInfoRoutine implements Runnable {
    private static final Logger logger = LoggingUtil.getThreemaLogger("UpdateWorkInfoRoutine");

    private final APIConnector apiConnector;
    private final IdentityStoreInterface identityStore;
    private final DeviceService deviceService;
    private final LicenseService licenseService;
    private final Context context;

    public UpdateWorkInfoRoutine(
        Context context,
        APIConnector apiConnector,
        IdentityStoreInterface identityStore,
        DeviceService deviceService,
        LicenseService licenseService
    ) {
        this.context = context;
        this.apiConnector = apiConnector;
        this.identityStore = identityStore;
        this.deviceService = deviceService;
        this.licenseService = licenseService;
    }

    @Override
    public void run() {
        if (!ConfigUtils.isWorkBuild()) {
            //ignore on a not-work build
            return;
        }

        if (this.deviceService == null || this.deviceService.isOnline()) {
            logger.info("Update work info");

            UserCredentials userCredentials = ((LicenseServiceUser) this.licenseService).loadCredentials();

            if (userCredentials == null) {
                logger.error("no credentials found");
                return;
            }

            try {
                String mdmFirstName = AppRestrictionUtil.getStringRestriction(this.context.getString(
                    R.string.restriction__firstname
                ));

                String mdmLastName = AppRestrictionUtil.getStringRestriction(this.context.getString(
                    R.string.restriction__lastname
                ));

                String mdmJobTitle = AppRestrictionUtil.getStringRestriction(this.context.getString(
                    R.string.restriction__job_title
                ));

                String mdmDepartment = AppRestrictionUtil.getStringRestriction(this.context.getString(
                    R.string.restriction__department
                ));

                String mdmCSI = AppRestrictionUtil.getStringRestriction(this.context.getString(
                    R.string.restriction__csi
                ));

                String mdmCategory = AppRestrictionUtil.getStringRestriction(this.context.getString(
                    R.string.restriction__category
                ));

                if (this.apiConnector.updateWorkInfo(
                    userCredentials.username,
                    userCredentials.password,
                    this.identityStore,
                    mdmFirstName,
                    mdmLastName,
                    mdmJobTitle,
                    mdmDepartment,
                    mdmCSI,
                    mdmCategory,
                    AppRestrictionService.getInstance().getMdmSource()
                )) {
                    logger.debug("work info successfully updated");
                } else {
                    logger.error("failed to update work info");
                }
            } catch (Exception x) {
                logger.error("Exception", x);
            }
        } else {
            logger.error("device is not online");
        }
    }

    /**
     * start a update in a new thread
     *
     * @return the new created thread or null if the thread could not be created
     */
    @Nullable
    public static Thread start() {
        UpdateWorkInfoRoutine updateWorkInfoRoutine = create();
        if (updateWorkInfoRoutine != null) {
            Thread t = new Thread(updateWorkInfoRoutine);
            t.start();
            return t;
        } else {
            return null;
        }
    }

    @Nullable
    public static UpdateWorkInfoRoutine create() {
        ServiceManager serviceManager = ThreemaApplication.getServiceManager();

        if (serviceManager == null) {
            return null;
        }
        try {
            return new UpdateWorkInfoRoutine(
                serviceManager.getContext(),
                serviceManager.getAPIConnector(),
                serviceManager.getIdentityStore(),
                serviceManager.getDeviceService(),
                serviceManager.getLicenseService()
            );
        } catch (FileSystemNotPresentException e) {
            logger.error("File system not present", e);
            return null;
        }
    }
}
