/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.preference

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.*
import ch.threema.app.services.license.LicenseService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("ThreemaPreferenceFragment")

/**
 * This fragment provides some tool bar functionality and manages loading the resources.
 */
abstract class ThreemaPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(getPreferenceResource(), rootKey)

        initializePreferences()
    }

    override fun onResume() {
        super.onResume()

        activity.also {
            if (it is SettingsActivity) {
                it.setActionBarTitle(if (ConfigUtils.isTabletLayout()) R.string.menu_settings else getPreferenceTitleResource())
            }
        }
    }

    /**
     * This method must be overridden to provide the action bar title of the preference category.
     */
    @StringRes
    protected abstract fun getPreferenceTitleResource(): Int

    /**
     * This method must be overridden to provide the xml definition of the preferences.
     */
    @XmlRes
    abstract fun getPreferenceResource(): Int

    /**
     * This method is called in [onCreatePreferences] and can be used by subclasses to initialize the preferences.
     */
    protected open fun initializePreferences() {
        // No need to do something here. Just a placeholder method that can be overridden by subclasses.
    }

    /**
     * Get the preference with the given key. Returns null if there is no such preference.
     */
    protected fun <T : Preference> getPrefOrNull(@StringRes stringRes: Int): T? =
        getPrefOrNull(getString(stringRes))

    /**
     * Get the preference with the given key. Returns null if there is no such preference.
     */
    protected fun <T : Preference> getPrefOrNull(key: String): T? {
        return try {
            getPref(key)
        } catch (e: Exception) {
            logger.warn("Preference '$key' not found")
            null
        }
    }

    /**
     * Get the preference with the given key. Throws an [IllegalArgumentException] if there is no such preference.
     */
    protected fun <T : Preference> getPref(@StringRes stringRes: Int) =
        getPref<T>(getString(stringRes))

    /**
     * Get the preference with the given key. Throws an [IllegalArgumentException] if there is no such preference.
     */
    protected fun <T : Preference> getPref(string: String): T =
        findPreference(string) ?: preferenceNotFound(string)

    protected fun requirePreferenceService(): PreferenceService {
        ThreemaApplication.getServiceManager()?.preferenceService?.let {
            return it
        }
        logger.error("Could not get preference service")
        throw IllegalStateException("Could not get preference service")
    }

    protected fun requireLicenceService(): LicenseService<*> {
        ThreemaApplication.getServiceManager()?.licenseService?.let {
            return it
        }
        logger.error("Could not get license service")
        throw IllegalStateException("Could not get license service")
    }

    protected fun requireWallpaperService(): WallpaperService {
        ThreemaApplication.getServiceManager()?.wallpaperService?.let {
            return it
        }
        logger.error("Could not get wallpaper service")
        throw IllegalStateException("Could not get wallpaper service")
    }

    protected fun requireSynchronizeContactsService(): SynchronizeContactsService {
        ThreemaApplication.getServiceManager()?.synchronizeContactsService?.let {
            return it
        }
        logger.error("Could not get synchronize contacts service")
        throw IllegalStateException("Could not get synchronize contacts service")
    }

    protected fun requireContactService(): ContactService {
        ThreemaApplication.getServiceManager()?.contactService?.let {
            return it
        }
        logger.error("Could not get contact service")
        throw IllegalStateException("Could not get contact service")
    }

    protected fun requireHiddenChatListService(): DeadlineListService {
        ThreemaApplication.getServiceManager()?.hiddenChatsListService?.let {
            return it
        }
        logger.error("Could not get hidden chat list service")
        throw IllegalStateException("Could not get hidden chat list service")
    }

    protected fun requireScreenLockService(): SystemScreenLockService {
        ThreemaApplication.getServiceManager()?.screenLockService?.let {
            return it
        }
        throw IllegalStateException("Could not get screen lock service")
    }

    /**
     * Execute the argument and return its return value and null if an exception is thrown.
     */
    protected fun <T> getOrNull(value: () -> T): T? {
        return try {
            value()
        } catch (e: Exception) {
            null
        }
    }

    private fun preferenceNotFound(pref: String): Nothing {
        throw IllegalArgumentException("No preference '$pref' found")
    }

}
