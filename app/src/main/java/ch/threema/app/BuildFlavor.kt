/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app

sealed class BuildFlavor(
    val gradleName: String,
    val licenseType: LicenseType,
    val buildEnvironment: BuildEnvironment,
    private val displayName: String,
) {

    companion object {
        @JvmStatic
        val current: BuildFlavor by lazy {
            when (BuildConfig.FLAVOR) {
                None.gradleName -> None
                StoreGoogle.gradleName -> StoreGoogle
                StoreThreema.gradleName -> StoreThreema
                StoreGoogleWork.gradleName -> StoreGoogleWork
                Green.gradleName -> Green
                SandboxWork.gradleName -> SandboxWork
                OnPrem.gradleName -> OnPrem
                Blue.gradleName -> Blue
                Hms.gradleName -> Hms
                HmsWork.gradleName -> HmsWork
                Libre.gradleName -> Libre
                else -> throw IllegalStateException("Unhandled build flavor " + BuildConfig.FLAVOR)
            }
        }
    }

    enum class LicenseType {
        NONE, GOOGLE, SERIAL, GOOGLE_WORK, HMS, HMS_WORK, ONPREM
    }

    enum class BuildEnvironment {
        LIVE, SANDBOX, ONPREM
    }

    data object None : BuildFlavor(
        gradleName = "none",
        licenseType = LicenseType.NONE,
        buildEnvironment = BuildEnvironment.LIVE,
        displayName = "DEV"
    )

    data object StoreGoogle : BuildFlavor(
        gradleName = "store_google",
        licenseType = LicenseType.GOOGLE,
        buildEnvironment = BuildEnvironment.LIVE,
        displayName = "Google Play"
    )

    data object StoreThreema : BuildFlavor(
        gradleName = "store_threema",
        licenseType = LicenseType.SERIAL,
        buildEnvironment = BuildEnvironment.LIVE,
        displayName = "Threema Shop",
    )

    data object StoreGoogleWork : BuildFlavor(
        gradleName = "store_google_work",
        licenseType = LicenseType.GOOGLE_WORK,
        buildEnvironment = BuildEnvironment.LIVE,
        displayName = "Work",
    )

    data object Green : BuildFlavor(
        gradleName = "green",
        licenseType = LicenseType.NONE,
        buildEnvironment = BuildEnvironment.SANDBOX,
        displayName = "Green",
    )

    data object SandboxWork : BuildFlavor(
        gradleName = "sandbox_work",
        licenseType = LicenseType.GOOGLE_WORK,
        buildEnvironment = BuildEnvironment.SANDBOX,
        displayName = "Sandbox Work",
    )

    data object OnPrem : BuildFlavor(
        gradleName = "onprem",
        licenseType = LicenseType.ONPREM,
        buildEnvironment = BuildEnvironment.ONPREM,
        displayName = "OnPrem",
    )

    data object Blue : BuildFlavor(
        gradleName = "blue",
        licenseType = LicenseType.GOOGLE_WORK,
        buildEnvironment = BuildEnvironment.SANDBOX,
        displayName = "Blue",
    )

    data object Hms : BuildFlavor(
        gradleName = "hms",
        licenseType = LicenseType.HMS,
        buildEnvironment = BuildEnvironment.LIVE,
        displayName = "HMS",
    )

    data object HmsWork : BuildFlavor(
        gradleName = "hms_work",
        licenseType = LicenseType.HMS_WORK,
        buildEnvironment = BuildEnvironment.LIVE,
        displayName = "HMS Work",
    )

    data object Libre : BuildFlavor(
        gradleName = "libre",
        licenseType = LicenseType.SERIAL,
        buildEnvironment = BuildEnvironment.LIVE,
        displayName = "Libre",
    )

    val fullDisplayName: String by lazy {
        displayName + if (BuildConfig.DEBUG) " (DEBUG)" else ""
    }

    /**
     * Return whether the self-updater is supported or not.
     */
    val maySelfUpdate: Boolean
        get() = this is StoreThreema

    /**
     * Return whether this build flavor always uses Threema Push.
     */
    val forceThreemaPush: Boolean
        get() = this is Libre

    /**
     * Return whether this build flavor is "libre", meaning that it contains
     * no proprietary services.
     */
    val isLibre
        get() = this is Libre

    /**
     * Return whether this build flavor uses the sandbox build environment.
     */
    val isSandbox: Boolean
        get() = buildEnvironment == BuildEnvironment.SANDBOX
}
