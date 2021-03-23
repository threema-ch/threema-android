/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

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
	 * @param appPlatformCode platform code, single letter (A = Android, I = iPhone, J = Generic Java)
	 * @param appLanguage language code, ISO 639-1 (e.g. "de", "en")
	 * @param appCountry country code, ISO 3166-1 (e.g. "CH", "DE", "US")
	 * @param appSystemModel system model string
	 * @param appSystemVersion system version string
	 */
	public AppVersion(String appVersionNumber, String appPlatformCode, String appLanguage, String appCountry, String appSystemModel, String appSystemVersion) {
		this.appVersionNumber = appVersionNumber;
		this.appPlatformCode = appPlatformCode;
		this.appLanguage = appLanguage;
		this.appCountry = appCountry;
		this.appSystemModel = appSystemModel;
		this.appSystemVersion = appSystemVersion;
	}

	/**
	 * Return the short version: Version;PlatformCode
	 */
	@Override
	public String getVersion() {
		return appVersionNumber + appPlatformCode;
	}

	/**
	 * Return the full version: Version;PlatformCode;Language/Country;SystemModel;SystemVersion
	 */
	@Override
	public String getFullVersion() {
		return appVersionNumber.replace(";", "_") + ";" + appPlatformCode.replace(";", "_") + ";" +
				appLanguage.replace(";", "_") + "/" + appCountry.replace(";", "_") +
				";" + appSystemModel.replace(";", "_") + ";" + appSystemVersion.replace(";", "_");
	}
}
