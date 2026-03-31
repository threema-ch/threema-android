package ch.threema.app.routines;

import android.content.Context;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.restrictions.AppRestrictionService;
import ch.threema.app.restrictions.AppRestrictions;
import ch.threema.app.services.DeviceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceUser;
import ch.threema.domain.models.UserCredentials;
import ch.threema.app.utils.ConfigUtils;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.protocol.api.APIConnector;
import ch.threema.domain.stores.IdentityStore;

/***
 * Send (only in work build) the infos to the work info resource
 */
public class UpdateWorkInfoRoutine implements Runnable {
    private static final Logger logger = getThreemaLogger("UpdateWorkInfoRoutine");

    @NonNull
    private final APIConnector apiConnector;
    @NonNull
    private final IdentityStore identityStore;
    @Nullable
    private final DeviceService deviceService;
    @NonNull
    private final LicenseService<?> licenseService;
    @NonNull
    private final AppRestrictions appRestrictions;

    public UpdateWorkInfoRoutine(
        @NonNull
        APIConnector apiConnector,
        @NonNull
        IdentityStore identityStore,
        @Nullable
        DeviceService deviceService,
        @NonNull
        LicenseService<?> licenseService,
        @NonNull
        AppRestrictions appRestrictions
    ) {
        this.apiConnector = apiConnector;
        this.identityStore = identityStore;
        this.deviceService = deviceService;
        this.licenseService = licenseService;
        this.appRestrictions = appRestrictions;
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
            boolean hasIdentity = identityStore.getIdentityString() != null;

            if (userCredentials == null && hasIdentity) {
                // In case we have no credentials but an identity, we should log an error and abort
                // this routine.
                logger.error("Cannot run update work info routine due to missing credentials");
                return;
            } else if (userCredentials == null) {
                // In case there is no identity and no credentials, the app is very likely not set
                // up and this routine can safely be skipped.
                logger.info("Skipping update work info routine as there are no credentials yet");
                return;
            } else if (!hasIdentity) {
                // If there are credentials but no identity, we skip this routine as we need an
                // identity for this routine.
                logger.info("Skipping update work info routine as there is no identity");
                return;
            }

            try {
                String mdmFirstName = appRestrictions.getFirstName();
                String mdmLastName = appRestrictions.getLastName();
                String mdmJobTitle = appRestrictions.getJobTitle();
                String mdmDepartment = appRestrictions.getDepartment();
                String mdmCSI = appRestrictions.getCsi();
                String mdmCategory = appRestrictions.getCategory();
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
            } catch (Exception e) {
                logger.error("Failed to update work info", e);
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
        return new UpdateWorkInfoRoutine(
            serviceManager.getAPIConnector(),
            serviceManager.getIdentityStore(),
            serviceManager.getDeviceService(),
            serviceManager.getLicenseService(),
            KoinJavaComponent.get(AppRestrictions.class)
        );
    }
}
