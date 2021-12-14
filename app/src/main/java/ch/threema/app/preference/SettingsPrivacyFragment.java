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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.TwoStatePreference;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.BlackListActivity;
import ch.threema.app.activities.ExcludedSyncIdentitiesActivity;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.exceptions.FileSystemNotPresentException;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ShortcutService;
import ch.threema.app.services.SynchronizeContactsService;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.SynchronizeContactsUtil;
import ch.threema.base.ThreemaException;
import ch.threema.localcrypto.MasterKeyLockedException;

public class SettingsPrivacyFragment extends ThreemaPreferenceFragment implements CancelableHorizontalProgressDialog.ProgressDialogClickListener, GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(SettingsPrivacyFragment.class);

	private static final String DIALOG_TAG_VALIDATE = "vali";
	private static final String DIALOG_TAG_SYNC_CONTACTS = "syncC";
	private static final String DIALOG_TAG_DISABLE_SYNC = "dissync";
	private static final String DIALOG_TAG_RESET_RECEIPTS = "rece";

	private static final int PERMISSION_REQUEST_CONTACTS = 1;

	private ServiceManager serviceManager = ThreemaApplication.getServiceManager();
	private SynchronizeContactsService synchronizeContactsService;
	private TwoStatePreference contactSyncPreference;
	private CheckBoxPreference disableScreenshot;
	private boolean disableScreenshotChecked = false;
	private View fragmentView;

	private final SynchronizeContactsListener synchronizeContactsListener = new SynchronizeContactsListener() {
		@Override
		public void onStarted(SynchronizeContactsRoutine startedRoutine) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateView();
					GenericProgressDialog.newInstance(R.string.wizard1_sync_contacts, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_SYNC_CONTACTS);
				}
			});
		}

		@Override
		public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateView();
					if(SettingsPrivacyFragment.this.isAdded()) {
						DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_SYNC_CONTACTS, true);
					}
				}
			});
		}

		@Override
		public void onError(SynchronizeContactsRoutine finishedRoutine) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateView();
					if(SettingsPrivacyFragment.this.isAdded()) {
						DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_SYNC_CONTACTS, true);
					}
				}
			});
		}
	};

	@Override
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.preference_privacy);

		try {
			if(this.requireInstances()) {
				this.synchronizeContactsService = this.serviceManager.getSynchronizeContactsService();
			}
		} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
			logger.error("Exception", e);
		}

		this.disableScreenshot = findPreference(getString(R.string.preferences__hide_screenshots));
		this.disableScreenshotChecked = this.disableScreenshot.isChecked();

		this.contactSyncPreference = findPreference(getResources().getString(R.string.preferences__sync_contacts));
		this.contactSyncPreference.setSummaryOn(getString(R.string.prefs_sum_sync_contacts_on, getString(R.string.app_name)));
		this.contactSyncPreference.setSummaryOff(getString(R.string.prefs_sum_sync_contacts_off, getString(R.string.app_name)));

		CheckBoxPreference blockUnknown = findPreference(getString(R.string.preferences__block_unknown));

		if (SynchronizeContactsUtil.isRestrictedProfile(getActivity())) {
			// restricted android profile (e.g. guest user)
			this.contactSyncPreference.setChecked(false);
			this.contactSyncPreference.setEnabled(false);
			this.contactSyncPreference.setSelectable(false);
		} else {
			this.contactSyncPreference.setOnPreferenceChangeListener((preference, newValue) -> {
				boolean newCheckedValue = newValue.equals(true);
				if (((TwoStatePreference) preference).isChecked() != newCheckedValue) {
					if (newCheckedValue) {
						enableSync();
					} else {
						disableSync();
					}
				}
				//always return true, fix samsung preferences handler
				return true;
			});
		}

		if (ConfigUtils.isWorkRestricted()) {
			Boolean value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__block_unknown));
			if (value != null) {
				blockUnknown.setEnabled(false);
				blockUnknown.setSelectable(false);
			}
			value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_screenshots));
			if (value != null) {
				this.disableScreenshot.setEnabled(false);
				this.disableScreenshot.setSelectable(false);
			}
			value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__contact_sync));
			if (value != null) {
				this.contactSyncPreference.setEnabled(false);
				this.contactSyncPreference.setSelectable(false);
			}
		}

		if (ConfigUtils.getScreenshotsDisabled(ThreemaApplication.getServiceManager().getPreferenceService(),
						ThreemaApplication.getServiceManager().getLockAppService())) {
			this.disableScreenshot.setEnabled(false);
			this.disableScreenshot.setSelectable(false);
		}

		findPreference("pref_excluded_sync_identities").setOnPreferenceClickListener(preference -> {
			startActivity(new Intent(getActivity(), ExcludedSyncIdentitiesActivity.class));
			return false;
		});

		findPreference("pref_black_list").setOnPreferenceClickListener(preference -> {
			startActivity(new Intent(getActivity(), BlackListActivity.class));
			return false;
		});

		findPreference("pref_reset_receipts").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.prefs_title_reset_receipts, getString(R.string.prefs_sum_reset_receipts) + "?", R.string.yes, R.string.no);
				dialog.setTargetFragment(SettingsPrivacyFragment.this);
				dialog.show(getFragmentManager(), DIALOG_TAG_RESET_RECEIPTS);
				return false;
			}
		});

		Preference directSharePreference = findPreference(getResources().getString(R.string.preferences__direct_share));
		if (directSharePreference != null) {
			directSharePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					boolean newCheckedValue = newValue.equals(true);
					if (((TwoStatePreference) preference).isChecked() != newCheckedValue) {
						ShortcutService shortcutService = null;
						try {
							shortcutService = serviceManager.getShortcutService();
						} catch (ThreemaException e) {
							logger.error("Exception, could not update or delete shortcuts upon changing direct share setting", e);
							return false;
						}
						if (newCheckedValue) {
							shortcutService.publishRecentChatsAsSharingTargets();
						} else {
							shortcutService.deleteDynamicShortcuts();
						}
					}
					return true;
				}
			});
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
			PreferenceCategory preferenceCategory = findPreference("pref_key_other");
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
				preferenceCategory.removePreference(findPreference(getResources().getString(R.string.preferences__direct_share)));
			}
			preferenceCategory.removePreference(findPreference(getResources().getString(R.string.preferences__disable_smart_replies)));
		}

		this.updateView();
	}

	private void updateView() {
		if(this.synchronizeContactsService.isSynchronizationInProgress()) {
			//disable switcher
			if(this.contactSyncPreference != null) {
				this.contactSyncPreference.setEnabled(false);
			}
		}
		else {
			if(this.contactSyncPreference != null) {
				this.contactSyncPreference.setEnabled(true);
			}
		}
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ListenerManager.synchronizeContactsListeners.add(this.synchronizeContactsListener);
		return super.onCreateView(inflater, container, savedInstanceState);
	}

	@Override
	public void onDestroyView() {
		ListenerManager.synchronizeContactsListeners.remove(this.synchronizeContactsListener);

		if (isAdded()) {
			DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_VALIDATE, true);
			DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_SYNC_CONTACTS, true);
			DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_DISABLE_SYNC, true);
		}

		super.onDestroyView();
	}

	private boolean requireInstances() {
		if(this.serviceManager != null) {
			return true;
		}

		this.serviceManager = ThreemaApplication.getServiceManager();
		return this.serviceManager != null;
	}

	private void enableSync() {
		if(this.requireInstances()) {
			SynchronizeContactsService synchronizeContactsService;
			try {
				synchronizeContactsService = this.serviceManager.getSynchronizeContactsService();
				if(synchronizeContactsService != null) {
					if(synchronizeContactsService.enableSync()) {
						if (ConfigUtils.requestContactPermissions(getActivity(), SettingsPrivacyFragment.this, PERMISSION_REQUEST_CONTACTS)) {
							launchContactsSync();
						}
					}
				}
			} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
				logger.error("Exception", e);
			}
		}

	}

	private void launchContactsSync() {
		//start a Sync
		synchronizeContactsService.instantiateSynchronizationAndRun();
	}

	private void disableSync() {
		if(this.requireInstances()) {
			final SynchronizeContactsService synchronizeContactsService;
			try {
				synchronizeContactsService = this.serviceManager.getSynchronizeContactsService();
			} catch (MasterKeyLockedException | FileSystemNotPresentException e) {
				logger.error("Exception", e);
				return;
			}

			GenericProgressDialog.newInstance(R.string.app_name, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_DISABLE_SYNC);

				new Thread(new Runnable() {
					@Override
					public void run() {
							if(synchronizeContactsService != null) {
								synchronizeContactsService.disableSync(new Runnable() {
									@Override
									public void run() {
										RuntimeUtil.runOnUiThread(new Runnable() {
											@Override
											public void run() {
												DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_DISABLE_SYNC, true);
												contactSyncPreference.setChecked(false);
											}
										});
									}
								});
							}
					}
				}).start();
		}

	}

	private void resetReceipts() {
		new Thread(() -> {
			try {
				ContactService contactService = ThreemaApplication.getServiceManager().getContactService();
				contactService.resetReceiptsSettings();
				RuntimeUtil.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getContext(), R.string.reset_successful, Toast.LENGTH_SHORT).show();
					}
				});
			} catch (Exception e) {
				logger.error("ContactService not available", e);
				Toast.makeText(getContext(), R.string.an_error_occurred, Toast.LENGTH_SHORT).show();
			}
		}, "ResetReceiptSettings").start();
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		this.fragmentView = view;

		preferenceFragmentCallbackInterface.setToolbarTitle(R.string.prefs_privacy);
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onDetach() {
		super.onDetach();

		if (this.disableScreenshot.isChecked() != this.disableScreenshotChecked) {
			ConfigUtils.recreateActivity(getActivity());
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_CONTACTS:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					launchContactsSync();
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
					disableSync();
					ConfigUtils.showPermissionRationale(getContext(), fragmentView, R.string.permission_contacts_sync_required, new BaseTransientBottomBar.BaseCallback<Snackbar>() {
						@Override
						public void onDismissed(Snackbar transientBottomBar, int event) {
							super.onDismissed(transientBottomBar, event);
						}
					});
				} else {
					disableSync();
				}
				break;
		}
	}

	@Override
	public void onCancel(String tag, Object object) { }

	@Override
	public void onYes(String tag, Object data) {
		resetReceipts();
	}

	@Override
	public void onNo(String tag, Object data) {	}
}
