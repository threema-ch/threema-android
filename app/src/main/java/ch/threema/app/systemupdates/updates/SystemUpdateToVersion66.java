package ch.threema.app.systemupdates.updates;

import android.Manifest;
import android.content.Context;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.annotation.RequiresPermission;
import ch.threema.app.preference.service.SynchronizedSettingsService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.utils.AndroidContactUtil;
import ch.threema.app.utils.ConfigUtils;
import kotlin.Lazy;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class SystemUpdateToVersion66 implements SystemUpdate {
    private static final Logger logger = getThreemaLogger("SystemUpdateToVersion66");

    private final Lazy<Context> appContextLazy = KoinJavaComponent.inject(Context.class);
    private final Lazy<SynchronizedSettingsService> synchronizedSettingsServiceLazy = KoinJavaComponent.inject(SynchronizedSettingsService.class);
    private final Lazy<SynchronizeContactsService> synchronizeContactsServiceLazy = KoinJavaComponent.inject(SynchronizeContactsService.class);

    @Override
    public void run() {
        var appContext = appContextLazy.getValue();
        if (!ConfigUtils.isPermissionGranted(appContext, Manifest.permission.WRITE_CONTACTS)) {
            return; // best effort
        }

        forceContactResync();
    }

    @RequiresPermission(Manifest.permission.WRITE_CONTACTS)
    private void forceContactResync() {
        logger.info("Force a contacts resync");

        AndroidContactUtil androidContactUtil = AndroidContactUtil.getInstance();
        androidContactUtil.deleteAllThreemaRawContacts();

        SynchronizedSettingsService synchronizedSettingsService = synchronizedSettingsServiceLazy.getValue();
        if (synchronizedSettingsService.isSyncContacts()) {
            try {
                synchronizeContactsServiceLazy.getValue()
                    .instantiateSynchronizationAndRun();
            } catch (Exception e) {
                logger.error("Failed to run contact sync", e);
            }
        }
    }

    @Override
    public String getDescription() {
        return "force a contacts resync";
    }

    @Override
    public int getVersion() {
        return 66;
    }
}
