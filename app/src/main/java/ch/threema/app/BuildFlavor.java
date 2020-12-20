/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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

package ch.threema.app;

public class BuildFlavor {
	private final static String FLAVOR_NONE = "none";
	private final static String FLAVOR_STORE_GOOGLE = "store_google";
	private final static String FLAVOR_STORE_THREEMA = "store_threema";
	private final static String FLAVOR_STORE_GOOGLE_WORK = "store_google_work";
	private final static String FLAVOR_SANDBOX = "sandbox";
	private final static String FLAVOR_SANDBOX_WORK = "sandbox_work";

	public enum LicenseType {
		NONE, GOOGLE, SERIAL, GOOGLE_WORK
	}

	private static boolean initialized = false;
	private static LicenseType licenseType = null;
	private static int serverPort = 5222;
	private static int serverPortAlt = 443;
	private static boolean sandbox = false;
	private static String name = null;

	/**
	 * License Type
	 * @return
	 */
	public static LicenseType getLicenseType() {
		init();
		return licenseType;
	}

	/**
	 * Server Port
	 * @return
	 */
	public static int getServerPort() {
		init();
		return serverPort;
	}
	/**
	 * Server Port
	 * @return
	 */
	public static int getServerPortAlt() {
		init();
		return serverPortAlt;
	}

	public static String getName() {
		init();
		return name;
	}

	public static boolean isSandbox() {
		init();
		return sandbox;
	}

	private static void init() {
		if(!initialized) {

			switch (BuildConfig.FLAVOR) {
				case FLAVOR_STORE_GOOGLE:
					licenseType = LicenseType.GOOGLE;
					name = "Google Play";
					break;
				case FLAVOR_STORE_THREEMA:
					licenseType = LicenseType.SERIAL;
					name = "Threema Shop";
					break;
				case FLAVOR_NONE:
					licenseType = LicenseType.NONE;
					name = "DEV";
					break;
				case FLAVOR_STORE_GOOGLE_WORK:
					licenseType = LicenseType.GOOGLE_WORK;
					name = "Work";
					break;
				case FLAVOR_SANDBOX:
					sandbox = true;
					name = "Sandbox";
					licenseType = LicenseType.NONE;
					break;
				case FLAVOR_SANDBOX_WORK:
					sandbox = true;
					name = "Sandbox Work";
					licenseType = LicenseType.GOOGLE_WORK;
					break;
				default:
					throw new RuntimeException("invalid flavor build " + BuildConfig.FLAVOR);
			}

			if(BuildConfig.DEBUG) {
				name += " (DEBUG)";
			}

			initialized = true;
		}
	}

}
