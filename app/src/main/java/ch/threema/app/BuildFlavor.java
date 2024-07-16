/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2024 Threema GmbH
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
	private final static String FLAVOR_GREEN = "green";
	private final static String FLAVOR_SANDBOX_WORK = "sandbox_work";
	private final static String FLAVOR_ONPREM = "onprem";
	private final static String FLAVOR_BLUE = "blue";
	private final static String FLAVOR_HMS = "hms";
	private final static String FLAVOR_HMS_WORK = "hms_work";
	private final static String FLAVOR_LIBRE = "libre";

	public enum LicenseType {
		NONE, GOOGLE, SERIAL, GOOGLE_WORK, HMS, HMS_WORK, ONPREM
	}

	public enum BuildEnvironment {
		LIVE, SANDBOX, ONPREM
	}

	private static volatile boolean initialized = false;
	private static LicenseType licenseType = null;
	private static BuildEnvironment buildEnvironment = null;
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
		return FLAVOR_STORE_THREEMA.equals(BuildConfig.FLAVOR);
	}

	/**
	 * Return whether this build flavor always uses Threema Push.
	 */
	@SuppressWarnings("ConstantConditions")
	public static boolean forceThreemaPush() {
		return FLAVOR_LIBRE.equals(BuildConfig.FLAVOR);
	}

	/**
	 * Return whether this build flavor is "libre", meaning that it contains
	 * no proprietary services.
	 */
	@SuppressWarnings("ConstantConditions")
	public static boolean isLibre() {
		return FLAVOR_LIBRE.equals(BuildConfig.FLAVOR);
	}

	/**
	 * Return whether this build flavor uses the sandbox build environment.
	 */
	public static boolean isSandbox() {
		init();
		return buildEnvironment == BuildEnvironment.SANDBOX;
	}

	@SuppressWarnings("ConstantConditions")
	private static synchronized void init() {
		if (!initialized) {
			// License Type
			switch (BuildConfig.FLAVOR) {
				case FLAVOR_NONE:
				case FLAVOR_GREEN:
					licenseType = LicenseType.NONE;
					break;
				case FLAVOR_STORE_GOOGLE:
					licenseType = LicenseType.GOOGLE;
					break;
				case FLAVOR_STORE_GOOGLE_WORK:
				case FLAVOR_SANDBOX_WORK:
				case FLAVOR_BLUE:
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
				case FLAVOR_LIBRE:
					licenseType = LicenseType.SERIAL;
					break;
				default:
					throw new IllegalStateException("Unhandled build flavor " + BuildConfig.FLAVOR);
			}

			// Build Environment
			switch (BuildConfig.FLAVOR) {
				case FLAVOR_GREEN:
				case FLAVOR_BLUE:
				case FLAVOR_SANDBOX_WORK:
					buildEnvironment = BuildEnvironment.SANDBOX;
					break;
				case FLAVOR_ONPREM:
					buildEnvironment = BuildEnvironment.ONPREM;
					break;
				case FLAVOR_NONE:
				case FLAVOR_STORE_GOOGLE:
				case FLAVOR_STORE_GOOGLE_WORK:
				case FLAVOR_HMS:
				case FLAVOR_HMS_WORK:
				case FLAVOR_STORE_THREEMA:
				case FLAVOR_LIBRE:
					buildEnvironment = BuildEnvironment.LIVE;
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
				case FLAVOR_GREEN:
					name = "Green";
					break;
				case FLAVOR_SANDBOX_WORK:
					name = "Sandbox Work";
					break;
				case FLAVOR_ONPREM:
					name = "OnPrem";
					break;
				case FLAVOR_BLUE:
					name = "Blue";
					break;
				case FLAVOR_HMS:
					name = "HMS";
					break;
				case FLAVOR_HMS_WORK:
					name = "HMS Work";
					break;
				case FLAVOR_LIBRE:
					name = "Libre";
					break;
				default:
					throw new IllegalStateException("Unhandled build flavor " + BuildConfig.FLAVOR);
			}
			if (BuildConfig.DEBUG) {
				name += " (DEBUG)";
			}

			initialized = true;
		}
	}

}
