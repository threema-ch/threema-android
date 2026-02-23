package ch.threema.app.systemupdates.updates;

import androidx.annotation.NonNull;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.WebClientSessionModel;

public class SystemUpdateToVersion40 implements SystemUpdate {
    private @NonNull final ServiceManager serviceManager;

    public SystemUpdateToVersion40(ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        PreferenceService preferenceService = serviceManager.getPreferenceService();
        String currentPushToken = preferenceService.getPushToken();

        if (!TestUtil.isEmptyOrNull(currentPushToken)) {
            // update all
            serviceManager.getDatabaseService().getWritableDatabase()
                .execSQL(
                    "UPDATE " + WebClientSessionModel.TABLE + " " + "SET " + WebClientSessionModel.COLUMN_PUSH_TOKEN + "=?",
                    new String[]{currentPushToken}
                );
        }
    }

    @Override
    public int getVersion() {
        return 40;
    }
}
