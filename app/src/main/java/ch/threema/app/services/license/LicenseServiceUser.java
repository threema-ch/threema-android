package ch.threema.app.services.license;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.domain.models.UserCredentials;
import ch.threema.domain.protocol.api.APIConnector;

public class LicenseServiceUser extends LicenseServiceThreema<UserCredentials> {

    public LicenseServiceUser(APIConnector apiConnector, PreferenceService preferenceService, String deviceId) {
        super(apiConnector, preferenceService, deviceId);
    }

    @Override
    public boolean hasCredentials() {
        var username = preferenceService.getLicenseUsername();
        var password = preferenceService.getLicensePassword();
        return username != null && password != null;
    }

    @Override
    @WorkerThread
    protected APIConnector.CheckLicenseResult checkLicense(UserCredentials credentials, String deviceId) throws Exception {
        return this.apiConnector.checkLicense(credentials.username, credentials.password, deviceId);
    }

    @Override
    public void saveCredentials(UserCredentials credentials) {
        this.preferenceService.setLicenseUsername(credentials.username);
        this.preferenceService.setLicensePassword(credentials.password);
    }

    @Override
    @Nullable
    public UserCredentials loadCredentials() {
        String username = preferenceService.getLicenseUsername();
        String password = preferenceService.getLicensePassword();
        if (username != null && password != null) {
            return new UserCredentials(username, password);
        }
        return null;
    }

}
