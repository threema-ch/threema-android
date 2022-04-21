/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2022 Threema GmbH
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import java.security.MessageDigest;
import java.util.Arrays;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.DialogFragment;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.UnlockMasterKeyActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.listeners.ConversationListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.PassphraseService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.BiometricUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.services.PreferenceService.LockingMech_NONE;

public class SettingsSecurityFragment extends ThreemaPreferenceFragment implements PasswordEntryDialog.PasswordEntryDialogClickListener, GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("SettingsSecurityFragment");

	private Preference pinPreference;
	private TwoStatePreference uiLockSwitchPreference;
	private DropDownPreference gracePreference, lockMechanismPreference;
	private Preference masterkeyPreference;
	private TwoStatePreference masterkeySwitchPreference;
	private PreferenceService preferenceService;
	private DeadlineListService hiddenChatsListService;

	private View fragmentView;

	private static final String ID_DIALOG_PASSPHRASE = "mkpw";
	private static final String ID_DIALOG_PROGRESS = "pogress";
	private static final String ID_DIALOG_PIN = "dpin";
	private static final String ID_UNHIDE_CHATS_CONFIRM = "uh";
	private static final String DIALOG_TAG_PASSWORD_REMINDER_PIN = "emin";
	private static final String DIALOG_TAG_PASSWORD_REMINDER_PASSPHRASE = "eminpass";

	private static final int ID_ENABLE_SYSTEM_LOCK = 7780;

	@Override
	protected void initializePreferences() {
		logger.debug("### initializePreferences");

		super.initializePreferences();

		preferenceService = requirePreferenceService();
		hiddenChatsListService = requireHiddenChatListService();
	}

	private void onCreateUnlocked() {
		logger.debug("### onCreateUnlocked");
		fragmentView.setVisibility(View.VISIBLE);

		uiLockSwitchPreference = findPreference(getResources().getString(R.string.preferences__lock_ui_switch));
		lockMechanismPreference = findPreference(getResources().getString(R.string.preferences__lock_mechanism));
		pinPreference = findPreference(getResources().getString(R.string.preferences__pin_lock_code));
		gracePreference = findPreference(getResources().getString(R.string.preferences__pin_lock_grace_time));

		//get pin switch pref from service!
		uiLockSwitchPreference.setChecked(preferenceService.isAppLockEnabled());

		CharSequence[] entries = lockMechanismPreference.getEntries();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ConfigUtils.isBlackBerry()) {
			// remove system screen lock option
			lockMechanismPreference.setEntries(Arrays.copyOf(entries, 2));
		} else {
			if (preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_BIOMETRIC)) {
				if (!BiometricUtil.isHardwareSupported(getContext())) {
					preferenceService.setLockMechanism(LockingMech_NONE);
					// remove biometric option
					lockMechanismPreference.setEntries(Arrays.copyOf(entries, 3));
				}
			} else {
				if (!BiometricUtil.isHardwareSupported(getContext())) {
					// remove biometric option
					lockMechanismPreference.setEntries(Arrays.copyOf(entries, 3));
				}
			}
		}

		if (preferenceService.isPinSet()) {
			pinPreference.setSummary(getString(R.string.click_here_to_change_pin));
		}

		switch (preferenceService.getLockMechanism()) {
			case PreferenceService.LockingMech_PIN:
				lockMechanismPreference.setValueIndex(1);
				break;
			case PreferenceService.LockingMech_SYSTEM:
				lockMechanismPreference.setValueIndex(2);
				break;
			case PreferenceService.LockingMech_BIOMETRIC:
				lockMechanismPreference.setValueIndex(3);
				break;
			default:
				lockMechanismPreference.setValueIndex(0);
		}

		updateLockPreferences();

		setGraceTime();

		lockMechanismPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
				switch ((String) newValue) {
					case LockingMech_NONE:
						if (hiddenChatsListService.getSize() > 0) {
							GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.hide_chat, R.string.unhide_chats_confirm, R.string.continue_anyway, R.string.cancel);
							dialog.setTargetFragment(SettingsSecurityFragment.this, 0);
							dialog.show(getFragmentManager(), ID_UNHIDE_CHATS_CONFIRM);
						} else {
							removeAccessProtection();
						}
						break;
					case PreferenceService.LockingMech_PIN:
						GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.warning, getString(R.string.password_remember_warning, getString(R.string.app_name)), R.string.ok, R.string.cancel);
						dialog.setTargetFragment(SettingsSecurityFragment.this, 0);
						dialog.show(getParentFragmentManager(), DIALOG_TAG_PASSWORD_REMINDER_PIN);
						break;
					case PreferenceService.LockingMech_SYSTEM:
						setSystemScreenLock();
						break;
					case PreferenceService.LockingMech_BIOMETRIC:
						setBiometricLock();
				}

				updateLockPreferences();
				return false;
			}
		});

		uiLockSwitchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
				boolean newCheckedValue = newValue.equals(true);

				if (((TwoStatePreference) preference).isChecked() != newCheckedValue) {
					preferenceService.setAppLockEnabled(false);

					if (newCheckedValue) {
						if (lockMechanismPreference.getValue() == null || PreferenceService.LockingMech_NONE.equals(lockMechanismPreference.getValue())) {
							return false;
						}

						setGraceTime();
						preferenceService.setAppLockEnabled(true);
					}
				}
				return true;
			}
		});

		pinPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(@NonNull Preference preference) {
				if (preference.getKey().equals(getResources().getString(R.string.preferences__pin_lock_code))) {
					if (preferenceService.isPinSet()) {
						setPin();
					}
				}
				return false;
			}
		});

		this.updateGracePreferenceSummary(gracePreference.getValue());
		gracePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
				updateGracePreferenceSummary(newValue.toString());
				return true;
			}
		});

		masterkeyPreference = getPref(getResources().getString(R.string.preferences__masterkey_passphrase));
		masterkeyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(@NonNull Preference preference) {
				if (MessageDigest.isEqual(preference.getKey().getBytes(), getResources().getString(R.string.preferences__masterkey_passphrase).getBytes())) {
					Intent intent = new Intent(getActivity(), UnlockMasterKeyActivity.class);
					intent.putExtra(ThreemaApplication.INTENT_DATA_PASSPHRASE_CHECK, true);
					startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_CHANGE_PASSPHRASE_UNLOCK);

				}
				return false;
			}
		});

		masterkeySwitchPreference = findPreference(getResources().getString(R.string.preferences__masterkey_switch));

		//fix wrong state
		if (masterkeySwitchPreference != null && masterkeySwitchPreference.isChecked() != ThreemaApplication.getMasterKey().isProtected()) {
			masterkeySwitchPreference.setChecked(ThreemaApplication.getMasterKey().isProtected());
		}
		setMasterKeyPreferenceText();

		masterkeySwitchPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
				boolean newCheckedValue = newValue.equals(true);

				if (((TwoStatePreference) preference).isChecked() != newCheckedValue) {
					if (!newCheckedValue) {
						Intent intent = new Intent(getActivity(), UnlockMasterKeyActivity.class);
						intent.putExtra(ThreemaApplication.INTENT_DATA_PASSPHRASE_CHECK, true);
						startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_RESET_PASSPHRASE);

						setMasterKeyPreferenceText();
					} else {
						startSetPassphraseActivity();
						return false;
					}
				}
				return true;
			}
		});

		logger.debug("### onCreateUnlocked end");
	}

	private void updateGracePreferenceSummary(String value) {
		String[] existingValues = getResources().getStringArray(R.array.list_pin_grace_time_values);
		for (int n = 0; n < existingValues.length; n++) {
			if (existingValues[n].equals(value)) {
				gracePreference.setSummary(getResources().getStringArray(R.array.list_pin_grace_time)[n]);
			}
		}
	}

	private void removeAccessProtection() {
		lockMechanismPreference.setValue(LockingMech_NONE);
		preferenceService.setPrivateChatsHidden(false);

		if (hiddenChatsListService.getSize() > 0) {
			hiddenChatsListService.clear();
			ListenerManager.conversationListeners.handle(new ListenerManager.HandleListener<ConversationListener>() {
				@Override
				public void handle(ConversationListener listener) {
					// make hidden chats visible again
					listener.onModifiedAll();
				}
			});
		}
	}

	private void updateLockPreferences() {
		pinPreference.setSummary(preferenceService.isPinSet() ? getString(R.string.click_here_to_change_pin) : getString(R.string.prefs_title_pin_code));
		lockMechanismPreference.setSummary(lockMechanismPreference.getEntry());

		switch (lockMechanismPreference.getValue()) {
			case LockingMech_NONE:
				pinPreference.setEnabled(false);
				gracePreference.setEnabled(false);
				uiLockSwitchPreference.setChecked(false);
				uiLockSwitchPreference.setEnabled(false);
				preferenceService.setPin(null);
				preferenceService.setAppLockEnabled(false);
				break;
			case PreferenceService.LockingMech_PIN:
				pinPreference.setEnabled(true);
				gracePreference.setEnabled(true);
				uiLockSwitchPreference.setEnabled(true);
				break;
			case PreferenceService.LockingMech_SYSTEM:
			case PreferenceService.LockingMech_BIOMETRIC:
				pinPreference.setEnabled(false);
				gracePreference.setEnabled(true);
				uiLockSwitchPreference.setEnabled(true);
				preferenceService.setPin(null);
				break;
		}
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		logger.debug("### onViewCreated");

		// we make the complete fragment invisible until it's been unlocked
		fragmentView = view;
		fragmentView.setVisibility(View.INVISIBLE);

		super.onViewCreated(view, savedInstanceState);

		// ask for pin before entering
		if (preferenceService.getLockMechanism().equals(LockingMech_NONE)) {
			onCreateUnlocked();
		} else {
			if ((preferenceService.getLockMechanism().equals(PreferenceService.LockingMech_PIN)) && !preferenceService.isPinSet()) {
				// fix misconfiguration
				preferenceService.setLockMechanism(LockingMech_NONE);
				onCreateUnlocked();
			} else {
				if (savedInstanceState == null) {
					HiddenChatUtil.launchLockCheckDialog(this, preferenceService);
				}
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
				case ThreemaActivity.ACTIVITY_ID_CHECK_LOCK:
					requireScreenLockService().setAuthenticated(true);
					onCreateUnlocked();
					break;
				case ID_ENABLE_SYSTEM_LOCK:
					lockMechanismPreference.setValue(PreferenceService.LockingMech_SYSTEM);
					if (uiLockSwitchPreference.isChecked()) {
						preferenceService.setAppLockEnabled(true);
					}
					updateLockPreferences();
					break;
				case ThreemaActivity.ACTIVITY_ID_RESET_PASSPHRASE:
					//reset here directly
					try {
						ThreemaApplication.getMasterKey().setPassphrase(null);
					} catch (Exception e) {
						logger.error("Exception", e);
					}
					break;
				case ThreemaActivity.ACTIVITY_ID_CHANGE_PASSPHRASE_UNLOCK:
					startChangePassphraseActivity();
					break;
				case ThreemaActivity.ACTIVITY_ID_SET_PASSPHRASE:
				case ThreemaActivity.ACTIVITY_ID_CHANGE_PASSPHRASE:
					//do not handle event
					setMasterKeyPreferenceText();
					break;
				default:
					super.onActivityResult(requestCode, resultCode, data);
			}

			// TODO
			/* show/hide persistent notification */
			PassphraseService.start(requireActivity().getApplicationContext());
		} else {
			switch (requestCode) {
				case ThreemaActivity.ACTIVITY_ID_CHECK_LOCK:
					requireScreenLockService().setAuthenticated(false);
					requireActivity().onBackPressed();
					break;
				case ThreemaActivity.ACTIVITY_ID_SET_PASSPHRASE:
					//only switch back on set
					masterkeySwitchPreference.setChecked(false);
					break;
				case ThreemaActivity.ACTIVITY_ID_RESET_PASSPHRASE:
					//only switch back on set
					masterkeySwitchPreference.setChecked(true);
					break;
				default:
					super.onActivityResult(requestCode, resultCode, data);
			}
		}
	}

	private void setPin() {
		DialogFragment dialogFragment = PasswordEntryDialog.newInstance(
			R.string.set_pin_menu_title,
			R.string.set_pin_summary_intro,
			R.string.set_pin_hint,
			R.string.ok,
			R.string.cancel,
			ThreemaApplication.MIN_PIN_LENGTH,
			ThreemaApplication.MAX_PIN_LENGTH,
			R.string.set_pin_again_summary,
			InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
			0, PasswordEntryDialog.ForgotHintType.NONE);
		dialogFragment.setTargetFragment(this, 0);
		dialogFragment.show(getParentFragmentManager(), ID_DIALOG_PIN);
	}

	@TargetApi(Build.VERSION_CODES.M)
	private void setSystemScreenLock() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			KeyguardManager keyguardManager = (KeyguardManager) requireActivity().getSystemService(Context.KEYGUARD_SERVICE);
			if (keyguardManager.isDeviceSecure()) {
				BiometricUtil.showUnlockDialog(null, this, true, ID_ENABLE_SYSTEM_LOCK, PreferenceService.LockingMech_SYSTEM);
			} else {
				Snackbar snackbar = Snackbar.make(fragmentView, R.string.no_lockscreen_set, Snackbar.LENGTH_LONG);
				snackbar.setAction(R.string.configure, new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
					}
				});
				snackbar.show();
			}
		}
	}

	private void setBiometricLock() {
		/* TODO: Use BiometricLockActivity */
		if (BiometricUtil.isBiometricsSupported(requireContext())) {
			BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
				.setTitle(getString(R.string.prefs_title_access_protection))
				.setSubtitle(getString(R.string.biometric_enter_authentication))
				.setNegativeButtonText(getString(R.string.cancel))
				.build();
			BiometricPrompt biometricPrompt = new BiometricPrompt(requireActivity(), new RuntimeUtil.MainThreadExecutor(), new BiometricPrompt.AuthenticationCallback() {
				@Override
				public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
					if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
						String text = errString + " (" + errorCode + ")";
						try {
							Snackbar.make(fragmentView, text, Snackbar.LENGTH_LONG).show();
						} catch (IllegalArgumentException e) {
							logger.error("Exception", e);
							Toast.makeText(ThreemaApplication.getAppContext(), text, Toast.LENGTH_LONG).show();
						}
					}
				}

				@Override
				public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
					super.onAuthenticationSucceeded(result);
					Snackbar.make(fragmentView, R.string.biometric_authentication_successful, Snackbar.LENGTH_LONG).show();

					lockMechanismPreference.setValue(PreferenceService.LockingMech_BIOMETRIC);
					if (uiLockSwitchPreference.isChecked()) {
						preferenceService.setLockMechanism(PreferenceService.LockingMech_BIOMETRIC);
					}
					updateLockPreferences();
				}

				@Override
				public void onAuthenticationFailed() {
					super.onAuthenticationFailed();
//					Snackbar.make(fragmentView, R.string.biometric_authnetication_failed, Snackbar.LENGTH_LONG).show();
				}
			});
			biometricPrompt.authenticate(promptInfo);
		}
	}

	private void setGraceTime() {
		String graceTime = gracePreference.getValue();
		if (Integer.parseInt(graceTime) >= 0 && Integer.parseInt(graceTime) < 30) {
			//set default grace time to never
			gracePreference.setValue("-1");
			updateGracePreferenceSummary(gracePreference.getValue());
		}
	}

	private void startSetPassphraseActivity() {
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.warning, getString(R.string.password_remember_warning, getString(R.string.app_name)), R.string.ok, R.string.cancel);
		dialog.setTargetFragment(this, 0);
		dialog.show(getParentFragmentManager(), DIALOG_TAG_PASSWORD_REMINDER_PASSPHRASE);
	}

	private void startChangePassphraseActivity() {
		setPassphrase();
	}

	private void setPassphrase() {
		DialogFragment dialogFragment = PasswordEntryDialog.newInstance(
			R.string.masterkey_passphrase_title,
			R.string.masterkey_passphrase_summary,
			R.string.masterkey_passphrase_hint,
			R.string.ok,
			R.string.cancel,
			8, 0,
			R.string.masterkey_passphrase_again_summary,
			0, 0, PasswordEntryDialog.ForgotHintType.NONE);
		dialogFragment.setTargetFragment(this, 0);
		dialogFragment.show(getParentFragmentManager(), ID_DIALOG_PASSPHRASE);
	}

	private void setMasterKeyPreferenceText() {
		masterkeyPreference.setSummary(ThreemaApplication.getMasterKey().isProtected() ?
			getString(R.string.click_here_to_change_passphrase) :
			getString(R.string.prefs_masterkey_passphrase));
	}

	@Override
	public void onYes(String tag, final String text, final boolean isChecked, Object data) {
		switch (tag) {
			case ID_DIALOG_PIN:
				if (preferenceService.setPin(text)) {
					lockMechanismPreference.setValue(PreferenceService.LockingMech_PIN);
					if (uiLockSwitchPreference.isChecked()) {
						preferenceService.setAppLockEnabled(true);
					}
					updateLockPreferences();
				} else {
					Toast.makeText(getActivity(), getString(R.string.pin_invalid_not_set), Toast.LENGTH_SHORT).show();
				}
				break;
			case ID_DIALOG_PASSPHRASE:
				new AsyncTask<Void, Void, Boolean>() {
					@Override
					protected void onPreExecute() {
						GenericProgressDialog.newInstance(R.string.setting_masterkey_passphrase, R.string.please_wait)
							.show(getParentFragmentManager(), ID_DIALOG_PROGRESS);
					}

					@Override
					protected Boolean doInBackground(Void... voids) {
						try {
							// TODO let passwordentrydialog return a char array
							int pl = text.length();
							char[] password = new char[pl];
							text.getChars(0, pl, password, 0);

							ThreemaApplication.getMasterKey().setPassphrase(password);
							RuntimeUtil.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									setMasterKeyPreferenceText();
								}
							});
						} catch (Exception e) {
							logger.error("Exception", e);
							return false;
						}
						return true;
					}

					@Override
					protected void onPostExecute(Boolean success) {
						if (isAdded()) {
							DialogUtil.dismissDialog(getFragmentManager(), ID_DIALOG_PROGRESS, true);
						}
						if (success) {
							masterkeySwitchPreference.setChecked(true);
							if (!PassphraseService.isRunning()) {
								PassphraseService.start(ThreemaApplication.getAppContext());
							}
						}
					}
				}.execute();
				break;
		}
	}

	@Override
	public void onNo(String tag) {
		switch (tag) {
			case ID_DIALOG_PASSPHRASE:
				masterkeySwitchPreference.setChecked(ThreemaApplication.getMasterKey().isProtected());
				setMasterKeyPreferenceText();
				break;
			case ID_DIALOG_PIN:
				// workaround to reset dropdown state
				lockMechanismPreference.setEnabled(false);
				lockMechanismPreference.setEnabled(true);
				break;
			default:
				break;
		}
	}

	@Override
	public void onYes(String tag, Object data) {
		switch (tag) {
			case ID_UNHIDE_CHATS_CONFIRM:
				removeAccessProtection();
				updateLockPreferences();
				break;
			case DIALOG_TAG_PASSWORD_REMINDER_PIN:
				setPin();
				break;
			case DIALOG_TAG_PASSWORD_REMINDER_PASSPHRASE:
				setPassphrase();
				break;
			default:
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		if (DIALOG_TAG_PASSWORD_REMINDER_PIN.equals(tag)) {
			// workaround to reset dropdown state
			lockMechanismPreference.setEnabled(false);
			lockMechanismPreference.setEnabled(true);
		}
	}

	@Override
	public int getPreferenceResource() {
		return R.xml.preference_security;
	}

}
