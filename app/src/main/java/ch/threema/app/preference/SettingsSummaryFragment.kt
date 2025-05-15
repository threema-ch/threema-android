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

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.activities.WorkExplainActivity
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.utils.ConfigUtils.*
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("SettingsSummaryFragment")

class SettingsSummaryFragment : ThreemaPreferenceFragment() {
    init {
        logScreenVisibility(logger)
    }

    private var preferencePairs: List<Pair<Preference, String>> = emptyList()
    private var selectedPrefView: View? = null
    private val preferenceService = requirePreferenceService()

    override fun initializePreferences() {
        val preferenceScreen = getPrefOrNull<PreferenceScreen>("pref_screen_header") ?: return

        var voipDisabled = false
        if (isWorkRestricted()) {
            val disableCalls =
                AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_calls))
            voipDisabled = disableCalls != null && disableCalls
        }

        if (voipDisabled) {
            preferenceScreen.removePreference(getPref("pref_key_calls"))
        }

        preferencePairs = getPrefPair()
        preferencePairs.forEach { prefPair ->
            val (pref, summary) = prefPair
            pref.summary = summary
        }

        // Remove the rate preference in onprem builds
        val feedbackPref = getPrefOrNull<Preference>(getString(R.string.preferences__rate))
        if (feedbackPref != null && isOnPremBuild()) {
            preferenceScreen.removePreference(feedbackPref)
        }

        val workPref = getPref<Preference>("pref_key_work")
        if (!isWorkBuild()) {
            workPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                startActivity(Intent(requireActivity(), WorkExplainActivity::class.java))
                true
            }
        } else {
            preferenceScreen.removePreference(workPref)
        }

        if (!preferenceService.showDeveloperMenu()) {
            preferenceScreen.removePreference(getPref("pref_key_developers"))
        }

        // Set background of first preference (as it is selected on tablets). Retry it every 100ms (until layout is ready)
        val prefKey = when {
            requireActivity().intent.extras?.get(SettingsActivity.EXTRA_SHOW_NOTIFICATION_FRAGMENT) == true -> "pref_key_notifications"
            requireActivity().intent.extras?.get(SettingsActivity.EXTRA_SHOW_MEDIA_FRAGMENT) == true -> "pref_key_particular_settings"
            requireActivity().intent.extras?.get(SettingsActivity.EXTRA_SHOW_SECURITY_FRAGMENT) == true -> "pref_key_security"
            else -> "pref_key_privacy"
        }
        if (isTabletLayout()) {
            Handler(Looper.getMainLooper()).also {
                it.post(object : Runnable {
                    override fun run() {
                        if (!onPrefClicked(prefKey)) {
                            it.postDelayed(this, 100)
                        }
                    }
                })
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar?.setNavigationOnClickListener {
            requireActivity().finish()
        }
    }

    /**
     * This method sets the background color of the preference header on tablets.
     * The currently selected preference header is marked as selected and the previously selected
     * preference header is reset to the default color.
     *
     * @param prefKey the key of the preference
     * @return true if the preference background could be successfully set (or if a single pane is used), false otherwise
     */
    fun onPrefClicked(prefKey: String): Boolean {
        if (isTabletLayout() && isAdded && context != null) {
            selectedPrefView?.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    android.R.color.transparent,
                ),
            )

            val index = preferencePairs.map { it.first.key }.indexOf(prefKey)
            if (index < 0) {
                return false
            }

            val view =
                activity?.findViewById<LinearLayout>(preferencePairs[index].first.widgetLayoutResource)
            selectedPrefView = (view?.parent as RecyclerView?)?.getChildAt(index)
            selectedPrefView?.setBackgroundColor(
                ContextCompat.getColor(
                    requireContext(),
                    R.color.list_item_background_checked,
                ),
            )

            return selectedPrefView != null
        }

        return true
    }

    override fun getPreferenceTitleResource(): Int = R.string.menu_settings

    override fun getPreferenceResource(): Int = R.xml.preference_headers

    /**
     * Get the preferences and their summaries as pairs. This method should be called after the
     * call preference has been excluded (due to work or device restrictions).
     */
    private fun getPrefPair(): List<Pair<Preference, String>> = listOfNotNull(
        Pair(
            getPref("pref_key_privacy"),
            intArrayOf(
                R.string.prefs_header_contacts,
                R.string.prefs_header_chat,
                R.string.prefs_header_lists,
            ).reduce(),
        ),
        Pair(
            getPref("pref_key_security"),
            intArrayOf(R.string.prefs_title_access_protection, R.string.prefs_masterkey).reduce(),
        ),
        Pair(
            getPref("pref_key_appearance"),
            intArrayOf(
                R.string.prefs_theme,
                R.string.prefs_emoji_style,
                R.string.prefs_language_override,
                R.string.prefs_title_fontsize,
                R.string.prefs_contact_list_title,
            ).reduce(),
        ),
        Pair(
            getPref("pref_key_notifications"),
            intArrayOf(R.string.prefs_voice_call_sound, R.string.prefs_vibrate).reduce(),
        ),
        Pair(
            getPref("pref_key_chatdisplay"),
            intArrayOf(R.string.prefs_header_keyboard, R.string.media).reduce(),
        ),
        Pair(
            getPref("pref_key_particular_settings"),
            intArrayOf(
                R.string.prefs_image_size,
                R.string.prefs_auto_download_title,
                R.string.prefs_storage_mgmt_title,
            ).reduce(),
        ),
        getPrefOrNull<Preference>("pref_key_calls")?.let {
            Pair(
                it,
                intArrayOf(
                    R.string.prefs_title_voip,
                    R.string.video_calls,
                    R.string.group_calls,
                ).reduce(),
            )
        },
        Pair(getPref("pref_key_rate"), ""),
        Pair(getPref("pref_key_about"), ""),
        Pair(getPref("pref_key_developers"), ""),
    )

    private fun IntArray.reduce(): String = joinToString(", ", transform = ::getString)
}
