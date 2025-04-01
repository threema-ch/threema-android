/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.app.preference;

import static android.provider.Settings.System.DEFAULT_RINGTONE_URI;
import static com.google.android.material.timepicker.TimeFormat.CLOCK_12H;
import static com.google.android.material.timepicker.TimeFormat.CLOCK_24H;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.format.DateFormat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.preference.CheckBoxPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

import com.google.android.material.timepicker.MaterialTimePicker;

import org.slf4j.Logger;

import java.text.DateFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.RingtoneSelectorDialog;
import ch.threema.app.notifications.NotificationChannels;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.base.utils.LoggingUtil;

public class SettingsNotificationsFragment extends ThreemaPreferenceFragment implements RingtoneSelectorDialog.RingtoneSelectorDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("SettingsNotificationsFragment");

    private static final String DIALOG_TAG_CONTACT_NOTIFICATION = "cn";
    private static final String DIALOG_TAG_GROUP_NOTIFICATION = "gn";
    private static final String DIALOG_TAG_VOIP_NOTIFICATION = "vn";
    private static final String DIALOG_TAG_GROUP_CALLS_NOTIFICATION = "gc";

    private SharedPreferences sharedPreferences;

    // Weekdays used for work-life balance prefs
    private final String[] weekdays = new String[7];
    private final String[] shortWeekdays = new String[7];
    private final String[] weekday_values = new String[]{"0", "1", "2", "3", "4", "5", "6"};
    private Preference startPreference, endPreference;

    private Preference ringtonePreference, groupRingtonePreference, voiceRingtonePreference, groupCallsRingtonePreference;

    private final ActivityResultLauncher<Intent> voipRingtonePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                onRingtoneSelected(DIALOG_TAG_VOIP_NOTIFICATION, uri);
            }
        });

    private final ActivityResultLauncher<Intent> contactTonePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                onRingtoneSelected(DIALOG_TAG_CONTACT_NOTIFICATION, uri);
            }
        });

    private final ActivityResultLauncher<Intent> groupTonePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                onRingtoneSelected(DIALOG_TAG_GROUP_NOTIFICATION, uri);
            }
        });

    private final ActivityResultLauncher<Intent> groupCallsTonePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                onRingtoneSelected(DIALOG_TAG_GROUP_CALLS_NOTIFICATION, uri);
            }
        });

    private final Preference.OnPreferenceChangeListener systemRingtoneChangedListener = (preference, newValue) -> {
        boolean newCheckedValue = newValue.equals(true);

        String tag = preference.getKey().equals(getString(R.string.preferences__group_calls_use_system_ringtone)) ?
            DIALOG_TAG_GROUP_CALLS_NOTIFICATION :
            DIALOG_TAG_VOIP_NOTIFICATION;

        if (newCheckedValue) {
            onRingtoneSelected(tag, DEFAULT_RINGTONE_URI);
        } else {
            onRingtoneSelected(tag, RingtoneUtil.THREEMA_CALL_RINGTONE_URI);
        }
        return true;
    };

    @RequiresApi(26)
    private void launchSystemNotificationSettings(String channelId) {
        Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
        startActivity(intent);
    }

    /**
     * Remove preference categories that do not apply to the current API level
     */
    private void initVersionDependentPrefs() {
        PreferenceScreen preferenceScreen = getPref("pref_key_notifications");
        if (ConfigUtils.supportsNotificationChannels()) {
            PreferenceCategory preferenceCategory = getPref("pref_key_system_notif");
            preferenceScreen.removePreference(preferenceCategory);
            preferenceCategory = getPref("pref_key_group_notif");
            preferenceScreen.removePreference(preferenceCategory);
            preferenceCategory = getPref("pref_key_groupcall_notif");
            preferenceScreen.removePreference(preferenceCategory);
            preferenceCategory = getPref("pref_key_other");

            Preference preference = preferenceCategory.findPreference("pref_notification_priority");
            if (preference != null) {
                preferenceCategory.removePreference(preference);
            }
        } else {
            PreferenceCategory preferenceCategory = getPref("pref_key_notif_new");
            preferenceScreen.removePreference(preferenceCategory);
        }
    }

    private void initWorkingTimePrefs() {
        if (!ConfigUtils.isWorkBuild()) {
            // remove preferences
            PreferenceScreen preferenceScreen = getPref("pref_key_notifications");
            PreferenceCategory preferenceCategory = getPref("pref_key_work_life_balance");
            preferenceScreen.removePreference(preferenceCategory);
            return;
        }

        DateFormatSymbols dfs = new DateFormatSymbols(getResources().getConfiguration().locale);
        System.arraycopy(dfs.getWeekdays(), 1, weekdays, 0, 7);
        System.arraycopy(dfs.getShortWeekdays(), 1, shortWeekdays, 0, 7);

        MultiSelectListPreference multiSelectListPreference = getPref(getString(R.string.preferences__working_days));
        multiSelectListPreference.setEntries(weekdays);
        multiSelectListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            //noinspection unchecked
            updateWorkingDaysSummary(preference, (Set<String>) newValue);
            return true;
        });

        updateWorkingDaysSummary(multiSelectListPreference, multiSelectListPreference.getValues());

        startPreference = getPref(getString(R.string.preferences__work_time_start));
        updateTimeSummary(startPreference, R.string.prefs_work_time_start_sum);
        startPreference.setOnPreferenceClickListener(preference -> {
            int[] startTime = splitDateFromPrefs(R.string.preferences__work_time_start);

            final MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTitleText(R.string.prefs_work_time_start)
                .setHour(startTime != null ? startTime[0] : 0)
                .setMinute(startTime != null ? startTime[1] : 0)
                .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? CLOCK_24H : CLOCK_12H)
                .build();
            timePicker.addOnPositiveButtonClickListener(v1 -> {
                int[] endTime = splitDateFromPrefs(R.string.preferences__work_time_end);

                if (endTime != null) {
                    int newTimeStamp = timePicker.getHour() * 60 + timePicker.getMinute();
                    int endTimeStamp = endTime[0] * 60 + endTime[1];

                    if (newTimeStamp >= endTimeStamp) {
                        return;
                    }
                }
                String newValue = String.format(Locale.US, "%02d:%02d", timePicker.getHour(), timePicker.getMinute());
                sharedPreferences.edit().putString(getResources().getString(R.string.preferences__work_time_start), newValue).apply();
                updateTimeSummary(startPreference, R.string.prefs_work_time_start_sum);
            });
            if (isAdded()) {
                timePicker.show(getParentFragmentManager(), "startt");
            }
            return true;
        });

        endPreference = getPref(getString(R.string.preferences__work_time_end));
        updateTimeSummary(endPreference, R.string.prefs_work_time_end_sum);
        endPreference.setOnPreferenceClickListener(preference -> {
            int[] endTime = splitDateFromPrefs(R.string.preferences__work_time_end);

            final MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTitleText(R.string.prefs_work_time_end)
                .setHour(endTime != null ? endTime[0] : 0)
                .setMinute(endTime != null ? endTime[1] : 0)
                .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? CLOCK_24H : CLOCK_12H)
                .build();
            timePicker.addOnPositiveButtonClickListener(v1 -> {
                int[] startTime = splitDateFromPrefs(R.string.preferences__work_time_start);

                if (startTime != null) {
                    int newTimeStamp = timePicker.getHour() * 60 + timePicker.getMinute();
                    int startTimeStamp = startTime[0] * 60 + startTime[1];

                    if (newTimeStamp <= startTimeStamp) {
                        return;
                    }
                }
                String newValue = String.format(Locale.US, "%02d:%02d", timePicker.getHour(), timePicker.getMinute());
                sharedPreferences.edit().putString(getResources().getString(R.string.preferences__work_time_end), newValue).apply();
                updateTimeSummary(endPreference, R.string.prefs_work_time_end_sum);
            });
            if (isAdded()) {
                timePicker.show(getParentFragmentManager(), "endt");
            }
            return true;
        });
    }

    private void updateWorkingDaysSummary(Preference preference, Set<String> values) {
        StringBuilder summary = new StringBuilder();
        for (String value : values) {
            int index = Arrays.asList(weekday_values).indexOf(value);
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(shortWeekdays[index]);
        }

        if (summary.length() == 0) {
            summary = new StringBuilder(getString(R.string.prefs_working_days_sum));
        }
        preference.setSummary(summary);
    }

    @Nullable
    private int[] splitDateFromPrefs(@StringRes int key) {
        String value = sharedPreferences.getString(getString(key), null);
        if (value == null) {
            return null;
        }
        try {
            String[] hourMinuteString = value.split(":");
            int[] hourMinuteInt = new int[2];
            hourMinuteInt[0] = Integer.parseInt(hourMinuteString[0]);
            hourMinuteInt[1] = Integer.parseInt(hourMinuteString[1]);

            return hourMinuteInt;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void initializePreferences() {
        sharedPreferences = getPreferenceManager().getSharedPreferences();

        initWorkingTimePrefs();
        initVersionDependentPrefs();

        // setup defaults and callbacks
        voiceRingtonePreference = findPreference(getResources().getString(R.string.preferences__voip_ringtone));
        updateRingtoneSummary(voiceRingtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__voip_ringtone), ""));
        groupCallsRingtonePreference = findPreference(getResources().getString(R.string.preferences__group_calls_ringtone));
        updateRingtoneSummary(groupCallsRingtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__group_calls_ringtone), ""));

        if (ConfigUtils.supportsNotificationChannels()) {
            findPreference(getResources().getString(R.string.preferences__chat_notification_settings)).setOnPreferenceClickListener(preference -> {
                launchSystemNotificationSettings(NotificationChannels.NOTIFICATION_CHANNEL_CHATS_DEFAULT);
                return true;
            });
            findPreference(getResources().getString(R.string.preferences__group_notification_settings)).setOnPreferenceClickListener(preference -> {
                launchSystemNotificationSettings(NotificationChannels.NOTIFICATION_CHANNEL_GROUP_CHATS_DEFAULT);
                return true;
            });
            findPreference(getResources().getString(R.string.preferences__group_call_notification_settings)).setOnPreferenceClickListener(preference -> {
                launchSystemNotificationSettings(NotificationChannels.NOTIFICATION_CHANNEL_INCOMING_GROUP_CALLS);
                return true;
            });
        } else {
            ringtonePreference = findPreference(getResources().getString(R.string.preferences__notification_sound));
            updateRingtoneSummary(ringtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__notification_sound), ""));

            ringtonePreference.setOnPreferenceClickListener(preference -> {
                chooseRingtone(RingtoneManager.TYPE_NOTIFICATION,
                    getRingtoneFromRingtonePref(R.string.preferences__notification_sound),
                    null,
                    getString(R.string.prefs_notification_sound),
                    DIALOG_TAG_CONTACT_NOTIFICATION);
                return true;
            });

            groupRingtonePreference = findPreference(getResources().getString(R.string.preferences__group_notification_sound));
            updateRingtoneSummary(groupRingtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__group_notification_sound), ""));

            groupRingtonePreference.setOnPreferenceClickListener(preference -> {
                chooseRingtone(RingtoneManager.TYPE_NOTIFICATION,
                    getRingtoneFromRingtonePref(R.string.preferences__group_notification_sound),
                    null,
                    getString(R.string.prefs_notification_sound),
                    DIALOG_TAG_GROUP_NOTIFICATION);
                return true;
            });
        }

        if (voiceRingtonePreference != null) {
            voiceRingtonePreference.setOnPreferenceClickListener(preference -> {
                chooseRingtone(RingtoneManager.TYPE_RINGTONE,
                    getRingtoneFromRingtonePref(R.string.preferences__voip_ringtone),
                    RingtoneUtil.THREEMA_CALL_RINGTONE_URI,
                    getString(R.string.prefs_voice_call_sound),
                    DIALOG_TAG_VOIP_NOTIFICATION);
                return true;
            });

            TwoStatePreference systemRingtonePreference = getPref(getString(R.string.preferences__use_system_ringtone));
            systemRingtonePreference.setOnPreferenceChangeListener(systemRingtoneChangedListener);
        }

        if (groupCallsRingtonePreference != null) {
            groupCallsRingtonePreference.setOnPreferenceClickListener(preference -> {
                chooseRingtone(RingtoneManager.TYPE_RINGTONE,
                    getRingtoneFromRingtonePref(R.string.preferences__group_calls_ringtone),
                    RingtoneUtil.THREEMA_CALL_RINGTONE_URI,
                    getString(R.string.prefs_voice_call_sound),
                    DIALOG_TAG_GROUP_CALLS_NOTIFICATION);
                return true;
            });

            TwoStatePreference groupCallsSystemRingtonePreference = getPref(getString(R.string.preferences__group_calls_use_system_ringtone));
            groupCallsSystemRingtonePreference.setOnPreferenceChangeListener(systemRingtoneChangedListener);
        }

        if (ConfigUtils.isWorkRestricted()) {
            CheckBoxPreference notificationPreview = getPref(getString(R.string.preferences__notification_preview));

            Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_message_preview));
            if (value != null) {
                notificationPreview.setEnabled(false);
                notificationPreview.setSelectable(false);
            }
        }
    }

    private void chooseRingtone(final int type, final Uri currentUri, final Uri defaultUri, final String title, final String tag) {
        try {
            Intent intent = RingtoneUtil.getRingtonePickerIntent(type, currentUri, defaultUri);

            switch (tag) {
                case DIALOG_TAG_VOIP_NOTIFICATION:
                    voipRingtonePickerLauncher.launch(intent);
                    break;
                case DIALOG_TAG_CONTACT_NOTIFICATION:
                    contactTonePickerLauncher.launch(intent);
                    break;
                case DIALOG_TAG_GROUP_NOTIFICATION:
                    groupTonePickerLauncher.launch(intent);
                    break;
                case DIALOG_TAG_GROUP_CALLS_NOTIFICATION:
                    groupCallsTonePickerLauncher.launch(intent);
                    break;
            }
        } catch (ActivityNotFoundException e) {
            RingtoneSelectorDialog dialog = RingtoneSelectorDialog.newInstance(
                title,
                type,
                currentUri,
                defaultUri,
                true,
                true);
            dialog.setTargetFragment(this, 0);
            dialog.show(getParentFragmentManager(), tag);
        }

    }

    private Uri getRingtoneFromRingtonePref(@StringRes int preference) {
        String uriString = sharedPreferences.getString(getResources().getString(preference), null);
        if (uriString == null) {
            // silent
            uriString = "";
        }
        return Uri.parse(uriString);
    }

    private void updateRingtoneSummary(@Nullable Preference preference, String value) {
        if (preference == null) {
            return;
        }

        String summary;
        if (value == null || value.length() == 0) {
            summary = getString(R.string.ringtone_none);
        } else {
            summary = RingtoneUtil.getRingtoneNameFromUri(getContext(), Uri.parse(value));
        }
        preference.setSummary(summary);
    }

    private void updateTimeSummary(@Nullable Preference preference, @StringRes int defaultSummary) {
        if (preference == null) {
            return;
        }

        preference.setSummary(sharedPreferences.getString(preference.getKey(), getString(defaultSummary)));
    }

    @Override
    public void onRingtoneSelected(String tag, Uri ringtone) {
        String toneString = ringtone != null ? ringtone.toString() : "";

        switch (tag) {
            case DIALOG_TAG_CONTACT_NOTIFICATION:
                sharedPreferences.edit().putString(ThreemaApplication.getAppContext().getString(R.string.preferences__notification_sound), toneString).apply();
                updateRingtoneSummary(ringtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__notification_sound), ""));
                break;
            case DIALOG_TAG_GROUP_NOTIFICATION:
                sharedPreferences.edit().putString(ThreemaApplication.getAppContext().getString(R.string.preferences__group_notification_sound), toneString).apply();
                updateRingtoneSummary(groupRingtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__group_notification_sound), ""));
                break;
            case DIALOG_TAG_VOIP_NOTIFICATION:
                sharedPreferences.edit().putString(ThreemaApplication.getAppContext().getString(R.string.preferences__voip_ringtone), toneString).apply();
                updateRingtoneSummary(voiceRingtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__voip_ringtone), ""));
                break;
            case DIALOG_TAG_GROUP_CALLS_NOTIFICATION:
                sharedPreferences.edit().putString(ThreemaApplication.getAppContext().getString(R.string.preferences__group_calls_ringtone), toneString).apply();
                updateRingtoneSummary(groupCallsRingtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__group_calls_ringtone), ""));
                break;
        }
    }

    @Override
    public void onCancel(String tag) {
        // Don't do anything on cancel
    }

    @Override
    public int getPreferenceTitleResource() {
        return R.string.prefs_notifications;
    }

    @Override
    public int getPreferenceResource() {
        return R.xml.preference_notifications;
    }

}
