/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;
import ch.threema.app.BuildConfig;
import ch.threema.app.FcmRegistrationIntentService;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.FileService;
import ch.threema.app.services.LifetimeService;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.MessageServiceImpl;
import ch.threema.app.services.NotificationService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.UserService;
import ch.threema.app.services.WallpaperService;
import ch.threema.app.ui.MediaItem;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.PowermanagerUtil;
import ch.threema.app.utils.PushUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.voip.activities.WebRTCDebugActivity;
import ch.threema.app.webclient.activities.WebDiagnosticsActivity;
import ch.threema.logging.backend.DebugLogFileBackend;
import ch.threema.storage.models.ContactModel;

import static ch.threema.app.utils.PowermanagerUtil.RESULT_DISABLE_AUTOSTART;
import static ch.threema.app.utils.PowermanagerUtil.RESULT_DISABLE_POWERMANAGER;

public class SettingsTroubleshootingFragment extends ThreemaPreferenceFragment implements GenericAlertDialog.DialogClickListener, SharedPreferences.OnSharedPreferenceChangeListener, TextEntryDialog.TextEntryDialogClickListener, CancelableHorizontalProgressDialog.ProgressDialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(SettingsTroubleshootingFragment.class);

	private static final String DIALOG_TAG_REMOVE_WALLPAPERS = "removeWP";
	private static final String DIALOG_TAG_GCM_REGISTER = "gcmReg";
	private static final String DIALOG_TAG_GCM_RESULT = "gcmRes";
	private static final String DIALOG_TAG_RESET_RINGTONES = "rri";
	private static final String DIALOG_TAG_IPV6_APP_RESTART = "rs";
	private static final String DIALOG_TAG_POWERMANAGER_WORKAROUNDS = "hw";
	private static final String DIALOG_TAG_AUTOSTART_WORKAROUNDS = "as";
	private static final String DIALOG_TAG_REALLY_ENABLE_POLLING = "enp";

	public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 441;
	public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS_HUAWEI = 442;
	private static final String DIALOG_TAG_SENDLOG = "sl";

	public static final String THREEMA_SUPPORT_IDENTITY = "*SUPPORT";

	private static final int PERMISSION_REQUEST_MESSAGE_LOG = 1;
	private static final int PERMISSION_REQUEST_SEND_LOG = 2;

	private TwoStatePreference pollingTwoStatePreference;
	private TwoStatePreference messageLogPreference, ipv6Preferences;

	private WallpaperService wallpaperService;
	private SharedPreferences sharedPreferences;
	private PreferenceService preferenceService;
	private RingtoneService ringtoneService;
	private NotificationService notificationService;
	private FileService fileService;
	private UserService userService;
	private LifetimeService lifetimeService;
	private DeadlineListService mutedChatsListService, mentionOnlyChatsListService;
	private MessageService messageService;
	private ContactService contactService;

	private BroadcastReceiver gcmRegisterBroadcastReceiver;
	private View fragmentView;

	private boolean isPlayServicesInstalled;

	@Override
	public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {

		if (!requiredInstances()) {
			return;
		}

		addPreferencesFromResource(R.xml.preference_troubleshooting);
		PreferenceScreen preferenceScreen = (PreferenceScreen) findPreference("pref_key_troubleshooting");

		sharedPreferences = getPreferenceManager().getSharedPreferences();

		gcmRegisterBroadcastReceiver = new BroadcastReceiver() {
			// register listener for gcm registration result
			@Override
			public void onReceive(Context context, Intent intent) {
				DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_GCM_REGISTER, true);
				boolean sentToken = !PushUtil.pushTokenNeedsRefresh(context);

				SimpleStringAlertDialog.newInstance(-1, sentToken ?
						(intent.getBooleanExtra(FcmRegistrationIntentService.EXTRA_CLEAR_TOKEN, false) ?
								getString(R.string.push_token_cleared) :
								getString(R.string.push_reset_text)) :
						getString(R.string.gcm_register_failed)).show(getFragmentManager(), DIALOG_TAG_GCM_RESULT);
			}
		};

		isPlayServicesInstalled = ConfigUtils.isPlayServicesInstalled(getContext());

		pollingTwoStatePreference = (TwoStatePreference) findPreference(getResources().getString(R.string.preferences__polling_switch));
		pollingTwoStatePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean newCheckedValue = newValue.equals(true);
				if (((TwoStatePreference) preference).isChecked() != newCheckedValue) {
					if (newCheckedValue) {
						if (isPlayServicesInstalled) {
							GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.enable_polling, R.string.push_disable_text, R.string.continue_anyway, R.string.cancel);
							dialog.setTargetFragment(SettingsTroubleshootingFragment.this, 0);
							dialog.show(getFragmentManager(), DIALOG_TAG_REALLY_ENABLE_POLLING);
							return false;
						}
						updatePollInterval();
						return true;
					} else {
						if (isPlayServicesInstalled) {
							lifetimeService.setPollingInterval(0);
						} else {
							Toast.makeText(getContext(), R.string.play_services_not_installed_unable_to_use_push, Toast.LENGTH_SHORT).show();
							return false;
						}
					}
				}
				return true;
			}
		});

		DropDownPreference pollingIntervalPreference = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__polling_interval));
		pollingIntervalPreference.setEnabled(pollingTwoStatePreference.isChecked());

		messageLogPreference = (TwoStatePreference) findPreference(getResources().getString(R.string.preferences__message_log_switch));
		messageLogPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				boolean newCheckedValue = newValue.equals(true);

				if (newCheckedValue) {
					if (!ConfigUtils.requestStoragePermissions(getActivity(), SettingsTroubleshootingFragment.this, PERMISSION_REQUEST_MESSAGE_LOG)) {
						return false;
					}
				}
				DebugLogFileBackend.setEnabled(newCheckedValue);

				return true;
			}
		});

		Preference sendLogPreference = findPreference(getResources().getString(R.string.preferences__sendlog));
		sendLogPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (ConfigUtils.requestStoragePermissions(getActivity(), SettingsTroubleshootingFragment.this, PERMISSION_REQUEST_SEND_LOG)) {
					prepareSendLogfile();
				}
				return true;
			}
		});

		Preference resetPushPreference = findPreference(getResources().getString(R.string.preferences__reset_push));
		resetPushPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (ConfigUtils.isPlayServicesInstalled(getActivity())) {
					PushUtil.clearPushTokenSentDate(getActivity());
					PushUtil.sendPushTokenToServer(getContext(), false, true);
					GenericProgressDialog.newInstance(R.string.push_reset_title, R.string.please_wait).showNow(getFragmentManager(), DIALOG_TAG_GCM_REGISTER);
				}
				return true;
			}
		});

		Preference wallpaperDeletePreferences = findPreference(getResources().getString(R.string.preferences__remove_wallpapers));
		wallpaperDeletePreferences.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.prefs_title_remove_wallpapers,
						R.string.really_remove_wallpapers,
						R.string.ok,
						R.string.cancel);

				dialog.setTargetFragment(SettingsTroubleshootingFragment.this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_REMOVE_WALLPAPERS);
				return false;
			}
		});

		Preference ringtoneResetPreferences = findPreference(getResources().getString(R.string.preferences__reset_ringtones));
		ringtoneResetPreferences.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.prefs_title_reset_ringtones,
						R.string.really_reset_ringtones,
						R.string.ok,
						R.string.cancel);

				dialog.setTargetFragment(SettingsTroubleshootingFragment.this, 0);
				dialog.show(getFragmentManager(), DIALOG_TAG_RESET_RINGTONES);
				return false;
			}
		});

		ipv6Preferences = (TwoStatePreference) findPreference(getResources().getString(R.string.preferences__ipv6_preferred));

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			// disable IPv6 support on Android <5.0 due to some know incompatibilities
			ipv6Preferences.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				public boolean onPreferenceChange(Preference preference, Object newValue) {

					boolean newCheckedValue = newValue.equals(true);
					boolean oldCheckedValue = ((TwoStatePreference) preference).isChecked();
					if (oldCheckedValue != newCheckedValue) {
						// value has changed
						GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.prefs_title_ipv6_preferred,
								R.string.ipv6_requires_restart,
								R.string.ipv6_restart_now,
								R.string.cancel);

						dialog.setTargetFragment(SettingsTroubleshootingFragment.this, 0);
						dialog.setData(oldCheckedValue);
						dialog.show(getFragmentManager(), DIALOG_TAG_IPV6_APP_RESTART);
						return false;
					}
					return true;
				}
			});
		} else {
			PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("pref_key_network");
			preferenceScreen.removePreference(preferenceCategory);
		}

		Preference powerManagerPrefs = findPreference(getResources().getString(R.string.preferences__powermanager_workarounds));
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			powerManagerPrefs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					if (PowermanagerUtil.hasPowerManagerOption(SettingsTroubleshootingFragment.this.getActivity())) {
						GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.disable_powermanager_title,
								String.format(getString(R.string.disable_powermanager_explain), getString(R.string.app_name)),
								R.string.next,
								R.string.cancel);

						dialog.setTargetFragment(SettingsTroubleshootingFragment.this, 0);
						dialog.show(getFragmentManager(), DIALOG_TAG_POWERMANAGER_WORKAROUNDS);
					} else {
						disableAutostart();
					}
					return true;
				}
			});

			Preference backgroundDataPrefs = findPreference(getResources().getString(R.string.preferences__background_data));
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				backgroundDataPrefs.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
					@TargetApi(Build.VERSION_CODES.N)
					@Override
					public boolean onPreferenceClick(Preference preference) {
						Intent intent = new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS);
						intent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));

						try {
							startActivity(intent);
						} catch (ActivityNotFoundException e) {
							// safety net for incomplete android implementations
						}
						return true;
					}
				});
			} else {
				PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("pref_key_fix_device");
				preferenceCategory.removePreference(backgroundDataPrefs);
			}

			updatePowerManagerPrefs();
		} else {
			PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("pref_key_fix_device");
			preferenceScreen.removePreference(preferenceCategory);
		}

		DropDownPreference echoCancelPreference = (DropDownPreference) findPreference(getResources().getString(R.string.preferences__voip_echocancel));
		int echoCancelIndex = preferenceService.getAECMode().equals("sw") ? 1 : 0;
		final String echoCancelArray[] = getResources().getStringArray(R.array.list_echocancel);
		final List<String> echoCancelValuesArrayList = Arrays.asList(getResources().getStringArray(R.array.list_echocancel_values));

		echoCancelPreference.setSummary(echoCancelArray[echoCancelIndex]);
		echoCancelPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				preference.setSummary(echoCancelArray[echoCancelValuesArrayList.indexOf(newValue.toString())]);
				return true;
			}
		});

		final Preference webrtcDebugPreference = findPreference(getResources().getString(R.string.preferences__webrtc_debug));
		webrtcDebugPreference.setOnPreferenceClickListener(preference -> {
			Intent intent = new Intent(getActivity(), WebRTCDebugActivity.class);
			getActivity().startActivity(intent);
			return true;
		});

		final Preference webclientDebugPreference = findPreference(getResources().getString(R.string.preferences__webclient_debug));
		webclientDebugPreference.setOnPreferenceClickListener(preference -> {
			Intent intent = new Intent(getActivity(), WebDiagnosticsActivity.class);
			getActivity().startActivity(intent);
			return true;
		});

		if (ConfigUtils.isWorkRestricted() || ConfigUtils.isBlackBerry()) {
			Boolean value;
			if (ConfigUtils.isBlackBerry()) {
				value = true;
			} else {
				value = AppRestrictionUtil.getBooleanRestriction(getString(R.string.restriction__disable_calls));
			}

			if (value != null) {
				PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("pref_key_voip");
				if (preferenceCategory != null) {
					preferenceScreen.removePreference(preferenceCategory);
				}
			}
		}

		if (ConfigUtils.isWorkRestricted()) {
			// remove everything except push reset
			PreferenceCategory preferenceCategory = (PreferenceCategory) findPreference("pref_key_workarounds");
			findPreference(getResources().getString(R.string.preferences__reset_push)).setDependency(null);

			preferenceCategory.removePreference(findPreference(getResources().getString(R.string.preferences__polling_switch)));
			preferenceCategory.removePreference(findPreference(getResources().getString(R.string.preferences__polling_interval)));
		}
	}

	private void updatePowerManagerPrefs() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			findPreference(getResources().getString(R.string.preferences__powermanager_workarounds)).setEnabled(PowermanagerUtil.needsFixing(getActivity()));
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
				this.wallpaperService,
				this.lifetimeService,
				this.preferenceService,
				this.fileService,
				this.userService,
				this.ringtoneService,
				this.mutedChatsListService,
				this.messageService,
				this.contactService
		);
	}

	protected void instantiate() {
		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.wallpaperService = serviceManager.getWallpaperService();
				this.lifetimeService = serviceManager.getLifetimeService();
				this.preferenceService = serviceManager.getPreferenceService();
				this.fileService = serviceManager.getFileService();
				this.userService = serviceManager.getUserService();
				this.ringtoneService = serviceManager.getRingtoneService();
				this.mutedChatsListService = serviceManager.getMutedChatsListService();
				this.mentionOnlyChatsListService = serviceManager.getMentionOnlyChatsListService();
				this.messageService = serviceManager.getMessageService();
				this.contactService = serviceManager.getContactService();
				this.notificationService = serviceManager.getNotificationService();
			} catch (Exception e) {
				logger.error("Exception", e);
			}
		}
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		this.fragmentView = view;
		preferenceFragmentCallbackInterface.setToolbarTitle(R.string.prefs_troubleshooting);
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onYes(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_REMOVE_WALLPAPERS:
				wallpaperService.removeAll(getActivity(), false);
				preferenceService.setCustomWallpaperEnabled(false);
				break;
			case DIALOG_TAG_RESET_RINGTONES:
				ringtoneService.resetRingtones(getActivity().getApplicationContext());
				mutedChatsListService.clear();
				mentionOnlyChatsListService.clear();
				notificationService.deleteNotificationChannels();
				notificationService.createNotificationChannels();
				if (ConfigUtils.isWorkBuild()) {
					preferenceService.setAfterWorkDNDEnabled(false);
				}
				Toast.makeText(getActivity().getApplicationContext(), getString(R.string.reset_ringtones_confirm), Toast.LENGTH_SHORT).show();
				ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
					@Override
					public void handle(ConversationListener listener) {
						listener.onModifiedAll();
					}
				});
				break;
			case DIALOG_TAG_IPV6_APP_RESTART:
				ipv6Preferences.setChecked(!(boolean) data);
				new Handler().postDelayed(() -> RuntimeUtil.runOnUiThread(() -> System.exit(0)), 700);
				break;
			case DIALOG_TAG_AUTOSTART_WORKAROUNDS:
				PowermanagerUtil.callAutostartManager(this);
				break;
			case DIALOG_TAG_POWERMANAGER_WORKAROUNDS:
				PowermanagerUtil.callPowerManager(this);
				break;
			case DIALOG_TAG_REALLY_ENABLE_POLLING:
				requestDisableBatteryOptimizations(getString(R.string.prefs_title_polling_switch), R.string.cancel, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS);
				break;
		}
	}

	private void disableAutostart() {
		if (PowermanagerUtil.hasAutostartOption(getActivity())) {
			GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.disable_autostart_title,
				String.format(getString(R.string.disable_autostart_explain), getString(R.string.app_name)),
				R.string.next,
				R.string.cancel);

			dialog.setTargetFragment(SettingsTroubleshootingFragment.this, 0);
			dialog.show(getFragmentManager(), DIALOG_TAG_AUTOSTART_WORKAROUNDS);
		} else {
			requestDisableBatteryOptimizations(getString(R.string.app_name), R.string.cancel, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS_HUAWEI);
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		sharedPreferences.registerOnSharedPreferenceChangeListener(this);
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(gcmRegisterBroadcastReceiver,
				new IntentFilter(ThreemaApplication.INTENT_GCM_REGISTRATION_COMPLETE));
	}

	@Override
	public void onStop() {
		sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(gcmRegisterBroadcastReceiver);

		DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_GCM_REGISTER, true);

		super.onStop();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getString(R.string.preferences__polling_switch))) {
			boolean newValue = sharedPreferences.getBoolean(getString(R.string.preferences__polling_switch), false);

			Preference preference = findPreference(getString(R.string.preferences__polling_interval));
			preference.setEnabled(newValue);

			if (!isAdded()) {
				return;
			}

			if (isPlayServicesInstalled) {
				PushUtil.sendPushTokenToServer(getContext(), newValue, true);
				GenericProgressDialog.newInstance(-1, R.string.please_wait).showNow(getFragmentManager(), DIALOG_TAG_GCM_REGISTER);
			} else {
				if (newValue) { // polling enabled
					PushUtil.sendPushTokenToServer(getContext(), true, false);
				}
			}
		} else if (key.equals(getString(R.string.preferences__polling_interval))) {
			updatePollInterval();
		}
	}

	private void prepareSendLogfile() {
		TextEntryDialog dialog = TextEntryDialog.newInstance(R.string.prefs_sendlog_summary,
				R.string.enter_description,
				R.string.send,
				R.string.cancel,
				5,
				3000,
				1);
		dialog.setTargetFragment(this, 0);
		dialog.show(getFragmentManager(), DIALOG_TAG_SENDLOG);
	}


	@SuppressLint("StaticFieldLeak")
	public void sendLogFileToSupport(final String caption) {
		new AsyncTask<Void, Void, Exception>() {

			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.preparing_messages, R.string.please_wait).show(getFragmentManager(), DIALOG_TAG_SENDLOG);
			}

			@Override
			protected Exception doInBackground(Void... params) {
				File zipFile = DebugLogFileBackend.getZipFile(fileService);

				try {
					final ContactModel contactModel = contactService.getOrCreateByIdentity(
						THREEMA_SUPPORT_IDENTITY, true);

					MessageReceiver receiver = contactService.createReceiver(contactModel);

					messageService.sendText(caption +
							"\n-- \n" +
							ConfigUtils.getDeviceInfo(getActivity(), false) + "\n" +
							ConfigUtils.getFullAppVersion(getActivity()) + "\n" +
							userService.getIdentity(), receiver);

					MediaItem mediaItem = new MediaItem(Uri.fromFile(zipFile), MediaItem.TYPE_NONE);
					mediaItem.setFilename(zipFile.getName());
					mediaItem.setMimeType(MimeUtil.MIME_TYPE_ZIP);

					messageService.sendMediaAsync(Collections.singletonList(mediaItem),
						Collections.singletonList(receiver), new MessageServiceImpl.SendResultListener() {
							@Override
							public void onError(String errorMessage) {
								RuntimeUtil.runOnUiThread(() -> Toast.makeText(getContext(), R.string.an_error_occurred_during_send, Toast.LENGTH_LONG).show());
							}

							@Override
							public void onCompleted() {
								RuntimeUtil.runOnUiThread(() -> Toast.makeText(getContext(), R.string.message_sent, Toast.LENGTH_LONG).show());
							}
						});
				} catch (Exception e) {
					return e;
				}
				return null;
			}

			@Override
			protected void onPostExecute(Exception exception) {
				if (isAdded()) {
					DialogUtil.dismissDialog(getFragmentManager(), DIALOG_TAG_SENDLOG, true);

					if (exception != null) {
						Toast.makeText(getActivity().getApplicationContext(), R.string.an_error_occurred, Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(getActivity().getApplicationContext(), R.string.message_sent, Toast.LENGTH_LONG).show();
					}
				}
			}
		}.execute();
	}

	private void updatePollInterval() {
		lifetimeService.setPollingInterval(preferenceService.getPollingInterval());
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
				if (resultCode == Activity.RESULT_OK) {
					pollingTwoStatePreference.setChecked(true);
					updatePollInterval();
				} else {
					pollingTwoStatePreference.setChecked(false);
				}
				break;
			case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS_HUAWEI:
				updatePowerManagerPrefs();
				break;
			case RESULT_DISABLE_POWERMANAGER:
				disableAutostart();
				updatePowerManagerPrefs();
				break;
			case RESULT_DISABLE_AUTOSTART:
				requestDisableBatteryOptimizations(getString(R.string.app_name), R.string.cancel, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS_HUAWEI);
				updatePowerManagerPrefs();
				break;
			default:
				super.onActivityResult(requestCode, resultCode, data);
				break;
		}
	}

	private void requestDisableBatteryOptimizations(String name, int label, int requestId) {
		Intent intent = new Intent(getActivity(), DisableBatteryOptimizationsActivity.class);
		intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_NAME, name);
		intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_CANCEL_LABEL, label);
		startActivityForResult(intent, requestId);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
	                                       @NonNull String permissions[], @NonNull int[] grantResults) {
		boolean result = (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);

		if (!result) {
			if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				ConfigUtils.showPermissionRationale(getContext(), fragmentView,  R.string.permission_storage_required);
			}
		}
		switch (requestCode) {
			case PERMISSION_REQUEST_MESSAGE_LOG:
				DebugLogFileBackend.setEnabled(result);
				messageLogPreference.setChecked(result);
				break;
			case PERMISSION_REQUEST_SEND_LOG:
				if (result) {
					prepareSendLogfile();
				}
				break;
		}
	}

	@Override
	public void onYes(String tag, String text) {
		sendLogFileToSupport(text);
	}

	@Override
	public void onNo(String tag) {
	}

	@Override
	public void onNo(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_IPV6_APP_RESTART:
				boolean oldValue = (boolean) data;
				ipv6Preferences.setChecked(oldValue);
				break;
			default:
				break;
		}
	}

	@Override
	public void onNeutral(String tag) {
	}

	@Override
	public void onCancel(String tag, Object object) {
	}
}
