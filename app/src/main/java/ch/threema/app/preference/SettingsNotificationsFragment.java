/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import com.takisoft.preferencex.TimePickerPreference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormatSymbols;
import java.util.Arrays;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.RingtoneSelectorDialog;
import ch.threema.app.dialogs.ShowOnceDialog;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RingtoneUtil;

public class SettingsNotificationsFragment extends ThreemaPreferenceFragment implements GenericAlertDialog.DialogClickListener, RingtoneSelectorDialog.RingtoneSelectorDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(SettingsNotificationsFragment.class);

	private static final String DIALOG_TAG_NOTIFICATIONS_DISABLED = "ndd";
	private static final String DIALOG_TAG_CONTACT_NOTIFICATION = "cn";
	private static final String DIALOG_TAG_GROUP_NOTIFICATION = "gn";
	private static final String DIALOG_TAG_VOIP_NOTIFICATION = "vn";
	private static final String DIALOG_TAG_MIUI_NOTICE = "miui10_channel_notice";

	private static final int INTENT_SYSTEM_NOTIFICATION_SETTINGS = 5199;

	private SharedPreferences sharedPreferences;
	private NotificationManagerCompat notificationManagerCompat;

	// Weekdays used for work-life balance prefs
	private final String[] weekdays = new String[7];
	private final String[] shortWeekdays = new String[7];
	private final String[] weekday_values = new String[]{"0", "1", "2", "3", "4", "5", "6"};
	private TimePickerPreference startPreference, endPreference;

	private Preference ringtonePreference, groupRingtonePreference, voiceRingtonePreference;

	private void initWorkingTimePrefs() {
		if (!ConfigUtils.isWorkBuild()) {
			// remove preferences
			PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("pref_key_notifications");
			PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("pref_key_work_life_balance");
			preferenceScreen.removePreference(preferenceCategory);
			return;
		}

		DateFormatSymbols dfs = new DateFormatSymbols(getResources().getConfiguration().locale);
		System.arraycopy(dfs.getWeekdays(), 1, weekdays, 0, 7);
		System.arraycopy(dfs.getShortWeekdays(), 1, shortWeekdays, 0, 7);

		MultiSelectListPreference multiSelectListPreference = (MultiSelectListPreference) findPreference(getString(R.string.preferences__working_days));
		multiSelectListPreference.setEntries(weekdays);
		multiSelectListPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				updateWorkingDaysSummary(preference, (Set<String>)newValue);
				return true;
			}
		});

		updateWorkingDaysSummary(multiSelectListPreference, multiSelectListPreference.getValues());

		startPreference = (TimePickerPreference) findPreference(getString(R.string.preferences__work_time_start));
		startPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				TimePickerPreference.TimeWrapper newTime = (TimePickerPreference.TimeWrapper) newValue;
				int newTimeStamp = newTime.hour * 60 + newTime.minute;
				int endTimeStamp = endPreference.getHourOfDay() * 60 + endPreference.getMinute();

				return newTimeStamp < endTimeStamp;
			}
		});
		endPreference = (TimePickerPreference) findPreference(getString(R.string.preferences__work_time_end));
		endPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				TimePickerPreference.TimeWrapper newTime = (TimePickerPreference.TimeWrapper) newValue;
				int newTimeStamp = newTime.hour * 60 + newTime.minute;
				int startTimeStamp = startPreference.getHourOfDay() * 60 + startPreference.getMinute();

				return newTimeStamp > startTimeStamp;
			}
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

	@Override
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
		sharedPreferences = getPreferenceManager().getSharedPreferences();

		addPreferencesFromResource(R.xml.preference_notifications);

		if (!ConfigUtils.isMIUI10()) {
			PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("pref_key_notifications");
			preferenceScreen.removePreference(findPreference("pref_key_miui"));
		}

		notificationManagerCompat = NotificationManagerCompat.from(getActivity());

		initWorkingTimePrefs();

		// setup defaults and callbacks
		ringtonePreference = findPreference(getResources().getString(R.string.preferences__notification_sound));
		updateRingtoneSummary(ringtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__notification_sound), ""));
		groupRingtonePreference = findPreference(getResources().getString(R.string.preferences__group_notification_sound));
		updateRingtoneSummary(groupRingtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__group_notification_sound), ""));
		voiceRingtonePreference = findPreference(getResources().getString(R.string.preferences__voip_ringtone));
		updateRingtoneSummary(voiceRingtonePreference, sharedPreferences.getString(getResources().getString(R.string.preferences__voip_ringtone), ""));

		ringtonePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				RingtoneSelectorDialog dialog = RingtoneSelectorDialog.newInstance(getString(R.string.prefs_notification_sound),
					RingtoneManager.TYPE_NOTIFICATION,
					getRingtoneFromRingtonePref(R.string.preferences__notification_sound),
					null,
					true,
					true);
				dialog.setTargetFragment(SettingsNotificationsFragment.this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_CONTACT_NOTIFICATION);
				return true;
			}
		});
		groupRingtonePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				RingtoneSelectorDialog dialog = RingtoneSelectorDialog.newInstance(getString(R.string.prefs_notification_sound),
					RingtoneManager.TYPE_NOTIFICATION,
					getRingtoneFromRingtonePref(R.string.preferences__group_notification_sound),
					null,
					true,
					true);
				dialog.setTargetFragment(SettingsNotificationsFragment.this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_GROUP_NOTIFICATION);
				return true;
			}
		});
		voiceRingtonePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {

				RingtoneSelectorDialog dialog = RingtoneSelectorDialog.newInstance(getString(R.string.prefs_voice_call_sound),
					RingtoneManager.TYPE_RINGTONE,
					getRingtoneFromRingtonePref(R.string.preferences__voip_ringtone),
					RingtoneUtil.THREEMA_CALL_RINGTONE_URI,
					true,
					true);
				dialog.setTargetFragment(SettingsNotificationsFragment.this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_VOIP_NOTIFICATION);
				return true;
			}
		});

		if (ConfigUtils.isWorkRestricted()) {
			CheckBoxPreference notificationPreview = (CheckBoxPreference) findPreference(getString(R.string.preferences__notification_preview));

			Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_message_preview));
			if (value != null) {
				notificationPreview.setEnabled(false);
				notificationPreview.setSelectable(false);
			}
		}

		if (ConfigUtils.isMIUI10()) {
			ShowOnceDialog.newInstance(R.string.miui_notification_title, R.string.miui_notification_body).show(getFragmentManager(), DIALOG_TAG_MIUI_NOTICE);

			Preference miuiPreference = findPreference("pref_key_miui");
			miuiPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					openMIUINotificationSettings();
					return true;
				}
			});
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		preferenceFragmentCallbackInterface.setToolbarTitle(R.string.prefs_notifications);
		super.onViewCreated(view, savedInstanceState);

		if (!notificationManagerCompat.areNotificationsEnabled()) {
			showNotificationsDisabledDialog();
		}
	}

	private void showNotificationsDisabledDialog() {
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.notifications_disabled_title, R.string.notifications_disabled_text, R.string.notifications_disabled_settings, R.string.cancel);
		dialog.setTargetFragment(this, 0);
		dialog.show(getFragmentManager(), DIALOG_TAG_NOTIFICATIONS_DISABLED);
	}

	private Uri getRingtoneFromRingtonePref(@StringRes int preference) {
		String uriString = sharedPreferences.getString(getResources().getString(preference), null);
		if (uriString == null) {
			// silent
			uriString = "";
		}
		return Uri.parse(uriString);
	}

	private void updateRingtoneSummary(Preference preference, String value) {
		String summary = null;
		if (value == null || value.length() == 0) {
			summary = getString(R.string.ringtone_none);
		} else {
			summary = RingtoneUtil.getRingtoneNameFromUri(getContext(), Uri.parse(value));
		}
		preference.setSummary(summary);
	}

	private void openMIUINotificationSettings() {
		ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.Settings$NotificationFilterActivity");
		Bundle bundle = new Bundle();
		bundle.putString("appName", getContext().getResources().getString(getContext().getApplicationInfo().labelRes));
		bundle.putString("packageName", BuildConfig.APPLICATION_ID);
		bundle.putString(":android:show_fragment", "NotificationAccessSettings");

		Intent intent = new Intent();
		intent.putExtras(bundle);
		intent.setComponent(cn);

		try {
			startActivity(intent);
		} catch (Exception e) {
			logger.error("Exception", e);
		}
	}

	@Override
	public void onYes(String tag, Object data) {
		Intent intent = new Intent();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
			// for Android 5-7
			intent.putExtra("app_package", getActivity().getPackageName());
			intent.putExtra("app_uid", getActivity().getApplicationInfo().uid);
			// for Android O
			intent.putExtra("android.provider.extra.APP_PACKAGE", getActivity().getPackageName());
		} else {
			intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			intent.addCategory(Intent.CATEGORY_DEFAULT);
			intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
		}
		startActivityForResult(intent, INTENT_SYSTEM_NOTIFICATION_SETTINGS);
	}

	@Override
	public void onNo(String tag, Object data) {
		// ignore disabled notifications
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == INTENT_SYSTEM_NOTIFICATION_SETTINGS && !notificationManagerCompat.areNotificationsEnabled()) {
			// return from system settings but notifications still disabled
			showNotificationsDisabledDialog();
		}
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
		}
	}

	@Override
	public void onCancel(String tag) {}
}
