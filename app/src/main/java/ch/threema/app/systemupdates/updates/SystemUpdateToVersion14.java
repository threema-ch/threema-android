package ch.threema.app.systemupdates.updates;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.SynchronizeContactsService;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.localcrypto.exceptions.MasterKeyLockedException;

public class SystemUpdateToVersion14 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion14");

    private @NonNull final ServiceManager serviceManager;

    public SystemUpdateToVersion14(@NonNull ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        //check if auto sync is enabled
        PreferenceService preferenceService = serviceManager.getPreferenceService();
        if (preferenceService.isSyncContacts()) {
            //disable sync
            final SynchronizeContactsService synchronizeContactService;
            try {
                synchronizeContactService = serviceManager.getSynchronizeContactsService();
            } catch (MasterKeyLockedException e) {
                throw new RuntimeException("Failed to get synchronize contacts service", e);
            }
            synchronizeContactService.disableSyncFromLocal(new Runnable() {
                @Override
                public void run() {
                    synchronizeContactService.enableSyncFromLocal();
                }
            });
        }
    }

    @Override
    public int getVersion() {
        return 13;
    }
}
