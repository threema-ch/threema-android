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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;

import com.google.android.material.timepicker.MaterialTimePicker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormatSymbols;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import static com.google.android.material.timepicker.TimeFormat.CLOCK_12H;
import static com.google.android.material.timepicker.TimeFormat.CLOCK_24H;

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
	private Preference startPreference, endPreference;

	private Preference ringtonePreference, groupRingtonePreference, voiceRingtonePreference;

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

	private void initWorkingTimePrefs() {
		if (!ConfigUtils.isWorkBuild()) {
			// remove preferences
			PreferenceScreen preferenceScreen = findPreference("pref_key_notifications");
			PreferenceCategory preferenceCategory = findPreference("pref_key_work_life_balance");
			preferenceScreen.removePreference(preferenceCategory);
			return;
		}

		DateFormatSymbols dfs = new DateFormatSymbols(getResources().getConfiguration().locale);
		System.arraycopy(dfs.getWeekdays(), 1, weekdays, 0, 7);
		System.arraycopy(dfs.getShortWeekdays(), 1, shortWeekdays, 0, 7);

		MultiSelectListPreference multiSelectListPreference = findPreference(getString(R.string.preferences__working_days));
		multiSelectListPreference.setEntries(weekdays);
		multiSelectListPreference.setOnPreferenceChangeListener((preference, newValue) -> {
			updateWorkingDaysSummary(preference, (Set<String>)newValue);
			return true;
		});

		updateWorkingDaysSummary(multiSelectListPreference, multiSelectListPreference.getValues());

		startPreference = findPreference(getString(R.string.preferences__work_time_start));
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

		endPreference = findPreference(getString(R.string.preferences__work_time_end));
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
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
		sharedPreferences = getPreferenceManager().getSharedPreferences();

		addPreferencesFromResource(R.xml.preference_notifications);

		int miuiVersion = ConfigUtils.getMIUIVersion();
		if (miuiVersion < 10) {
			PreferenceScreen preferenceScreen = findPreference("pref_key_notifications");
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

		ringtonePreference.setOnPreferenceClickListener(preference -> {
			chooseRingtone(RingtoneManager.TYPE_NOTIFICATION,
				getRingtoneFromRingtonePref(R.string.preferences__notification_sound),
				null,
				getString(R.string.prefs_notification_sound),
				DIALOG_TAG_CONTACT_NOTIFICATION);
			return true;
		});
		groupRingtonePreference.setOnPreferenceClickListener(preference -> {
			chooseRingtone(RingtoneManager.TYPE_NOTIFICATION,
				getRingtoneFromRingtonePref(R.string.preferences__group_notification_sound),
				null,
				getString(R.string.prefs_notification_sound),
				DIALOG_TAG_GROUP_NOTIFICATION);
			return true;
		});
		voiceRingtonePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				chooseRingtone(RingtoneManager.TYPE_RINGTONE, getRingtoneFromRingtonePref(R.string.preferences__voip_ringtone), RingtoneUtil.THREEMA_CALL_RINGTONE_URI, getString(R.string.prefs_voice_call_sound), DIALOG_TAG_VOIP_NOTIFICATION);
				return true;
			}
		});

		if (ConfigUtils.isWorkRestricted()) {
			CheckBoxPreference notificationPreview = findPreference(getString(R.string.preferences__notification_preview));

			Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_message_preview));
			if (value != null) {
				notificationPreview.setEnabled(false);
				notificationPreview.setSelectable(false);
			}
		}

		if (miuiVersion >= 10) {
			ShowOnceDialog.newInstance(
				R.string.miui_notification_title,
				miuiVersion >= 12 ?
				R.string.miui12_notification_body:
				R.string.miui_notification_body).show(getFragmentManager(), DIALOG_TAG_MIUI_NOTICE);

			Preference miuiPreference = findPreference("pref_key_miui");
			miuiPreference.setOnPreferenceClickListener(preference -> {
				openMIUINotificationSettings();
				return true;
			});
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
			}
		} catch (ActivityNotFoundException e) {
			RingtoneSelectorDialog dialog = RingtoneSelectorDialog.newInstance(
				title,
				type,
				currentUri,
				defaultUri,
				true,
				true);
			dialog.setTargetFragment(SettingsNotificationsFragment.this, 0);
			dialog.show(getFragmentManager(), tag);
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

	private void updateTimeSummary(Preference preference, @StringRes int defaultSummary) {
		preference.setSummary(sharedPreferences.getString(preference.getKey(), getString(defaultSummary)));
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

		intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
		// for Android 5-7
		intent.putExtra("app_package", getActivity().getPackageName());
		intent.putExtra("app_uid", getActivity().getApplicationInfo().uid);
		// for Android O
		intent.putExtra("android.provider.extra.APP_PACKAGE", getActivity().getPackageName());
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
