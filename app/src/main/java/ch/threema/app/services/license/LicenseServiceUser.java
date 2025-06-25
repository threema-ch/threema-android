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
import androidx.annotation.WorkerThread;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.TestUtil;
import ch.threema.domain.protocol.api.APIConnector;

public class LicenseServiceUser extends LicenseServiceThreema<UserCredentials> {

    public LicenseServiceUser(APIConnector apiConnector, PreferenceService preferenceService, String deviceId) {
        super(apiConnector, preferenceService, deviceId);
    }

    @Override
    public boolean hasCredentials() {
        return !TestUtil.isEmptyOrNull(this.preferenceService.getLicenseUsername(),
            this.preferenceService.getLicensePassword());
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
        String username = this.preferenceService.getLicenseUsername();
        String password = this.preferenceService.getLicensePassword();

        if (!TestUtil.isEmptyOrNull(username, password)) {
            return new UserCredentials(username, password);
        }
        return null;
    }

}
