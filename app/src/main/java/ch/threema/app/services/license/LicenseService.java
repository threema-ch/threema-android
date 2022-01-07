/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2022 Threema GmbH
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

public interface LicenseService<T extends LicenseService.Credentials> {
	/**
	 * Holder of the credential values
	 */
	interface Credentials{}

	/**
	 * validate by credentials (do not throw any exception)
	 * save on success
	 * @param credentials holder of the credential values
	 * @return null or a error message
	 */
	String validate(T credentials);

	/**
	 * validate by credentials
	 * save on success
	 * @param credentials holder of the credential values
	 * @param allowException
	 * @return null or a error message
	 */
	String validate(T credentials, boolean allowException);

	/**
	 * validate by saved credentials
	 * @param allowException
	 * @return null or a error message
	 */
	String validate(boolean allowException);

	/**
	 * check if any credentials are saved
	 * @return
	 */
	boolean hasCredentials();

	/**
	 * check if a validate check was successfully
	 * @return
	 */
	boolean isLicensed();

	/**
	 * load the credentials
	 *
	 * @return null or the saved credentials
	 */
	T loadCredentials();
}
