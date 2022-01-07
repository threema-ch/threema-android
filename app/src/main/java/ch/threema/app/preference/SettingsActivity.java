/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2022 Threema GmbH
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

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.WorkExplainActivity;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.LockAppService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;

public class SettingsActivity extends PreferenceActivityCompat implements ThreemaPreferenceFragment.PreferenceFragmentCallbackInterface {
	private ActionBar actionBar;
	private PreferenceService preferenceService;
	private LockAppService lockAppService;
	private Header initialFragmentHeader = null;
	private boolean noHeaders;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			preferenceService = serviceManager.getPreferenceService();
			lockAppService = serviceManager.getLockAppService();
		} else {
			finish();
		}

		// hide contents in app switcher and inhibit screenshots
		ConfigUtils.setScreenshotsAllowed(this, preferenceService, lockAppService);
		ConfigUtils.configureActivityTheme(this);

		super.onCreate(savedInstanceState);

		setupActionBar();

		if (initialFragmentHeader != null) {
			switchToHeader(initialFragmentHeader);
		}
	}

	@Override
	protected void onDestroy() {
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(false);

		ThreemaApplication.activityDestroyed(this);

		super.onDestroy();
	}

	private void setupActionBar() {
		Toolbar toolbar = findViewById(R.id.action_bar);
		if (toolbar != null) {
			toolbar.setTitleTextAppearance(this, R.style.TextAppearance_Toolbar_Title);
			toolbar.setSubtitleTextAppearance(this, R.style.TextAppearance_Toolbar_SubTitle);
		}

		actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.menu_settings);
		}
	}

	@Override
	public boolean onIsMultiPane() {
		return ConfigUtils.isTabletLayout() && !noHeaders;
	}

	@Override
	public void onBuildHeaders(@NonNull List<Header> target) {
		String initialFragmentName;

		loadHeadersFromResource(R.xml.preference_headers, target);

		Intent intent = getIntent();

		initialFragmentName = intent.getStringExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT);
		noHeaders = intent.getBooleanExtra(PreferenceActivity.EXTRA_NO_HEADERS, false);

		boolean voipDisabled = ConfigUtils.isBlackBerry();
		if (!voipDisabled && ConfigUtils.isWorkRestricted()) {
			Boolean disableCalls = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_calls));
			voipDisabled = disableCalls != null && disableCalls;
		}

		if (!voipDisabled) {
			final Header voipHeader = new Header();
			voipHeader.fragment = "ch.threema.app.preference.SettingsCallsFragment";
			voipHeader.iconRes = R.drawable.ic_videocall;
			voipHeader.titleRes = R.string.prefs_title_voip;
			target.add(voipHeader);
		}

		final Header aboutHeader = new Header();
		aboutHeader.fragment = "ch.threema.app.preference.SettingsAboutFragment";
		aboutHeader.iconRes = R.drawable.ic_about_outline;
		aboutHeader.titleRes = R.string.menu_about;
		target.add(aboutHeader);

		if (!ConfigUtils.isWorkBuild()) {
			final Header workHeader = new Header();
			workHeader.fragment = null;
			workHeader.intent = new Intent(getApplicationContext(), WorkExplainActivity.class);
			workHeader.iconRes = R.drawable.ic_work_outline;
			workHeader.titleRes = R.string.threema_work;
			workHeader.summary = null;
			target.add(workHeader);
		}

		// Show developers menu if in debug mode and if the preference has been set
		// Note: onCreate() may not have been called yet - preferenceService is therefore not available
		if (this.preferenceService != null && this.preferenceService.showDeveloperMenu()) {
			final Header header = new Header();
			header.fragment = "ch.threema.app.preference.SettingsDeveloperFragment";
			header.iconRes = R.drawable.ic_directions_bike_grey600_24dp;
			header.titleRes = R.string.prefs_developers;
			target.add(header);
		}

		for (Header header : target) {
			if (header.fragment != null) {
				if (header.fragment.equals(initialFragmentName)) {
					this.initialFragmentHeader = header;
				}

				if (header.fragment.equals(SettingsChatFragment.class.getName())) {
					header.summary = concatStringResources(new int[]{R.string.prefs_header_keyboard, R.string.media});
				} else if (header.fragment.equals(SettingsPrivacyFragment.class.getName())) {
					header.summary = concatStringResources(new int[]{R.string.prefs_header_contacts, R.string.prefs_header_chat, R.string.prefs_header_lists});
				} else if (header.fragment.equals(SettingsSecurityFragment.class.getName())) {
					header.summary = concatStringResources(new int[]{R.string.prefs_title_access_protection, R.string.prefs_masterkey});
				} else if (header.fragment.equals(SettingsAppearanceFragment.class.getName())) {
					header.summary = concatStringResources(new int[]{R.string.prefs_theme, R.string.prefs_emoji_style, R.string.prefs_language_override, R.string.prefs_title_fontsize, R.string.prefs_contact_list_title});
				} else if (header.fragment.equals(SettingsNotificationsFragment.class.getName())) {
					header.summary = concatStringResources(new int[]{R.string.prefs_voice_call_sound, R.string.prefs_vibrate, R.string.prefs_light});
				} else if (header.fragment.equals(SettingsMediaFragment.class.getName())) {
					header.summary = concatStringResources(new int[]{R.string.prefs_image_size, R.string.prefs_auto_download_title, R.string.prefs_storage_mgmt_title});
				} else if (header.fragment.equals(SettingsCallsFragment.class.getName())) {
					header.summary = concatStringResources(new int[]{R.string.prefs_title_voip, R.string.video_calls});
				}
			}
		}
	}

	private String concatStringResources(int[] strings) {
		StringBuilder result = new StringBuilder();
		for (int res : strings) {
			if (result.length() > 0) {
				result.append(", ");
			}
			result.append(getString(res));
		}
		return result.toString();
	}

	@Override
	public boolean isValidFragment(String fragmentName) {
		return SettingsChatFragment.class.getName().equals(fragmentName)
			|| SettingsAppearanceFragment.class.getName().equals(fragmentName)
			|| SettingsMediaFragment.class.getName().equals(fragmentName)
			|| SettingsSecurityFragment.class.getName().equals(fragmentName)
			|| SettingsPrivacyFragment.class.getName().equals(fragmentName)
			|| SettingsNotificationsFragment.class.getName().equals(fragmentName)
			|| SettingsTroubleshootingFragment.class.getName().equals(fragmentName)
			|| SettingsAboutFragment.class.getName().equals(fragmentName)
			|| SettingsDeveloperFragment.class.getName().equals(fragmentName)
			|| SettingsRateFragment.class.getName().equals(fragmentName)
			|| SettingsCallsFragment.class.getName().equals(fragmentName);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				if (isMultiPane()) {
					finish();
				} else {
					onBackPressed();
				}
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void setToolbarTitle(int res) {
		if (actionBar != null && !isMultiPane()) {
			actionBar.setTitle(res);
		}
	}

	@Override
	protected void onApplyThemeResource(Resources.Theme theme, int resid, boolean first) {
		if (ConfigUtils.getAppTheme(this) == ConfigUtils.THEME_DARK) {
			theme.applyStyle(R.style.Theme_Threema_Settings_Dark, true);
		} else {
			super.onApplyThemeResource(theme, resid, first);
		}
	}

	@Override
	protected void onPause() {
		ThreemaApplication.activityPaused(this);
		super.onPause();

		overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
	}

	@Override
	protected void onResume() {
		ThreemaApplication.activityResumed(this);

		if (!BackupService.isRunning() && !RestoreService.isRunning()) {
			if (ConfigUtils.refreshDeviceTheme(this)) {
				ConfigUtils.recreateActivity(this);
			}
		}
		super.onResume();

	}

	@Override
	public void onBackPressed() {
		if (noHeaders) {
			finish();
		} else {
			setToolbarTitle(R.string.menu_settings);
			super.onBackPressed();
		}
	}

	@Override
	public void onUserInteraction() {
		ThreemaApplication.activityUserInteract(this);
		super.onUserInteraction();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
	}
}
