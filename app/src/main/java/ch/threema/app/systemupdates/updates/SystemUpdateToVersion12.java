package ch.threema.app.systemupdates.updates;

import androidx.annotation.NonNull;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.SynchronizeContactsUtil;

public class SystemUpdateToVersion12 implements SystemUpdate {
    private final @NonNull ServiceManager serviceManager;

    public SystemUpdateToVersion12(@NonNull ServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    public void run() {
        try {
            SynchronizeContactsUtil.startDirectly();
        } catch (SecurityException exception) {
            // Nothing to do
        }

        PreferenceService preferenceService = serviceManager.getPreferenceService();
        if (preferenceService.isSyncContacts()) {
            UserService userService = serviceManager.getUserService();
            userService.enableAccountAutoSync(true);
        }
    }

    @Override
    public int getVersion() {
        return 12;
    }
}
