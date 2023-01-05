/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.TestUtil;
import ch.threema.domain.onprem.UnauthorizedFetchException;
import ch.threema.domain.protocol.api.APIConnector;

abstract public class  LicenseServiceThreema<T extends LicenseService.Credentials>  implements LicenseService<T> {
	protected final APIConnector apiConnector;
	protected final PreferenceService preferenceService;
	private String deviceId;
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
				!TestUtil.empty(this.preferenceService.getSerialNumber())
						|| !TestUtil.empty(this.preferenceService.getLicenseUsername(), this.preferenceService.getLicensePassword());
	}

	@Override
	public String validate(T credentials) {
		return this.validate(credentials, false);
	}

	/**
	 * Validate the license credentials and check for updates.
	 */
	@Override
	public String validate(T credentials, boolean allowException) {
		APIConnector.CheckLicenseResult result;
		try {
			result = this.checkLicense(credentials, deviceId);
			if(result.success) {
				this.updateMessage = result.updateMessage;
				this.updateUrl = result.updateUrl;

				//save in preferences
				this.saveCredentials(credentials);
				this.preferenceService.setLicensedStatus(true);
				this.isLicensed = true;
			}
			else {
				this.preferenceService.setLicensedStatus(false);
				this.isLicensed = false;
				return result.error;
			}
		} catch (UnauthorizedFetchException e) {
			// Treat unauthorized OPPF fetch like (temporarily) bad license
			this.isLicensed = false;
			return e.getMessage();
		} catch (Exception e) {
			if(!allowException) {
				return e.getMessage();
			}
		}

		return null;
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

	@Override
	public String validate(boolean allowException) {
		T credentials = this.loadCredentials();
		if(credentials != null) {
			return this.validate(credentials, allowException);
		}
		return "no license";
	}

	abstract protected APIConnector.CheckLicenseResult checkLicense(T credentials, String deviceId) throws Exception;
	abstract protected void saveCredentials(T credentials);
}
