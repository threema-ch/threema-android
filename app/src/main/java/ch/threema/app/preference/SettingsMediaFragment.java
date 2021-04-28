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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.StorageManagementActivity;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;

public class SettingsMediaFragment extends ThreemaPreferenceFragment {
	private static final Logger logger = LoggerFactory.getLogger(SettingsMediaFragment.class);

	private static final int PERMISSION_REQUEST_SAVE_MEDIA = 1;
	private CheckBoxPreference saveMediaPreference;
	private View fragmentView;

	@Override
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.preference_media);

		PreferenceService preferenceService;
		try {
			preferenceService = ThreemaApplication.getServiceManager().getPreferenceService();
		} catch (Exception e) {
			logger.error("Exception", e);
			return;
		}

		if (preferenceService == null) {
			return;
		}

		Preference autoDownloadExplainSizePreference = findPreference(getResources().getString(R.string.preferences__auto_download_explain));
		autoDownloadExplainSizePreference.setSummary(getString(R.string.auto_download_limit_explain, Formatter.formatShortFileSize(getContext(),
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
			MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_SI :
			MessageServiceImpl.FILE_AUTO_DOWNLOAD_MAX_SIZE_ISO)));

		Preference mediaPreference = findPreference(getResources().getString(R.string.preferences__storage_management));
		mediaPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(getActivity(), StorageManagementActivity.class));
				return true;
			}
		});

		saveMediaPreference = (CheckBoxPreference) findPreference(getResources().getString(R.string.preferences__save_media));
		saveMediaPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if ((boolean) newValue) {
					return ConfigUtils.requestStoragePermissions(getActivity(), SettingsMediaFragment.this,  PERMISSION_REQUEST_SAVE_MEDIA);
				}
				return true;
			}
		});

		if (ConfigUtils.isWorkRestricted()) {
			Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_save_to_gallery));

			if (value != null) {
				saveMediaPreference.setEnabled(false);
				saveMediaPreference.setSelectable(false);
			}
		}

		MultiSelectListPreference wifiDownloadPreference = (MultiSelectListPreference) findPreference(getResources().getString(R.string.preferences__auto_download_wifi));
		wifiDownloadPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				preference.setSummary(getAutoDownloadSummary((Set<String>)newValue));
				return true;
			}
		});
		wifiDownloadPreference.setSummary(getAutoDownloadSummary(preferenceService.getWifiAutoDownload()));

		MultiSelectListPreference mobileDownloadPreference = (MultiSelectListPreference) findPreference(getResources().getString(R.string.preferences__auto_download_mobile));
		mobileDownloadPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				preference.setSummary(getAutoDownloadSummary((Set<String>)newValue));
				return true;
			}
		});
		mobileDownloadPreference.setSummary(getAutoDownloadSummary(preferenceService.getMobileAutoDownload()));
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		this.fragmentView = view;
		preferenceFragmentCallbackInterface.setToolbarTitle(R.string.prefs_media_title);
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_SAVE_MEDIA:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					saveMediaPreference.setChecked(true);
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
					ConfigUtils.showPermissionRationale(getContext(), fragmentView,  R.string.permission_storage_required);
				}
				break;
		}
	}

	private CharSequence getAutoDownloadSummary(Set<String> selectedOptions) {
		String[] values = getResources().getStringArray(R.array.list_auto_download_values);
		List<String> result = new ArrayList<>(selectedOptions.size());

		for (int i=0; i < values.length; i++) {
			if (selectedOptions.contains(values[i])) result.add(getResources().getStringArray(R.array.list_auto_download)[i]);
		}

		return result.isEmpty() ? getResources().getString(R.string.never) : TextUtils.join(", ", result);
	}
}
