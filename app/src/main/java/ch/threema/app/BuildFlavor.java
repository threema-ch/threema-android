/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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
	private final static String FLAVOR_ONPREM = "onprem";
	private final static String FLAVOR_RED = "red";
	private final static String FLAVOR_HMS = "hms";
	private final static String FLAVOR_HMS_WORK = "hms_work";
	private final static String FLAVOR_FDROID = "fdroid";

	public enum LicenseType {
		NONE, GOOGLE, SERIAL, GOOGLE_WORK, HMS, HMS_WORK, ONPREM
	}

	private static volatile boolean initialized = false;
	private static LicenseType licenseType = null;
	private static String name = null;

	/**
	 * Return the build flavor {@link LicenseType}.
	 */
	public static LicenseType getLicenseType() {
		init();
		return licenseType;
	}

	/**
	 * Return the build flavor name.
	 */
	public static String getName() {
		init();
		return name;
	}

	/**
	 * Return whether the self-updater is supported or not.
	 */
	@SuppressWarnings("ConstantConditions")
	public static boolean maySelfUpdate() {
		switch (BuildConfig.FLAVOR) {
			case FLAVOR_STORE_THREEMA:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Return whether this build flavor always uses Threema Push.
	 */
	@SuppressWarnings("ConstantConditions")
	public static boolean forceThreemaPush() {
		switch (BuildConfig.FLAVOR) {
			case FLAVOR_FDROID:
				return true;
			default:
				return false;
		}
	}

	/**
	 * Return whether this build flavor is "libre", meaning that it contains
	 * no proprietary services.
	 */
	@SuppressWarnings("ConstantConditions")
	public static boolean isLibre() {
		switch (BuildConfig.FLAVOR) {
			case FLAVOR_FDROID:
				return true;
			default:
				return false;
		}
	}

	@SuppressWarnings("ConstantConditions")
	private static synchronized void init() {
		if (!initialized) {
			// License Type
			switch (BuildConfig.FLAVOR) {
				case FLAVOR_NONE:
				case FLAVOR_SANDBOX:
					licenseType = LicenseType.NONE;
					break;
				case FLAVOR_STORE_GOOGLE:
					licenseType = LicenseType.GOOGLE;
					break;
				case FLAVOR_STORE_GOOGLE_WORK:
				case FLAVOR_SANDBOX_WORK:
				case FLAVOR_RED:
					licenseType = LicenseType.GOOGLE_WORK;
					break;
				case FLAVOR_ONPREM:
					licenseType = LicenseType.ONPREM;
					break;
				case FLAVOR_HMS:
					licenseType = LicenseType.HMS;
					break;
				case FLAVOR_HMS_WORK:
					licenseType = LicenseType.HMS_WORK;
					break;
				case FLAVOR_STORE_THREEMA:
				case FLAVOR_FDROID:
					licenseType = LicenseType.SERIAL;
					break;
				default:
					throw new IllegalStateException("Unhandled build flavor " + BuildConfig.FLAVOR);
			}

			// Name
			switch (BuildConfig.FLAVOR) {
				case FLAVOR_STORE_GOOGLE:
					name = "Google Play";
					break;
				case FLAVOR_STORE_THREEMA:
					name = "Threema Shop";
					break;
				case FLAVOR_NONE:
					name = "DEV";
					break;
				case FLAVOR_STORE_GOOGLE_WORK:
					name = "Work";
					break;
				case FLAVOR_SANDBOX:
					name = "Sandbox";
					break;
				case FLAVOR_SANDBOX_WORK:
					name = "Sandbox Work";
					break;
				case FLAVOR_ONPREM:
					name = "OnPrem";
					break;
				case FLAVOR_RED:
					name = "Red";
					break;
				case FLAVOR_HMS:
					name = "HMS";
					break;
				case FLAVOR_HMS_WORK:
					name = "HMS Work";
					break;
				case FLAVOR_FDROID:
					name = "F-Droid";
					break;
				default:
					throw new IllegalStateException("Unhandled build flavor " + BuildConfig.FLAVOR);
			}
			if (BuildConfig.DEBUG) {
				name += "";
			}

			initialized = true;
		}
	}

}
