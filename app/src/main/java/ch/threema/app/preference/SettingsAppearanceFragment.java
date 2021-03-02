/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.WallpaperService;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.StateBitmapUtil;
import ch.threema.app.utils.TestUtil;

public class SettingsAppearanceFragment extends ThreemaPreferenceFragment implements GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(SettingsAppearanceFragment.class);

	private int oldTheme;
	private SharedPreferences sharedPreferences;
	private WallpaperService wallpaperService;
	private FileService fileService;

	@Override
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {

		sharedPreferences = getPreferenceManager().getSharedPreferences();

		if (!requiredInstances()) {
			return;
		}

		addPreferencesFromResource(R.xml.preference_appearance);

		CheckBoxPreference defaultColoredAvatar = (CheckBoxPreference) findPreference(getResources().getString(R.string.preferences__default_contact_picture_colored));
		if(defaultColoredAvatar != null) {
			defaultColoredAvatar.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean newCheckedValue = newValue.equals(true);
					if (((CheckBoxPreference) preference).isChecked() != newCheckedValue) {
						ListenerManager.contactSettingsListeners.handle(new ListenerManager.HandleListener<ContactSettingsListener>() {
							@Override
							public void handle(ContactSettingsListener listener) {
								listener.onAvatarSettingChanged();
							}
						});
					}
					return true;
				}
			});
		}

		CheckBoxPreference showProfilePics = (CheckBoxPreference) findPreference(getResources().getString(R.string.preferences__receive_profilepics));
		if(showProfilePics != null) {
			showProfilePics.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean newCheckedValue = newValue.equals(true);
					if (((CheckBoxPreference) preference).isChecked() != newCheckedValue) {
						ListenerManager.contactSettingsListeners.handle(new ListenerManager.HandleListener<ContactSettingsListener>() {
							@Override
							public void handle(ContactSettingsListener listener) {
								listener.onAvatarSettingChanged();
							}
						});
					}
					return true;
				}
			});
		}

		CheckBoxPreference biggerSingleEmojis = (CheckBoxPreference) findPreference(getResources().getString(R.string.preferences__bigger_single_emojis));
		if (biggerSingleEmojis != null) {
			biggerSingleEmojis.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					ConfigUtils.setBiggerSingleEmojis(newValue.equals(true));
					return true;
				}
			});
		}

		DropDownPreference themePreference = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__theme));
		int themeIndex = Integer.parseInt(sharedPreferences.getString(getResources().getString(R.string.preferences__theme), "0"));
		final String themeArray[] = getResources().getStringArray(R.array.list_theme);

		if (themeIndex >= themeArray.length) {
			themeIndex = 0;
		}

		oldTheme = themeIndex;

		themePreference.setSummary(themeArray[themeIndex]);
		themePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int newTheme = Integer.parseInt(newValue.toString());

				if (newTheme != oldTheme) {
					ConfigUtils.setAppTheme(newTheme);
					StateBitmapUtil.init(ThreemaApplication.getAppContext());
					preference.setSummary(themeArray[newTheme]);
					ListenerManager.contactSettingsListeners.handle(new ListenerManager.HandleListener<ContactSettingsListener>() {
						@Override
						public void handle(ContactSettingsListener listener) {
							listener.onAvatarSettingChanged();
						}
					});
					ConfigUtils.recreateActivity(getActivity());
				}
				return true;
			}
		});

		DropDownPreference emojiPreference = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__emoji_style));

		int emojiIndex = Integer.parseInt(sharedPreferences.getString(getResources().getString(R.string.preferences__emoji_style), "0"));
		String emojiArray[] = getResources().getStringArray(R.array.list_emoji_style);

		if (emojiIndex >= emojiArray.length) {
			emojiIndex = 0;
		}

		final int oldEmojiStyle = emojiIndex;

		emojiPreference.setSummary(emojiArray[emojiIndex]);

		emojiPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int newEmojiStyle = Integer.parseInt(newValue.toString());

				if (newEmojiStyle != oldEmojiStyle) {
					if (newEmojiStyle == PreferenceService.EmojiStyle_ANDROID) {
						GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.prefs_android_emojis,
								R.string.android_emojis_warning,
								R.string.ok,
								R.string.cancel);

						dialog.setData(newEmojiStyle);
						dialog.setTargetFragment(SettingsAppearanceFragment.this, 0);
						dialog.show(getFragmentManager(), "android_emojis");
					} else {
						ConfigUtils.setEmojiStyle(getActivity(), newEmojiStyle);
						updateEmojiPrefs(newEmojiStyle);
						ConfigUtils.recreateActivity(getActivity());
					}
				}
				return true;
			}
		});

		final String languageArray[] = getResources().getStringArray(R.array.list_language_override);
		DropDownPreference languagePreference = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__language_override));
		final String oldLocale = languagePreference.getValue();
		try {
			languagePreference.setSummary(languageArray[languagePreference.findIndexOfValue(oldLocale)]);
		} catch (Exception e) {
			//
		}
		languagePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				String newLocale = newValue.toString();
				if (newLocale != null && !newLocale.equals(oldLocale)) {
					ConfigUtils.updateLocaleOverride(newValue);
					ConfigUtils.updateAppContextLocale(ThreemaApplication.getAppContext(), newLocale);
					ConfigUtils.recreateActivity(getActivity());
				}
				return true;
			}
		});

		Preference wallpaperPreference = findPreference(getResources().getString(R.string.preferences__wallpaper));
		wallpaperPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				wallpaperService.selectWallpaper(SettingsAppearanceFragment.this, null, null);
				return true;
			}
		});

		DropDownPreference sortingPreference = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__contact_sorting));
		if(sortingPreference != null) {
			sortingPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					//trigger sort change
					ListenerManager.contactSettingsListeners.handle(new ListenerManager.HandleListener<ContactSettingsListener>() {
						@Override
						public void handle(ContactSettingsListener listener) {
							listener.onSortingChanged();
						}
					});
					return true;
				}
			});
		}

		DropDownPreference formatPreference = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__contact_format));
		formatPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				//trigger format name change
				ListenerManager.contactSettingsListeners.handle(new ListenerManager.HandleListener<ContactSettingsListener>() {
					@Override
					public void handle(ContactSettingsListener listener) {
						listener.onNameFormatChanged();
					}
				});
				return true;
			}
		});

		CheckBoxPreference showInactiveContacts = (CheckBoxPreference) findPreference(getResources().getString(R.string.preferences__show_inactive_contacts));

		if (showInactiveContacts != null) {
			showInactiveContacts.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean newCheckedValue = newValue.equals(true);
					if (((CheckBoxPreference) preference).isChecked() != newCheckedValue) {
						ListenerManager.contactSettingsListeners.handle(new ListenerManager.HandleListener<ContactSettingsListener>() {
							@Override
							public void handle(ContactSettingsListener listener) {
								listener.onInactiveContactsSettingChanged();
							}
						});
					}
					return true;
				}
			});
			if (ConfigUtils.isWorkRestricted()) {
				Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__hide_inactive_ids));
				if (value != null) {
					showInactiveContacts.setEnabled(false);
					showInactiveContacts.setSelectable(false);
				}
			}
		}
	}

	final protected boolean requiredInstances() {
		if (!this.checkInstances()) {
			this.instantiate();
		}
		return this.checkInstances();
	}

	protected boolean checkInstances() {
		return TestUtil.required(
				this.fileService,
				this.wallpaperService
		);
	}

	protected void instantiate() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.fileService = serviceManager.getFileService();
				this.wallpaperService = serviceManager.getWallpaperService();
			} catch (FileSystemNotPresentException e) {
				logger.error("Exception", e);
			}
		}
	}

	private void updateEmojiPrefs(int newEmojiStyle) {
		DropDownPreference preference = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__emoji_style));
		preference.setValueIndex(newEmojiStyle);
		preference.setSummary(getResources().getStringArray(R.array.list_emoji_style)[newEmojiStyle]);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		preferenceFragmentCallbackInterface.setToolbarTitle(R.string.prefs_header_appearance);
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onYes(String tag, Object data) {
		ConfigUtils.setEmojiStyle(getActivity(), (int) data);
		updateEmojiPrefs((int) data);
		ConfigUtils.recreateActivity(getActivity());
	}

	@Override
	public void onNo(String tag, Object data) {
		updateEmojiPrefs(PreferenceService.EmojiStyle_DEFAULT);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		wallpaperService.handleActivityResult(this, requestCode, resultCode, data, null);

		super.onActivityResult(requestCode, resultCode, data);
	}
}
