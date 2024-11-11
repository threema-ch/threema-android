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
import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.onprem.UnauthorizedFetchException;
import ch.threema.domain.protocol.api.APIConnector;

abstract public class  LicenseServiceThreema<T extends LicenseService.Credentials>  implements LicenseService<T> {
	private static final Logger logger = LoggingUtil.getThreemaLogger("LicenseServiceThreema");

	protected final APIConnector apiConnector;
	protected final PreferenceService preferenceService;
	private final String deviceId;
	private String updateMessage;
	private String updateUrl;
	private boolean updateMessageShown;     /* not the best place to track this... */
	private boolean isLicensed;

	public LicenseServiceThreema(APIConnector apiConnector, PreferenceService preferenceService, String deviceId) {
		this.apiConnector = apiConnector;
		this.preferenceService = preferenceService;
		this.deviceId = deviceId;
		this.isLicensed = preferenceService.getLicensedStatus();
	}

	@Override
	public boolean hasCredentials() {
		return
				!TestUtil.isEmptyOrNull(this.preferenceService.getSerialNumber())
						|| !TestUtil.isEmptyOrNull(this.preferenceService.getLicenseUsername(), this.preferenceService.getLicensePassword());
	}

	@Override
	@Nullable
	@WorkerThread
	public String validate(T credentials) {
		return this.validate(credentials, false);
	}

	@Override
	@Nullable
	@WorkerThread
	public String validate(boolean allowException) {
		T credentials = this.loadCredentials();
		if(credentials != null) {
			return this.validate(credentials, allowException);
		}
		return "no license";
	}

	/**
	 * Validate the license credentials. If the credentials validate, the licensed state
	 * will be set to `true` and saved. In case of success also an update message and update url
	 * (if available) are retrieved.
	 * If the validation yields an invalid result, the licensed state will be set to `false`.
	 *
	 * @param credentials holder of the credential values
	 * @param allowException If true, general exceptions will be ignored
	 * @return In case of success `null` is returned. If validation failed an error message will be
	 *         returned
	 */
	@Nullable
	@WorkerThread
	private String validate(T credentials, boolean allowException) {
		logger.info("Validating credentials");
		APIConnector.CheckLicenseResult result;
		try {
			result = this.checkLicense(credentials, deviceId);
			if (result.success) {
				logger.info("Validating credentials successful");
				this.updateMessage = result.updateMessage;
				this.updateUrl = result.updateUrl;

				//save in preferences
				this.saveCredentials(credentials);
				this.preferenceService.setLicensedStatus(true);
				this.isLicensed = true;
			} else {
				logger.info("Validating credentials failed: {}", result.error);
				this.preferenceService.setLicensedStatus(false);
				this.isLicensed = false;
				if (result.error == null) {
					return "No success but no error message provided";
				}
				return result.error;
			}
		} catch (UnauthorizedFetchException e) {
			// Treat unauthorized OPPF fetch like (temporarily) bad license
			this.isLicensed = false;
			logger.warn("Could not validate credentials", e);
			return getExceptionMessageOrDefault(
				e,
				"Unauthorized"
			);
		} catch (Exception e) {
			if(!allowException) {
				logger.warn("Could not validate credentials", e);
				return getExceptionMessageOrDefault(
					e,
					"Error during validation"
				);
			} else {
				logger.warn("Could not validate credentials", e);
			}
		}
		return null;
	}

	@NonNull
	private String getExceptionMessageOrDefault(
		@NonNull Throwable t,
		@NonNull String defaultMessage
	) {
		String message = t.getMessage();
		return message == null
			? defaultMessage
			: message;
	}

	public String getUpdateMessage() {
		return updateMessage;
	}

	public String getUpdateUrl() {
		return updateUrl;
	}

	public boolean isUpdateMessageShown() {
		return updateMessageShown;
	}

	public void setUpdateMessageShown(boolean updateMessageShown) {
		this.updateMessageShown = updateMessageShown;
	}

	@Override
	public boolean isLicensed() {
		return this.isLicensed;
	}

	/**
	 * Save the credentials. Note that the credentials will override existing credentials, even if
	 * the new credentials are invalid.
	 *
	 * @param credentials The credentials to save
	 */
	abstract public void saveCredentials(T credentials);

	@WorkerThread
	abstract protected APIConnector.CheckLicenseResult checkLicense(T credentials, String deviceId) throws Exception;
}
