/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.*
import ch.threema.app.services.license.LicenseService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ConnectionIndicatorUtil
import ch.threema.app.utils.RuntimeUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ConnectionStateListener
import ch.threema.domain.protocol.connection.ServerConnection
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

private val logger = LoggingUtil.getThreemaLogger("ThreemaPreferenceFragment")

/**
 * This fragment provides some tool bar functionality and manages loading the resources.
 */
@SuppressLint("NewApi")
abstract class ThreemaPreferenceFragment : PreferenceFragmentCompat(), ConnectionStateListener {
    private var colorTransparent = 0
    private var initialized = false

    private var settingsScrollView: NestedScrollView? = null
    private var appBar: AppBarLayout? = null
    var toolbar: MaterialToolbar? = null
    private var toolbarTitle: TextView? = null
    var title: TextView? = null
    private var connectionIndicator: View? = null
    private var serverConnection: ServerConnection? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        initialized = true

        setPreferencesFromResource(getPreferenceResource(), rootKey)

        initializePreferences()
    }

    override fun onResume() {
        serverConnection?.let {
            it.addConnectionStateListener(this)
            ConnectionIndicatorUtil.getInstance()
                .updateConnectionIndicator(connectionIndicator, it.connectionState)
        }

        super.onResume()

        activity.apply {
            if (this is SettingsActivity) {
                setActionBarTitle(if (ConfigUtils.isTabletLayout()) R.string.menu_settings else getPreferenceTitleResource())
            }
        }
    }

    override fun onPause() {
        serverConnection?.removeConnectionStateListener(this)

        super.onPause()
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
        return checkNotNull(ThreemaApplication.getServiceManager()?.preferenceService) {
            "Could not get preference service"
        }
    }

    protected fun requireLicenceService(): LicenseService<*> {
        return checkNotNull(ThreemaApplication.getServiceManager()?.licenseService) {
            "Could not get license service"
        }
    }

    protected fun requireWallpaperService(): WallpaperService {
        return checkNotNull(ThreemaApplication.getServiceManager()?.wallpaperService) {
            "Could not get wallpaper service"
        }
    }

    protected fun requireSynchronizeContactsService(): SynchronizeContactsService {
        return checkNotNull(ThreemaApplication.getServiceManager()?.synchronizeContactsService) {
            "Could not get synchronize contacts service"
        }
    }

    protected fun requireContactService(): ContactService {
        return checkNotNull(ThreemaApplication.getServiceManager()?.contactService) {
            "Could not get contact service"
        }
    }

    protected fun requireConversationCategoryService(): ConversationCategoryService {
        return checkNotNull(ThreemaApplication.getServiceManager()?.conversationCategoryService) {
            "Could not get conversation category service"
        }
    }

    protected fun requireScreenLockService(): SystemScreenLockService {
        return checkNotNull(ThreemaApplication.getServiceManager()?.screenLockService) {
            "Could not get screen lock service"
        }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            serverConnection = ThreemaApplication.getServiceManager()?.getConnection()
        } catch (e: java.lang.Exception) {
            // ignore
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsScrollView = view.findViewById(R.id.settings_contents_view)
        toolbar = view.findViewById(R.id.toolbar)
        toolbarTitle = view.findViewById(R.id.toolbar_title)
        title = view.findViewById(R.id.title_text_view)
        connectionIndicator = view.findViewById(R.id.connection_indicator)
        appBar = view.findViewById(R.id.appbar)
        appBar?.setLiftable(true)

        setTitle(getPreferenceTitleResource())

        colorTransparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)

        (activity as AppCompatActivity?)!!.setSupportActionBar(toolbar)
        val ab: ActionBar? = (activity as AppCompatActivity?)!!.supportActionBar

        if (ab != null) {
            if (!ConfigUtils.isTabletLayout() || this is SettingsSummaryFragment) {
                ab.setDisplayHomeAsUpEnabled(true)
                toolbar?.setNavigationOnClickListener {
                    if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                        requireActivity().supportFragmentManager.popBackStack()
                    } else {
                        requireActivity().finish()
                    }
                }
            } else {
                toolbar?.visibility = View.INVISIBLE
            }
        }

        settingsScrollView?.let {
            if (initialized) {
                it.post {
                    it.scrollTo(0, 0)
                }
            }
            initialized = false
            toolbarTitle?.alpha = 0f
            if (Build.VERSION.SDK_INT >= 24 && !ConfigUtils.isTabletLayout()) {
                it.setOnScrollChangeListener { _, _, _, _, _ ->
                    setToolbarColor()
                }
            }
        }

        listView?.let {
            it.setPadding(
                listView.paddingLeft,
                listView.paddingTop,
                listView.paddingRight,
                listView.paddingBottom +
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        48F,
                        context?.resources?.displayMetrics,
                    ).toInt(),
            )
            it.clipToPadding = false
        }
    }

    private fun setToolbarColor() {
        val titleLocation = IntArray(2)
        title?.let {
            it.getLocationInWindow(titleLocation)
            val toolbarLocation = IntArray(2)
            toolbar?.let { materialToolbar ->
                materialToolbar.getLocationInWindow(toolbarLocation)
                val currentTitleTop = titleLocation[1] + it.paddingTop

                val titleFadeOutStart =
                    toolbarLocation[1] + materialToolbar.height + (it.paddingTop / 2)
                val titleFadeOutEnd =
                    toolbarLocation[1] + materialToolbar.height - it.height + it.paddingTop + it.paddingBottom + (it.paddingTop / 4)

                val toolbarFadeInStart = titleFadeOutEnd
                val toolbarFadeInEnd = toolbarFadeInStart - (materialToolbar.height / 2)

                val titleAlpha =
                    1F - ((titleFadeOutStart - currentTitleTop).toFloat() / (titleFadeOutStart - titleFadeOutEnd).toFloat())
                val toolbarAlpha =
                    (toolbarFadeInStart - currentTitleTop).toFloat() / (toolbarFadeInStart - toolbarFadeInEnd).toFloat()
                it.alpha = titleAlpha
                toolbarTitle?.alpha = toolbarAlpha

                appBar?.isLifted = titleLocation[1] <= materialToolbar.height
            }
        }
    }

    open fun setTitle(title: CharSequence?) {
        this.title?.text = title
        toolbarTitle?.text = title
    }

    open fun setTitle(stringRes: Int) {
        title?.setText(stringRes)
        toolbarTitle?.setText(stringRes)
    }

    /**
     * Hack to style MultiSelectPreferences as Material Dialogs
     */
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is MultiSelectListPreference) {
            val dialogFragment: DialogFragment = MaterialMultiSelectListPreference()
            val bundle = Bundle(1)
            bundle.putString("key", preference.getKey())
            dialogFragment.arguments = bundle
            dialogFragment.setTargetFragment(this, 0)
            dialogFragment.show(
                parentFragmentManager,
                "androidx.preference.PreferenceFragment.DIALOG",
            )
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun updateConnectionState(connectionState: ConnectionState?) {
        RuntimeUtil.runOnUiThread {
            ConnectionIndicatorUtil.getInstance()
                .updateConnectionIndicator(connectionIndicator, connectionState)
        }
    }

    override fun onDestroyView() {
        appBar = null
        settingsScrollView = null

        super.onDestroyView()
    }
}
