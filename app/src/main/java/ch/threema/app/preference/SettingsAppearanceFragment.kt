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

import androidx.preference.CheckBoxPreference
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.PreferenceService
import ch.threema.app.services.WallpaperService
import ch.threema.app.utils.AppRestrictionUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.StateBitmapUtil

@Suppress("unused")
class SettingsAppearanceFragment : ThreemaPreferenceFragment() {

    private var oldTheme: Int = 0

    private val wallpaperService: WallpaperService = requireWallpaperService()

    private var showBadge: CheckBoxPreference? = null
    private var showBadgeChecked = false

    private val onWallpaperResultLauncher = wallpaperService.getWallpaperActivityResultLauncher(this, null, null)

    override fun initializePreferences() {
        super.initializePreferences()

        this.showBadge = getPrefOrNull(R.string.preferences__show_unread_badge)
        this.showBadgeChecked = this.showBadge?.isChecked ?: false

        initDefaultColoredAvatarPref()

        initShowProfilePicPref()

        initBiggerSingleEmojisPref()

        initThemePref()

        initEmojiStylePref()

        initLanguageArrayPref()

        initWallpaperPref()

        initSortingPref()

        initFormatPref()

        initShowInactiveContactsPref()
    }

    override fun onDetach() {
        super.onDetach()
        if (this.showBadge?.isChecked != this.showBadgeChecked) {
            ConfigUtils.recreateActivity(activity)
        }
    }

    override fun getPreferenceResource(): Int = R.xml.preference_appearance

    private fun initDefaultColoredAvatarPref() {
        getPrefOrNull<CheckBoxPreference>(R.string.preferences__default_contact_picture_colored)?.apply {
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                val newCheckedValue = newValue == true
                if ((preference as CheckBoxPreference).isChecked != newCheckedValue) {
                    ListenerManager.contactSettingsListeners.handle { listener -> listener.onAvatarSettingChanged() }
                }
                true
            }
        }
    }

    private fun initShowProfilePicPref() {
        getPrefOrNull<CheckBoxPreference>(R.string.preferences__receive_profilepics)?.apply {
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                val newCheckedValue = newValue == true
                if ((preference as CheckBoxPreference).isChecked != newCheckedValue) {
                    ListenerManager.contactSettingsListeners.handle { listener -> listener.onAvatarSettingChanged() }
                }
                true
            }
        }
    }

    private fun initBiggerSingleEmojisPref() {
        getPrefOrNull<CheckBoxPreference>(R.string.preferences__bigger_single_emojis)?.apply {
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                ConfigUtils.setBiggerSingleEmojis(newValue == true)
                true
            }
        }
    }

    private fun initThemePref() {
        val themePreference = getPref<DropDownPreference>(R.string.preferences__theme)
        var themeIndex: Int = preferenceManager.sharedPreferences?.getString(resources.getString(R.string.preferences__theme), "0")?.toInt()
                ?: 0
        val themeArray = resources.getStringArray(R.array.list_theme)

        if (themeIndex >= themeArray.size) {
            themeIndex = 0
        }

        oldTheme = themeIndex

        themePreference.summary = themeArray[themeIndex]
        themePreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val newTheme = newValue.toString().toInt()
            if (newTheme != oldTheme) {
                ConfigUtils.setAppTheme(newTheme)
                StateBitmapUtil.init(ThreemaApplication.getAppContext())
                preference.summary = themeArray[newTheme]
                ListenerManager.contactSettingsListeners.handle { listener -> listener.onAvatarSettingChanged() }
                ConfigUtils.recreateActivity(activity)
            }
            true
        }
    }

    private fun initEmojiStylePref() {
        val emojiPreference = getPref<DropDownPreference>(R.string.preferences__emoji_style)
        if (BuildFlavor.isLibre()) {
            val aboutCategory = getPref<PreferenceCategory>("pref_key_appearance_cat")
            aboutCategory.removePreference(emojiPreference)
        } else {
            var emojiIndex: Int = preferenceManager.sharedPreferences?.getString(resources.getString(R.string.preferences__emoji_style), "0")?.toInt() ?: 0
            val emojiArray = resources.getStringArray(R.array.list_emoji_style)
            if (emojiIndex >= emojiArray.size) {
                emojiIndex = 0
            }
            val oldEmojiStyle = emojiIndex
            emojiPreference.summary = emojiArray[emojiIndex]
            emojiPreference.setOnPreferenceChangeListener { _, newValue ->
                val newEmojiStyle = newValue.toString().toInt()
                if (newEmojiStyle != oldEmojiStyle) {
                    if (newEmojiStyle == PreferenceService.EmojiStyle_ANDROID) {
                        val dialog = GenericAlertDialog.newInstance(R.string.prefs_android_emojis,
                                R.string.android_emojis_warning,
                                R.string.ok,
                                R.string.cancel)
                        dialog.setData(newEmojiStyle)
                        dialog.setCallback(object : GenericAlertDialog.DialogClickListener {
                            override fun onYes(tag: String?, data: Any?) {
                                ConfigUtils.setEmojiStyle(activity, data as Int)
                                updateEmojiPrefs(data)
                                ConfigUtils.recreateActivity(activity)
                            }
                            override fun onNo(tag: String?, data: Any?) {
                                updateEmojiPrefs(PreferenceService.EmojiStyle_DEFAULT)
                            }
                        })
                        dialog.show(parentFragmentManager, "android_emojis")
                    } else {
                        ConfigUtils.setEmojiStyle(activity, newEmojiStyle)
                        updateEmojiPrefs(newEmojiStyle)
                        ConfigUtils.recreateActivity(activity)
                    }
                }
                true
            }
        }
    }

    private fun initLanguageArrayPref() {
        val languageArray = resources.getStringArray(R.array.list_language_override)
        val languagePreference = getPref<DropDownPreference>(R.string.preferences__language_override)
        val oldLocale = languagePreference.value
        try {
            languagePreference.summary = languageArray[languagePreference.findIndexOfValue(oldLocale)]
        } catch (e: Exception) {
            //
        }
        languagePreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val newLocale = newValue?.toString()
            if (newLocale != null && newLocale != oldLocale) {
                ConfigUtils.updateLocaleOverride(newValue)
                ConfigUtils.updateAppContextLocale(ThreemaApplication.getAppContext(), newLocale)
                ConfigUtils.recreateActivity(activity)
            }
            true
        }
    }

    private fun initWallpaperPref() {
        val wallpaperPreference: Preference = getPref(R.string.preferences__wallpaper)
        wallpaperPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            wallpaperService.selectWallpaper(this@SettingsAppearanceFragment, onWallpaperResultLauncher, null, null)
            true
        }
    }

    private fun initSortingPref() {
        getPrefOrNull<DropDownPreference>(R.string.preferences__contact_sorting)?.apply {
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ -> //trigger sort change
                ListenerManager.contactSettingsListeners.handle { listener -> listener.onSortingChanged() }
                true
            }
        }
    }

    private fun initFormatPref() {
        val formatPreference = getPref<DropDownPreference>(resources.getString(R.string.preferences__contact_format))
        formatPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ -> //trigger format name change
            ListenerManager.contactSettingsListeners.handle { listener -> listener.onNameFormatChanged() }
            true
        }
    }

    private fun initShowInactiveContactsPref() {
        getPrefOrNull<CheckBoxPreference>(R.string.preferences__show_inactive_contacts)?.apply {
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
                val newCheckedValue = newValue == true
                if ((preference as CheckBoxPreference).isChecked != newCheckedValue) {
                    ListenerManager.contactSettingsListeners.handle { listener -> listener.onInactiveContactsSettingChanged() }
                }
                true
            }
            if (ConfigUtils.isWorkRestricted()) {
                val value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__hide_inactive_ids))
                if (value != null) {
                    isEnabled = false
                    isSelectable = false
                }
            }
        }
    }

    private fun updateEmojiPrefs(newEmojiStyle: Int) {
        val preference = getPref<DropDownPreference>(R.string.preferences__emoji_style)
        preference.setValueIndex(newEmojiStyle)
        preference.summary = resources.getStringArray(R.array.list_emoji_style)[newEmojiStyle]
    }

}
