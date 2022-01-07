/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2022 Threema GmbH
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
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;

public class SettingsCallsFragment extends ThreemaPreferenceFragment {
	private static final Logger logger = LoggerFactory.getLogger(SettingsCallsFragment.class);

	private static final int PERMISSION_REQUEST_READ_PHONE_STATE = 3;

	private View fragmentView;
	private CheckBoxPreference enableCallReject;

	@Override
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.preference_calls);

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


		if (ConfigUtils.isWorkRestricted()) {
			CheckBoxPreference callEnable = (CheckBoxPreference) findPreference(getResources().getString(R.string.preferences__voip_enable));
			PreferenceCategory videoCategory = (PreferenceCategory) findPreference("pref_key_voip_video_settings");
			CheckBoxPreference videoCallEnable = (CheckBoxPreference) findPreference(getResources().getString(R.string.preferences__voip_video_enable));
			DropDownPreference videoCallProfile = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__voip_video_profile));

			Boolean disableCalls = AppRestrictionUtil.getBooleanRestriction(getResources().getString(R.string.restriction__disable_calls));
			Boolean disableVideoCalls = AppRestrictionUtil.getBooleanRestriction(getResources().getString(R.string.restriction__disable_video_calls));

			if (disableCalls != null) {
				// admin does not want user to tamper with call setting
				callEnable.setEnabled(false);
				callEnable.setSelectable(false);
				callEnable.setChecked(!disableCalls);

				if (disableCalls) {
					// disabled calls also disable video calls
					disableVideoCalls = true;
				}
			 }

			if (disableVideoCalls != null) {
				// admin does not want user to tamper with video call setting
				videoCallEnable.setEnabled(false);
				videoCallEnable.setSelectable(false);
				videoCallEnable.setChecked(!disableVideoCalls);
			}

			if (disableVideoCalls == null || !disableVideoCalls) {
				// video calls are force-enabled or left to the user - user may change profile setting
				videoCategory.setDependency(null);
				videoCategory.setEnabled(true);

				videoCallProfile.setDependency(null);
				videoCallProfile.setEnabled(true);
				videoCallProfile.setSelectable(true);
			}
		}

		this.enableCallReject = (CheckBoxPreference) findPreference(getResources().getString(R.string.preferences__voip_reject_mobile_calls));
		this.enableCallReject.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean newCheckedValue = newValue.equals(true);

				if (newCheckedValue) {
					return ConfigUtils.requestPhonePermissions(getActivity(), SettingsCallsFragment.this, PERMISSION_REQUEST_READ_PHONE_STATE);
				}
				return true;
			}
		});
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		this.fragmentView = view;
		preferenceFragmentCallbackInterface.setToolbarTitle(R.string.prefs_title_voip);
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String permissions[], @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_READ_PHONE_STATE:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					this.enableCallReject.setChecked(true);
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
					ConfigUtils.showPermissionRationale(getContext(), fragmentView,  R.string.permission_phone_required);
				}
				break;
		}
	}
}
