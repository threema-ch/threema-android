/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.CheckBoxPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.TwoStatePreference
import ch.threema.app.R
import ch.threema.app.dialogs.RingtoneSelectorDialog
import ch.threema.app.dialogs.RingtoneSelectorDialog.RingtoneSelectorDialogClickListener
import ch.threema.app.notifications.NotificationChannels
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.RingtoneUtil
import ch.threema.app.utils.contracts.PickRingtoneContract
import ch.threema.app.utils.contracts.launch
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.DateFormatSymbols
import java.util.Locale

private val logger = LoggingUtil.getThreemaLogger("SettingsNotificationsFragment")

class SettingsNotificationsFragment : ThreemaPreferenceFragment(), RingtoneSelectorDialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var singleChatNotificationSoundPreference: Preference
    private lateinit var groupChatNotificationPreference: Preference
    private lateinit var voipRingtonePreference: Preference
    private lateinit var groupCallsRingtonePreference: Preference

    private lateinit var weekdayShortNames: List<String>
    private lateinit var workHoursStartTimePreference: Preference
    private lateinit var workHoursEndTimePreference: Preference

    private val voipRingtonePickerLauncher = registerForActivityResult<PickRingtoneContract.Input, Uri?>(PickRingtoneContract) { uri ->
        if (uri != null) {
            onRingtoneSelected(DIALOG_TAG_VOIP_NOTIFICATION, uri)
        }
    }
    private val contactTonePickerLauncher = registerForActivityResult<PickRingtoneContract.Input, Uri?>(PickRingtoneContract) { uri ->
        if (uri != null) {
            onRingtoneSelected(DIALOG_TAG_CONTACT_NOTIFICATION, uri)
        }
    }
    private val groupTonePickerLauncher = registerForActivityResult<PickRingtoneContract.Input, Uri?>(PickRingtoneContract) { uri ->
        if (uri != null) {
            onRingtoneSelected(DIALOG_TAG_GROUP_NOTIFICATION, uri)
        }
    }
    private val groupCallsTonePickerLauncher = registerForActivityResult<PickRingtoneContract.Input, Uri?>(PickRingtoneContract) { uri ->
        if (uri != null) {
            onRingtoneSelected(DIALOG_TAG_GROUP_CALLS_NOTIFICATION, uri)
        }
    }

    public override fun initializePreferences() {
        sharedPreferences = preferenceManager.sharedPreferences!!

        initWorkingTimePrefs()
        setVisibilityOfVersionDependentPreferences()

        voipRingtonePreference = getPref(R.string.preferences__voip_ringtone)
        voipRingtonePreference.updateRingtoneSummary(R.string.preferences__voip_ringtone)
        groupCallsRingtonePreference = getPref(R.string.preferences__group_calls_ringtone)
        groupCallsRingtonePreference.updateRingtoneSummary(R.string.preferences__group_calls_ringtone)

        if (ConfigUtils.supportsNotificationChannels()) {
            getPref<Preference>(R.string.preferences__chat_notification_settings).onClick {
                launchSystemNotificationSettings(NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT)
            }
            getPref<Preference>(R.string.preferences__group_notification_settings).onClick {
                launchSystemNotificationSettings(NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT)
            }
            getPref<Preference>(R.string.preferences__voip_notification_settings).onClick {
                launchSystemNotificationSettings(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_CALLS)
            }
            getPref<Preference>(R.string.preferences__group_call_notification_settings).onClick {
                launchSystemNotificationSettings(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS)
            }
        } else {
            singleChatNotificationSoundPreference = getPref(R.string.preferences__notification_sound)
            singleChatNotificationSoundPreference.updateRingtoneSummary(R.string.preferences__notification_sound)
            singleChatNotificationSoundPreference.onClick {
                chooseRingtone(
                    type = RingtoneManager.TYPE_NOTIFICATION,
                    currentUri = getRingtoneFromRingtonePref(R.string.preferences__notification_sound),
                    defaultUri = null,
                    title = getString(R.string.prefs_notification_sound),
                    tag = DIALOG_TAG_CONTACT_NOTIFICATION,
                )
            }

            groupChatNotificationPreference = getPref(R.string.preferences__group_notification_sound)
            groupChatNotificationPreference.updateRingtoneSummary(R.string.preferences__group_notification_sound)
            groupChatNotificationPreference.onClick {
                chooseRingtone(
                    type = RingtoneManager.TYPE_NOTIFICATION,
                    currentUri = getRingtoneFromRingtonePref(R.string.preferences__group_notification_sound),
                    defaultUri = null,
                    title = getString(R.string.prefs_notification_sound),
                    tag = DIALOG_TAG_GROUP_NOTIFICATION,
                )
            }

            voipRingtonePreference.onClick {
                chooseRingtone(
                    type = RingtoneManager.TYPE_RINGTONE,
                    currentUri = getRingtoneFromRingtonePref(R.string.preferences__voip_ringtone),
                    defaultUri = RingtoneUtil.THREEMA_CALL_RINGTONE_URI,
                    title = getString(R.string.prefs_voice_call_sound),
                    tag = DIALOG_TAG_VOIP_NOTIFICATION,
                )
            }

            getPref<TwoStatePreference>(getString(R.string.preferences__use_system_ringtone)).onChange<Boolean> { useSystemRingtone ->
                onRingtoneSelected(
                    tag = DIALOG_TAG_VOIP_NOTIFICATION,
                    ringtone = if (useSystemRingtone) Settings.System.DEFAULT_RINGTONE_URI else RingtoneUtil.THREEMA_CALL_RINGTONE_URI,
                )
            }

            groupCallsRingtonePreference.onClick {
                chooseRingtone(
                    type = RingtoneManager.TYPE_RINGTONE,
                    currentUri = getRingtoneFromRingtonePref(R.string.preferences__group_calls_ringtone),
                    defaultUri = RingtoneUtil.THREEMA_CALL_RINGTONE_URI,
                    title = getString(R.string.prefs_voice_call_sound),
                    tag = DIALOG_TAG_GROUP_CALLS_NOTIFICATION,
                )
            }

            getPref<TwoStatePreference>(getString(R.string.preferences__group_calls_use_system_ringtone)).onChange<Boolean> { useSystemRingtone ->
                onRingtoneSelected(
                    tag = DIALOG_TAG_GROUP_CALLS_NOTIFICATION,
                    ringtone = if (useSystemRingtone) Settings.System.DEFAULT_RINGTONE_URI else RingtoneUtil.THREEMA_CALL_RINGTONE_URI,
                )
            }
        }

        if (ConfigUtils.isWorkRestricted() && AppRestrictionUtil.hasBooleanRestriction(getString(R.string.restriction__disable_message_preview))) {
            with(getPref<CheckBoxPreference>(getString(R.string.preferences__notification_preview))) {
                isEnabled = false
                isSelectable = false
            }
        }
    }

    private fun setVisibilityOfVersionDependentPreferences() {
        if (ConfigUtils.supportsNotificationChannels()) {
            getPref<Preference>(R.string.preferences__single_chat_notifications_legacy).isVisible = false
            getPref<Preference>(R.string.preferences__group_chat_notifications_legacy).isVisible = false
            getPref<Preference>(R.string.preferences__voip_call_notifications_legacy).isVisible = false
            getPref<Preference>(R.string.preferences__group_call_notifications_legacy).isVisible = false
            getPref<Preference>(R.string.preferences__notification_priority).isVisible = false
        } else {
            getPref<PreferenceCategory>(R.string.preferences__notification_channels).isVisible = false
        }
    }

    private fun initWorkingTimePrefs() {
        if (!ConfigUtils.isWorkBuild()) {
            getPref<PreferenceCategory>(R.string.preferences__work_life_balance).isVisible = false
            return
        }

        val dateFormatSymbols = getDateFormatSymbols()
        weekdayShortNames = dateFormatSymbols.shortWeekdays.drop(1)

        getPref<MultiSelectListPreference>(getString(R.string.preferences__working_days))
            .let { workDaysPreference ->
                workDaysPreference.entries = dateFormatSymbols.weekdays.drop(1).toTypedArray()
                workDaysPreference.onChange<Set<String>> { selectedWeekDays ->
                    workDaysPreference.updateWorkingDaysSummary(selectedWeekDays)
                }
                workDaysPreference.updateWorkingDaysSummary(workDaysPreference.values)
            }

        workHoursStartTimePreference = getPref(getString(R.string.preferences__work_time_start))
        workHoursStartTimePreference.updateTimeSummary(defaultSummary = R.string.prefs_work_time_start_sum)
        workHoursStartTimePreference.onClick(::onWorkHoursStartTimeClicked)

        workHoursEndTimePreference = getPref(getString(R.string.preferences__work_time_end))
        workHoursEndTimePreference.updateTimeSummary(defaultSummary = R.string.prefs_work_time_end_sum)
        workHoursEndTimePreference.onClick(::onWorkHoursEndTimeClicked)
    }

    private fun getDateFormatSymbols() = DateFormatSymbols(resources.configuration.locales[0])

    private fun buildTimePicker(initialTime: IntArray?, builder: MaterialTimePicker.Builder.() -> Unit): MaterialTimePicker =
        MaterialTimePicker.Builder()
            .setHour(initialTime?.get(0) ?: 0)
            .setMinute(initialTime?.get(1) ?: 0)
            .setTimeFormat(if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .apply(builder)
            .build()

    private fun onWorkHoursStartTimeClicked() {
        val timePicker = buildTimePicker(initialTime = splitDateFromPrefs(R.string.preferences__work_time_start)) {
            setTitleText(R.string.prefs_work_time_start)
        }
        timePicker.addOnPositiveButtonClickListener {
            splitDateFromPrefs(R.string.preferences__work_time_end)
                ?.let { endTime ->
                    val newTimeStamp = timePicker.hour * 60 + timePicker.minute
                    val endTimeStamp = endTime[0] * 60 + endTime[1]
                    if (newTimeStamp >= endTimeStamp) {
                        return@addOnPositiveButtonClickListener
                    }
                }
            sharedPreferences.edit {
                putString(
                    resources.getString(R.string.preferences__work_time_start),
                    timePicker.getFormattedTime(),
                )
            }
            workHoursStartTimePreference.updateTimeSummary(defaultSummary = R.string.prefs_work_time_start_sum)
        }
        if (isAdded) {
            timePicker.show(parentFragmentManager, DIALOG_TAG_START_TIME_PICKER)
        }
    }

    private fun onWorkHoursEndTimeClicked() {
        val timePicker = buildTimePicker(initialTime = splitDateFromPrefs(R.string.preferences__work_time_end)) {
            setTitleText(R.string.prefs_work_time_end)
        }
        timePicker.addOnPositiveButtonClickListener {
            splitDateFromPrefs(R.string.preferences__work_time_start)
                ?.let { startTime ->
                    val newTimeStamp = timePicker.hour * 60 + timePicker.minute
                    val startTimeStamp = startTime[0] * 60 + startTime[1]

                    if (newTimeStamp <= startTimeStamp) {
                        return@addOnPositiveButtonClickListener
                    }
                }
            sharedPreferences.edit {
                putString(resources.getString(R.string.preferences__work_time_end), timePicker.getFormattedTime())
            }
            workHoursEndTimePreference.updateTimeSummary(defaultSummary = R.string.prefs_work_time_end_sum)
        }
        if (isAdded) {
            timePicker.show(parentFragmentManager, DIALOG_TAG_END_TIME_PICKER)
        }
    }

    private fun MaterialTimePicker.getFormattedTime(): String =
        "%02d:%02d".format(Locale.US, hour, minute) // Locale.US used intentionally, must not be changed to stay consistent with persisted data

    private fun splitDateFromPrefs(@StringRes key: Int): IntArray? {
        val value = sharedPreferences.getString(getString(key), null) ?: return null
        try {
            val parts = value.split(':')
            return intArrayOf(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            return null
        }
    }

    private fun chooseRingtone(type: Int, currentUri: Uri?, defaultUri: Uri?, title: String, tag: String) {
        try {
            when (tag) {
                DIALOG_TAG_VOIP_NOTIFICATION -> voipRingtonePickerLauncher.launch(type, currentUri, defaultUri)
                DIALOG_TAG_CONTACT_NOTIFICATION -> contactTonePickerLauncher.launch(type, currentUri, defaultUri)
                DIALOG_TAG_GROUP_NOTIFICATION -> groupTonePickerLauncher.launch(type, currentUri, defaultUri)
                DIALOG_TAG_GROUP_CALLS_NOTIFICATION -> groupCallsTonePickerLauncher.launch(type, currentUri, defaultUri)
            }
        } catch (e: ActivityNotFoundException) {
            openFallbackRingtoneSelector(type, currentUri, defaultUri, title, tag)
        }
    }

    private fun openFallbackRingtoneSelector(type: Int, currentUri: Uri?, defaultUri: Uri?, title: String, tag: String) {
        val dialog = RingtoneSelectorDialog.newInstance(
            /* title = */
            title,
            /* ringtoneType = */
            type,
            /* existingUri = */
            currentUri,
            /* defaultUri = */
            defaultUri,
            /* showDefault = */
            true,
            /* showSilent = */
            true,
        )
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, tag)
    }

    private fun getRingtoneFromRingtonePref(@StringRes preference: Int): Uri? =
        sharedPreferences.getString(resources.getString(preference), null)?.toUri()

    private fun Preference.updateRingtoneSummary(@StringRes prefKey: Int) {
        val value = this@SettingsNotificationsFragment.sharedPreferences.getString(resources.getString(prefKey), "")
        summary = if (value.isNullOrEmpty()) {
            getString(R.string.ringtone_none)
        } else {
            RingtoneUtil.getRingtoneNameFromUri(context, value.toUri())
        }
    }

    private fun Preference.updateTimeSummary(@StringRes defaultSummary: Int) {
        summary = this@SettingsNotificationsFragment.sharedPreferences.getString(key, getString(defaultSummary))
    }

    private fun Preference.updateWorkingDaysSummary(selectedDays: Set<String>) {
        summary = if (selectedDays.isNotEmpty()) {
            selectedDays.joinToString(separator = ", ") { selectedDay ->
                weekdayShortNames[selectedDay.toInt()]
            }
        } else {
            getString(R.string.prefs_working_days_sum)
        }
    }

    @RequiresApi(26)
    private fun launchSystemNotificationSettings(channelId: String) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        startActivity(intent)
    }

    override fun onRingtoneSelected(tag: String?, ringtone: Uri) {
        val toneString = ringtone.toString()
        val context = requireContext()

        when (tag) {
            DIALOG_TAG_CONTACT_NOTIFICATION -> {
                sharedPreferences.edit {
                    putString(context.getString(R.string.preferences__notification_sound), toneString)
                }
                singleChatNotificationSoundPreference.updateRingtoneSummary(R.string.preferences__notification_sound)
            }

            DIALOG_TAG_GROUP_NOTIFICATION -> {
                sharedPreferences.edit {
                    putString(context.getString(R.string.preferences__group_notification_sound), toneString)
                }
                groupChatNotificationPreference.updateRingtoneSummary(R.string.preferences__group_notification_sound)
            }

            DIALOG_TAG_VOIP_NOTIFICATION -> {
                sharedPreferences.edit {
                    putString(context.getString(R.string.preferences__voip_ringtone), toneString)
                }
                voipRingtonePreference.updateRingtoneSummary(R.string.preferences__voip_ringtone)
            }

            DIALOG_TAG_GROUP_CALLS_NOTIFICATION -> {
                sharedPreferences.edit {
                    putString(context.getString(R.string.preferences__group_calls_ringtone), toneString)
                }
                groupCallsRingtonePreference.updateRingtoneSummary(R.string.preferences__group_calls_ringtone)
            }
        }
    }

    override fun getPreferenceTitleResource() = R.string.prefs_notifications

    override fun getPreferenceResource() = R.xml.preference_notifications

    companion object {
        private const val DIALOG_TAG_CONTACT_NOTIFICATION = "cn"
        private const val DIALOG_TAG_GROUP_NOTIFICATION = "gn"
        private const val DIALOG_TAG_VOIP_NOTIFICATION = "vn"
        private const val DIALOG_TAG_GROUP_CALLS_NOTIFICATION = "gc"

        private const val DIALOG_TAG_START_TIME_PICKER = "startt"
        private const val DIALOG_TAG_END_TIME_PICKER = "endt"
    }
}
