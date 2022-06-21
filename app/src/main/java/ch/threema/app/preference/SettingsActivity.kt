/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import android.content.res.Resources
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConfigUtils.THEME_DARK
import ch.threema.app.utils.ConfigUtils.isTabletLayout
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("SettingsActivity")

class SettingsActivity : ThreemaToolbarActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    private val settingsFragment = SettingsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // hide contents in app switcher and inhibit screenshots
        ConfigUtils.setScreenshotsAllowed(this, preferenceService, lockAppService)

        if (isTabletLayout() && ConfigUtils.getAppTheme(this) == THEME_DARK) {
            findViewById<View>(R.id.settings_separator).visibility = View.INVISIBLE
        }

        if (savedInstanceState == null) {
            when {
                intent.extras?.get(EXTRA_SHOW_NOTIFICATION_FRAGMENT) == true -> showSpecificSettings(SettingsNotificationsFragment())
                intent.extras?.get(EXTRA_SHOW_MEDIA_FRAGMENT) == true -> showSpecificSettings(SettingsMediaFragment())
                intent.extras?.get(EXTRA_SHOW_SECURITY_FRAGMENT) == true -> showSpecificSettings(SettingsSecurityFragment())
                else -> showDefaultSettings()
            }
        } else if (isTabletLayout()) {
            // Remove and recreate fragments on tablets because they are not attached to the activity anymore
            supportFragmentManager.fragments.forEach { supportFragmentManager.beginTransaction().remove(it).commit() }
            showDefaultSettings()
        }
    }

    /**
     * This is called if the settings should be displayed after the user opened the settings normally.
     * On phones, the headers are shown, whereas on tablet layouts the headers are shown on the left side and the privacy settings are shown on the right side.
     */
    private fun showDefaultSettings() {
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, settingsFragment)
                .commit()

        // Show first preference screen (privacy) on the right side on tablets per default.
        if (isTabletLayout()) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings_detailed, SettingsPrivacyFragment())
                    .commit()
        }
    }

    /**
     * This is called, when the settings must jump to a specific category. This is the case for the security settings when
     * marking a private chat when no locking mechanism is set. From the android data usage settings there is a link directly
     * to the media settings and from the android notification settings we can get directly to the sound and notification
     * settings. This is done with an intent filter via [SettingsMediaDummyActivity] and [SettingsNotificationsDummyActivity].
     *
     * @param fragment the fragment that should be shown (directly)
     */
    private fun showSpecificSettings(fragment: Fragment) {
        // Show first preference screen (privacy) on the right side on tablets per default.
        if (isTabletLayout()) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, settingsFragment)
                    .commit()

            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings_detailed, fragment)
                    .commit()
        } else {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, fragment)
                    .commit()
        }
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragmentClassName = pref.fragment
        if (fragmentClassName == null) {
            logger.error("Fragment should not be null")
            return false
        }
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                fragmentClassName)

        val layoutID = if (isTabletLayout()) R.id.settings_detailed else R.id.settings
        val transaction = supportFragmentManager
                .beginTransaction()
                .replace(layoutID, fragment)
        // On tablets there is no need to add the fragment to the back stack except for nested fragments (i.e. troubleshooting)
        if (!isTabletLayout() || fragment is SettingsTroubleshootingFragment) {
            transaction.addToBackStack(null)
        }
        transaction.commit()

        settingsFragment.onPrefClicked(pref.key)

        return true
    }

    override fun getLayoutResource() = if (isTabletLayout()) R.layout.activity_settings_tablet else R.layout.activity_settings

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
            }
        }
        return false
    }

    override fun onApplyThemeResource(theme: Resources.Theme, resid: Int, first: Boolean) {
        if (ConfigUtils.getAppTheme(this) == THEME_DARK) {
            theme.applyStyle(R.style.Theme_Threema_Settings_Dark, true)
        } else {
            super.onApplyThemeResource(theme, resid, first)
        }
    }

    fun setActionBarTitle(@StringRes title: Int = R.string.menu_settings) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(title)
    }

    companion object {
        const val EXTRA_SHOW_NOTIFICATION_FRAGMENT = "extra_show_notification_fragment"
        const val EXTRA_SHOW_MEDIA_FRAGMENT = "extra_show_media_fragment"
        const val EXTRA_SHOW_SECURITY_FRAGMENT = "extra_show_security_fragment"
    }

}
