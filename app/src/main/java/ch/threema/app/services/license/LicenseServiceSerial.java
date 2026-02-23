package ch.threema.app.services.license;

import androidx.annotation.Nullable;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.domain.models.SerialCredentials;
import ch.threema.domain.protocol.api.APIConnector;

public class LicenseServiceSerial extends LicenseServiceThreema<SerialCredentials> {

    public LicenseServiceSerial(APIConnector apiConnector, PreferenceService preferenceService, String deviceId) {
        super(apiConnector, preferenceService, deviceId);
    }

    @Override
    public boolean hasCredentials() {
        var serialNumber = preferenceService.getSerialNumber();
        return serialNumber != null && !serialNumber.isEmpty();
    }

    @Override
    protected APIConnector.CheckLicenseResult checkLicense(SerialCredentials credentials, String deviceId) throws Exception {
        return this.apiConnector.checkLicense(credentials.licenseKey, deviceId);
    }

    @Override
    public void saveCredentials(SerialCredentials credentials) {
        this.preferenceService.setSerialNumber(credentials.licenseKey);
    }

    @Override
    @Nullable
    public SerialCredentials loadCredentials() {
        var serialNumber = preferenceService.getSerialNumber();
        if (serialNumber != null && !serialNumber.isEmpty()) {
            return new SerialCredentials(serialNumber);
        }
        return null;
    }
}
