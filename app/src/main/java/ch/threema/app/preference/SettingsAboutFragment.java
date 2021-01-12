/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import ch.threema.app.BuildConfig;
import ch.threema.app.BuildFlavor;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.AboutActivity;
import ch.threema.app.activities.DownloadApkActivity;
import ch.threema.app.activities.LicenseActivity;
import ch.threema.app.activities.PrivacyPolicyActivity;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.license.LicenseService;
import ch.threema.app.services.license.LicenseServiceSerial;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.TestUtil;


public class SettingsAboutFragment extends ThreemaPreferenceFragment {
	private static final Logger logger = LoggerFactory.getLogger(SettingsAboutFragment.class);

	private static final int ABOUT_REQUIRED_CLICKS = 10;
	private static final String DIALOG_TAG_CHECK_UPDATE = "checkup";

	private String updateUrl, updateMessage;
	private int aboutCounter;

	private PreferenceService preferenceService;
	private LicenseService licenseService;

	@Override
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
		if (!requiredInstances()) {
			return;
		}

		addPreferencesFromResource(R.xml.preference_about);

		PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("pref_key_about");
		PreferenceCategory aboutCategory = (PreferenceCategory) findPreference("pref_key_about_header");

		Preference aboutPreference = findPreference(getResources().getString(R.string.preferences__about));
		Preference workLicensePreference = findPreference(getResources().getString(R.string.preferences__work_license_name));
		Preference licensePreference = findPreference(getResources().getString(R.string.preferences__licenses));
		Preference privacyPolicyPreference = findPreference(getResources().getString(R.string.preferences__privacy_policy));
		Preference checkUpdatePreference = findPreference(getResources().getString(R.string.preferences__check_updates));
		Preference troubleShootingPreference = findPreference(getResources().getString(R.string.preferences__troubleshooting));
		Preference deviceInfoPreference = findPreference(getResources().getString(R.string.preferences__device_info));
		Preference translatorsPreference = findPreference(getResources().getString(R.string.preferences__translators));

		aboutPreference.setTitle(getString(R.string.threema_version) + " " + getVersionString());
		aboutPreference.setSummary(R.string.about_copyright);
		aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (aboutCounter == ABOUT_REQUIRED_CLICKS) {
					aboutCounter++;
					final Intent intent = new Intent(getActivity().getApplicationContext(), AboutActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
					startActivity(intent);
					final Activity activity = getActivity();
					if (activity != null) {
						activity.finish();
					}
					return true;
				} else {
					aboutCounter++;
					return false;
				}
			}
		});

		licensePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(getActivity().getApplicationContext(), LicenseActivity.class));
				return true;
			}
		});

		privacyPolicyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(getActivity().getApplicationContext(), PrivacyPolicyActivity.class));
				return true;
			}
		});

		if (ConfigUtils.isSerialLicensed() && !ConfigUtils.isWorkBuild()) {
			checkUpdatePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					checkForUpdates((LicenseServiceSerial) licenseService);
					return true;
				}
			});
		} else {
			aboutCategory.removePreference(checkUpdatePreference);
		}

		if (ConfigUtils.isWorkBuild()) {
			workLicensePreference.setSummary(preferenceService.getLicenseUsername());
			preferenceScreen.removePreference(findPreference("pref_key_feedback_header"));

			if (ConfigUtils.isWorkRestricted()) {
				Boolean readonly = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__readonly_profile));

				if (readonly != null && readonly) {
					aboutCategory.removePreference(workLicensePreference);
				}
			}
		} else {
			aboutCategory.removePreference(workLicensePreference);
		}

		if (deviceInfoPreference != null) {
			if (Build.MANUFACTURER != null) {
				deviceInfoPreference.setTitle(Build.MANUFACTURER + " " + Build.MODEL);
			}
			deviceInfoPreference.setSummary(Build.FINGERPRINT);
		}

		if (troubleShootingPreference != null) {
			troubleShootingPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					final Header header = new Header();
					header.fragment = "ch.threema.app.preference.SettingsTroubleshootingFragment";
					header.iconRes = R.drawable.ic_bug_outline;
					header.titleRes = R.string.prefs_troubleshooting;

					try {
						((SettingsActivity) getActivity()).switchToHeader(header);
					} catch (Exception ignore) {}
					return true;
				}
			});
		}

		translatorsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				SimpleStringAlertDialog.newInstance(R.string.translators, getString(R.string.translators_thanks, getString(R.string.translators_list))).show(getFragmentManager(), "tt");
				return true;
			}
		});

	}

	final protected boolean requiredInstances() {
		if (!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	protected boolean checkInstances() {
		return TestUtil.required(
				this.preferenceService,
				this.licenseService
		);
	}

	protected void instantiate() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			this.preferenceService = serviceManager.getPreferenceService();
			try {
				this.licenseService = serviceManager.getLicenseService();
			} catch (FileSystemNotPresentException e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		preferenceFragmentCallbackInterface.setToolbarTitle(R.string.menu_about);
		super.onViewCreated(view, savedInstanceState);
	}

	private String getVersionString() {
		final Context context = getContext();
		final StringBuilder version = new StringBuilder();
		version.append(ConfigUtils.getFullAppVersion(context));
		version.append(" Build ").append(ConfigUtils.getBuildNumber(context));
		version.append(" ").append(BuildFlavor.getName());
		if (BuildConfig.DEBUG) {
			version.append(" Commit ").append(BuildConfig.GIT_HASH);
		}
		return version.toString();
	}

	@SuppressLint("StaticFieldLeak")
	private void checkForUpdates(final @NonNull LicenseServiceSerial licenseServiceSerial) {
		if (BuildConfig.FLAVOR.equals("store_threema")) {
				new AsyncTask<Void, Void, String>() {
					@Override
					protected void onPreExecute() {
						GenericProgressDialog.newInstance(R.string.check_updates, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_CHECK_UPDATE);
					}

					@Override
					protected String doInBackground(Void... voids) {
						try {
							// Validate license and check for updates
							licenseServiceSerial.validate(false);

							// If an update is available, then `updateUrl` and `updateMessage` will
							// be set to a non-null value.
							updateUrl = licenseServiceSerial.getUpdateUrl();
							updateMessage = licenseServiceSerial.getUpdateMessage();
							if (TestUtil.empty(updateUrl, updateMessage)) {
								// No update available...
								return getString(R.string.no_update_available);
							}

							// Update available!
							return null;
						} catch (final Exception x) {
							return String.format(getString(R.string.an_error_occurred_more), x.getLocalizedMessage());
						}
					}

					@Override
					protected void onPostExecute(String error) {
						DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_CHECK_UPDATE, true);
						if (error != null) {
							SimpleStringAlertDialog.newInstance(R.string.check_updates, error).show(getFragmentManager(), "nu");
						} else {
							Intent dialogIntent = IntentDataUtil.createActionIntentUpdateAvailable(updateMessage, updateUrl);
							dialogIntent.putExtra(DownloadApkActivity.EXTRA_FORCE_UPDATE_DIALOG, true);
							dialogIntent.setClass(getContext(), DownloadApkActivity.class);
							startActivity(dialogIntent);
						}
					}
				}.execute();
		}
	}
}
