package ch.threema.app.preference

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ch.threema.android.buildActivityIntent
import ch.threema.android.getSerializable
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.LockAppService
import ch.threema.app.startup.finishAndRestartLaterIfNotReady
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConfigUtils.isTabletLayout
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("SettingsActivity")

class SettingsActivity : ThreemaToolbarActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    init {
        logScreenVisibility(logger)
    }

    private val lockAppService: LockAppService by inject()
    private val synchronizedSettingsService: SynchronizedSettingsService by inject()

    private val settingsSummaryFragment by lazy {
        SettingsSummaryFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishAndRestartLaterIfNotReady()) {
            return
        }

        // hide contents in app switcher and inhibit screenshots
        ConfigUtils.applyScreenshotPolicy(this, synchronizedSettingsService, lockAppService)

        if (isTabletLayout() && ConfigUtils.isTheDarkSide(this)) {
            findViewById<View>(R.id.settings_separator).visibility = View.INVISIBLE
        }

        if (savedInstanceState == null) {
            when (intent.getSerializable<InitialScreen>(EXTRA_INITIAL_SCREEN)) {
                InitialScreen.MEDIA -> showSpecificSettings(SettingsMediaFragment())
                InitialScreen.NOTIFICATIONS -> showSpecificSettings(SettingsNotificationsFragment())
                InitialScreen.SECURITY -> showSpecificSettings(SettingsSecurityFragment())
                else -> showDefaultSettings()
            }
        } else if (isTabletLayout()) {
            // Remove and recreate fragments on tablets because they are not attached to the activity anymore
            supportFragmentManager.fragments.forEach {
                supportFragmentManager.beginTransaction().remove(it).commit()
            }
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
            .replace(R.id.settings, settingsSummaryFragment)
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
     * marking a private chat when no locking mechanism is set.
     *
     * @param fragment the fragment that should be shown (directly)
     */
    private fun showSpecificSettings(fragment: Fragment) {
        // Show first preference screen (privacy) on the right side on tablets per default.
        if (isTabletLayout()) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, settingsSummaryFragment)
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

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val fragmentClassName = pref.fragment
        if (fragmentClassName == null) {
            logger.error("Fragment should not be null")
            return false
        }
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            fragmentClassName,
        )

        val layoutID = if (isTabletLayout()) R.id.settings_detailed else R.id.settings
        val transaction =
            if (isTabletLayout()) {
                supportFragmentManager
                    .beginTransaction()
                    .replace(layoutID, fragment)
            } else {
                supportFragmentManager
                    .beginTransaction()
                    .setCustomAnimations(
                        R.anim.slide_in_right_short,
                        R.anim.slide_out_left_short,
                        R.anim.slide_in_left_short,
                        R.anim.slide_out_right_short,
                    )
                    .replace(layoutID, fragment)
            }
        // On tablets there is no need to add the fragment to the back stack except for nested fragments (i.e. troubleshooting)
        if (!isTabletLayout() || fragment is SettingsAdvancedOptionsFragment) {
            transaction.addToBackStack(null)
        }
        transaction.commit()

        settingsSummaryFragment.onPrefClicked(pref.key)

        return true
    }

    override fun getLayoutResource() =
        if (isTabletLayout()) R.layout.activity_settings_tablet else R.layout.activity_settings

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
            }
        }
        return false
    }

    fun setActionBarTitle(@StringRes title: Int = R.string.menu_settings) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(title)
    }

    enum class InitialScreen {
        MEDIA,
        NOTIFICATIONS,
        SECURITY,
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun createIntent(
            context: Context,
            initialScreen: InitialScreen? = null,
        ) = buildActivityIntent<SettingsActivity>(context) {
            if (initialScreen != null) {
                putExtra(EXTRA_INITIAL_SCREEN, initialScreen)
            }
        }

        const val EXTRA_INITIAL_SCREEN = "extra_initial_screen"
    }
}
