package ch.threema.app.systemupdates.updates;

import org.koin.java.KoinJavaComponent;

import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.models.WebClientSessionModel;
import kotlin.Lazy;

public class SystemUpdateToVersion40 implements SystemUpdate {

    private final Lazy<PreferenceService> preferenceServiceLazy = KoinJavaComponent.inject(PreferenceService.class);
    private final Lazy<DatabaseProvider> databaseProviderLazy = KoinJavaComponent.inject(DatabaseProvider.class);

    @Override
    public void run() {
        PreferenceService preferenceService = preferenceServiceLazy.getValue();
        String currentPushToken = preferenceService.getPushToken();

        if (!TestUtil.isEmptyOrNull(currentPushToken)) {
            // update all
            databaseProviderLazy.getValue().getWritableDatabase()
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
