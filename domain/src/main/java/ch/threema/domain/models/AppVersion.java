/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.domain.models;

import ch.threema.domain.protocol.Version;

public class AppVersion extends Version {
	private final String appVersionNumber;
	private final String appPlatformCode;
	private final String appLanguage;
	private final String appCountry;
	private final String appSystemModel;
	private final String appSystemVersion;

	/**
	 * Create an app version object.
	 *
	 * @param appVersionNumber version number, a short string in the format major.minor (e.g. "1.0")
	 * @param appPlatformCode platform code, single letter (A = Android, I = iPhone, Q = Desktop/Web, J = Generic Java)
	 * @param appLanguage language code, ISO 639-1 (e.g. "de", "en")
	 * @param appCountry country code, ISO 3166-1 (e.g. "CH", "DE", "US")
	 * @param appSystemModel smartphone model string
	 * @param appSystemVersion operating system version string
	 */
	public AppVersion(String appVersionNumber, String appPlatformCode, String appLanguage, String appCountry, String appSystemModel, String appSystemVersion) {
		this.appVersionNumber = appVersionNumber;
		this.appPlatformCode = appPlatformCode;
		this.appLanguage = appLanguage;
		this.appCountry = appCountry;
		this.appSystemModel = appSystemModel;
		this.appSystemVersion = appSystemVersion;
	}

	@Override
	public String getVersionNumber() {
		return appVersionNumber;
	}

	/**
	 * Return the short version: Version;PlatformCode
	 */
	@Override
	public String getVersionString() {
		return appVersionNumber + appPlatformCode;
	}

	/**
	 * Return the full version. Used in the CSP client-info extension payload.
	 *
	 * Format: `<app-version>;<platform>;<lang>/<country-code>;<device-model>;<os-version>`
	 */
	@Override
	public String getFullVersionString() {
		return appVersionNumber.replace(";", "_") + ";"
			+ appPlatformCode.replace(";", "_") + ";"
			+ appLanguage.replace(";", "_") + "/"
			+ appCountry.replace(";", "_") + ";"
			+ appSystemModel.replace(";", "_") + ";"
			+ appSystemVersion.replace(";", "_");
	}
}
