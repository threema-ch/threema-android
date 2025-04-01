/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

public interface LicenseService<T extends LicenseService.Credentials> {
    /**
     * Holder of the credential values
     */
    interface Credentials {
    }

    /**
     * Validate by credentials
     * On success, the credentials will be saved.
     *
     * @param credentials holder of the credential values
     * @return `null` for success or an error message if validation failed
     */
    @Nullable
    @WorkerThread
    String validate(T credentials);

    /**
     * Validate by saved credentials
     *
     * @param allowException If true, general exceptions will be ignored
     * @return `null` for success or an error message if validation failed
     */
    @Nullable
    @WorkerThread
    String validate(boolean allowException);

    /**
     * check if any credentials are saved
     */
    boolean hasCredentials();

    /**
     * check if a validate check was successfully
     */
    boolean isLicensed();

    /**
     * load the credentials
     *
     * @return null or the saved credentials
     */
    T loadCredentials();
}
