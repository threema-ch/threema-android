package ch.threema.app.systemupdates.updates;

import org.koin.java.KoinJavaComponent;

import ch.threema.app.preference.service.SynchronizedSettingsService;
import ch.threema.app.services.SynchronizeContactsService;
import kotlin.Lazy;

public class SystemUpdateToVersion14 implements SystemUpdate {

    private final Lazy<SynchronizeContactsService> synchronizeContactsServiceLazy = KoinJavaComponent.inject(SynchronizeContactsService.class);
    private final Lazy<SynchronizedSettingsService> synchronizedSettingsServiceLazy = KoinJavaComponent.inject(SynchronizedSettingsService.class);

    @Override
    public void run() {
        //check if auto sync is enabled
        SynchronizedSettingsService synchronizedSettingsService = synchronizedSettingsServiceLazy.getValue();
        if (synchronizedSettingsService.isSyncContacts()) {
            //disable sync
            final SynchronizeContactsService synchronizeContactService;
            try {
                synchronizeContactService = synchronizeContactsServiceLazy.getValue();
            } catch (Exception e) {
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
        return 14;
    }
}
