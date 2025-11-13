/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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
