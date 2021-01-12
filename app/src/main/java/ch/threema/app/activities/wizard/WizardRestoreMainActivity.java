/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

package ch.threema.app.activities.wizard;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.WizardRestoreSelectorDialog;
import ch.threema.app.dialogs.WizardSafeSearchPhoneDialog;
import ch.threema.app.threemasafe.ThreemaSafeAdvancedDialog;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.threemasafe.ThreemaSafeServerInfo;
import ch.threema.app.threemasafe.ThreemaSafeService;
import ch.threema.app.threemasafe.ThreemaSafeServiceImpl;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.ThreemaException;
import ch.threema.client.ProtocolDefines;

public class WizardRestoreMainActivity extends WizardBackgroundActivity implements GenericAlertDialog.DialogClickListener, PasswordEntryDialog.PasswordEntryDialogClickListener, WizardSafeSearchPhoneDialog.WizardSafeSearchPhoneDialogCallback,  WizardRestoreSelectorDialog.WizardRestoreSelectorDialogCallback, ThreemaSafeAdvancedDialog.WizardDialogCallback {
	private static final Logger logger = LoggerFactory.getLogger(WizardRestoreMainActivity.class);

	private static final String DIALOG_TAG_PASSWORD = "tpw";
	private static final String DIALOG_TAG_PROGRESS = "tpr";
	private static final String DIALOG_TAG_FORGOT_ID = "li";
	private static final String DIALOG_TAG_RESTORE_SELECTOR = "rss";
	private static final String DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM = "de";
	private static final String DIALOG_TAG_DOWNLOADING_BACKUP = "dwnldBkp";
	private static final String DIALOG_TAG_NO_INTERNET = "nin";
	private static final String DIALOG_TAG_ADVANCED = "adv";

	public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 541;

	private ThreemaSafeService threemaSafeService;

	EditText identityEditText;
	ThreemaSafeMDMConfig safeMDMConfig;
	ThreemaSafeServerInfo serverInfo = new ThreemaSafeServerInfo();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_wizard_restore_main);

		try {
			threemaSafeService = ThreemaApplication.getServiceManager().getThreemaSafeService();
		} catch (Exception e) {
			finish();
			return;
		}

		this.safeMDMConfig = ThreemaSafeMDMConfig.getInstance();

		this.identityEditText = findViewById(R.id.safe_edit_id);

		if (ConfigUtils.isWorkRestricted()) {
			if (safeMDMConfig.isRestoreForced()) {
				this.identityEditText.setText(safeMDMConfig.getIdentity());
				this.identityEditText.setEnabled(false);

				findViewById(R.id.backup_restore_other_button).setVisibility(View.GONE);
				findViewById(R.id.safe_restore_subtitle).setVisibility(View.INVISIBLE);
				findViewById(R.id.forgot_id).setVisibility(View.GONE);

				if (safeMDMConfig.isSkipRestorePasswordEntryDialog()) {
					reallySafeRestore(safeMDMConfig.getPassword());
				}
			}
		}

		this.identityEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		this.identityEditText.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(ProtocolDefines.IDENTITY_LEN)});

		findViewById(R.id.forgot_id).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				WizardSafeSearchPhoneDialog.newInstance().show(getSupportFragmentManager(), DIALOG_TAG_FORGOT_ID);
			}
		});

		findViewById(R.id.backup_restore_other_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showOtherRestoreOptions();
			}
		});

		findViewById(R.id.safe_restore_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (identityEditText != null && identityEditText.getText() != null && identityEditText.getText().toString().length() == ProtocolDefines.IDENTITY_LEN) {
					doSafeRestore();
				} else {
					SimpleStringAlertDialog.newInstance(R.string.safe_restore, R.string.invalid_threema_id).show(getSupportFragmentManager(), "");
				}
			}
		});

		Button advancedOptions = findViewById(R.id.advanced_options);
		advancedOptions.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ThreemaSafeAdvancedDialog dialog = ThreemaSafeAdvancedDialog.newInstance(serverInfo, false);
				dialog.show(getSupportFragmentManager(), DIALOG_TAG_ADVANCED);
			}
		});

		if (ConfigUtils.isWorkRestricted()) {
			if (safeMDMConfig.isRestoreExpertSettingsDisabled()) {
				advancedOptions.setEnabled(false);
				advancedOptions.setVisibility(View.GONE);
			}
			Intent intent = getIntent();
			if (intent.hasExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP) &&
				intent.hasExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW)) {

				launchIdRecovery(intent.getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP),
					intent.getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW));
			}
			if (safeMDMConfig.isRestoreDisabled()) {
				findViewById(R.id.safe_restore_button).setVisibility(View.GONE);
				((TextView) findViewById(R.id.safe_restore_title)).setText(R.string.restore);
				findViewById(R.id.safe_restore_subtitle).setVisibility(View.INVISIBLE);
				findViewById(R.id.forgot_id).setVisibility(View.GONE);
				identityEditText.setVisibility(View.GONE);
				advancedOptions.setVisibility(View.GONE);
				showOtherRestoreOptions();
			}
		}
	}

	private void showOtherRestoreOptions() {
		WizardRestoreSelectorDialog.newInstance().show(getSupportFragmentManager(), DIALOG_TAG_RESTORE_SELECTOR);
	}

	private void doSafeRestore() {
		PasswordEntryDialog dialogFragment = PasswordEntryDialog.newInstance(
			R.string.safe_enter_password,
			R.string.restore_data_password_msg,
			R.string.password_hint,
			R.string.ok,
			R.string.cancel,
			ThreemaSafeServiceImpl.MIN_PW_LENGTH,
			ThreemaSafeServiceImpl.MAX_PW_LENGTH, 0, 0, 0);
		dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_PASSWORD);
	}

	@SuppressLint("StaticFieldLeak")
	private void reallySafeRestore(String password) {
		final String identity;

		if (safeMDMConfig.isRestoreForced()) {
			serverInfo = safeMDMConfig.getServerInfo();
			identity = safeMDMConfig.getIdentity();
		} else {
			if (safeMDMConfig.isRestoreExpertSettingsDisabled()) {
				serverInfo = safeMDMConfig.getServerInfo();
			}
			if (identityEditText.getText() != null) {
				identity = identityEditText.getText().toString();
			} else {
				identity = null;
			}
		}

		if (TestUtil.empty(identity)) {
			Toast.makeText(this, R.string.invalid_threema_id, Toast.LENGTH_LONG).show();
			return;
		}

		if (TestUtil.empty(password)) {
			Toast.makeText(this, R.string.wrong_backupid_or_password_or_no_internet_connection, Toast.LENGTH_LONG).show();
			return;
		}

		new AsyncTask<Void, Void, String>() {
			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.restore, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_PROGRESS);
				try {
					serviceManager.stopConnection();
				} catch (InterruptedException e) {
					this.cancel(true);
				}
			}

			@Override
			protected String doInBackground(Void... voids) {
				if (this.isCancelled()) {
					return "Backup cancelled";
				}
				preferenceService.setThreemaSafeEnabled(false);
				try {
					threemaSafeService.restoreBackup(identity, password, serverInfo);
					threemaSafeService.testServer(serverInfo);  // intentional: test server to update configuration only after restoring backup, so that master key (and thus shard hash) is set
					threemaSafeService.setEnabled(true);
					return null;
				} catch (ThreemaException e) {
					return e.getMessage();
				} catch (IOException e) {
					if (e instanceof FileNotFoundException) {
						return getString(R.string.safe_no_backup_found);
					}
					return e.getLocalizedMessage();
				}
			}

			@Override
			protected void onCancelled(String failureMessage) {
				this.onPostExecute(failureMessage);
			}

			@Override
			protected void onPostExecute(String failureMessage) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_PROGRESS, true);

				if (failureMessage == null) {
					SimpleStringAlertDialog.newInstance(R.string.restore_success_body,
							Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ?
									R.string.android_backup_restart_threema :
									R.string.safe_backup_tap_to_restart,
							true).show(getSupportFragmentManager(), "d");
					try {
						serviceManager.startConnection();
					} catch (ThreemaException e) {
						logger.error("Exception", e);
					}
					ConfigUtils.scheduleAppRestart(getApplicationContext(), 3000, getApplicationContext().getString(R.string.ipv6_restart_now));
				} else {
					Toast.makeText(WizardRestoreMainActivity.this, getString(R.string.safe_restore_failed) + ". " + failureMessage, Toast.LENGTH_LONG).show();
					if (safeMDMConfig.isRestoreForced()) {
						finish();
					}
				}
			}
		}.execute();
	}

	private void launchIdRecovery(String backupString, String backupPassword) {
		Intent intent = new Intent(this, WizardRestoreIDActivity.class);

		if (!TestUtil.empty(backupString) && !TestUtil.empty(backupPassword)) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP, backupString);
			intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW, backupPassword);
		}
		startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_RESTORE_KEY);
		overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
	}

	private void showDisableEnergySaveDialog() {
		GenericAlertDialog.newInstance(R.string.menu_restore, R.string.restore_disable_energy_saving, R.string.ok, R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM);
	}

	private void doDataBackupRestore(final Uri uri) {
		if (!ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme()) && this.fileService != null) {
			// copy "file" to cache directory first
			GenericProgressDialog.newInstance(R.string.importing_files, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING_BACKUP);

			new Thread(() -> {
				final File file = fileService.copyUriToTempFile(uri, "file", "zip", true);

				RuntimeUtil.runOnUiThread(() -> {
					DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING_BACKUP, true);

					if (file != null) {
						performDataBackupRestore(file);
						file.deleteOnExit();
					}
				});
			}).start();

		} else {
			String path = FileUtil.getRealPathFromURI(this, uri);
			if (path != null && !path.isEmpty()) {
				File file = new File(path);
				if (file.exists()) {
					performDataBackupRestore(file);
				}
			}
		}
	}

	private void performDataBackupRestore(File file) {
		if (file.exists()) {
//			try {
// Zipfile validity check is sometimes wrong
//				ZipFile zipFile = new ZipFile(file);
//				if (zipFile.isValidZipFile()) {
				ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

				NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
				if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
					showNoInternetDialog(file);
				} else {
					confirmRestore(file);
				}
				return;
			}
//				}
//			} catch (ZipException e) {
//				logger.error("Exception", e);
//			}
		logger.error(getString(R.string.invalid_backup), this);
	}

	private void confirmRestore(File file) {
		PasswordEntryDialog dialogFragment = PasswordEntryDialog.newInstance(
			R.string.backup_data_title,
			R.string.restore_data_password_msg,
			R.string.password_hint,
			R.string.ok,
			R.string.cancel,
			ThreemaApplication.MIN_PW_LENGTH_BACKUP,
			ThreemaApplication.MAX_PW_LENGTH_BACKUP, 0, 0, 0);
		dialogFragment.setData(file);
		dialogFragment.show(getSupportFragmentManager(), "restorePW");
	}

	private void startNextWizard() {
		logger.debug("start next wizard");

		if (this.userService.hasIdentity()) {
			serviceManager.getPreferenceService().setWizardRunning(true);

			startActivity(new Intent(this, WizardBaseActivity.class));
			overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
			finish();
		}
	}

	@UiThread
	private void showNoInternetDialog(File file) {
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.menu_restore, R.string.new_wizard_need_internet, R.string.retry, R.string.cancel);
		dialog.setData(file);
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_NO_INTERNET);
	}

	@Override
	protected void onPause() {
		ThreemaApplication.activityPaused(this);
		super.onPause();
	}

	@Override
	protected void onResume() {
		ThreemaApplication.activityResumed(this);
		super.onResume();
	}

	@Override
	public void onUserInteraction() {
		ThreemaApplication.activityUserInteract(this);
		super.onUserInteraction();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
		logger.debug("Restore: {} {}", requestCode, resultCode);

		if (resultCode != RESULT_OK) {
			if (requestCode != REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS && requestCode != ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER) {
				if (safeMDMConfig.isRestoreDisabled()) {
					finish();
				}
			}
		}

		switch (requestCode) {
			case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
				FileUtil.selectFile(WizardRestoreMainActivity.this, null, new String[]{MimeUtil.MIME_TYPE_ZIP}, ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER, false, 0, fileService.getBackupPath().getPath());
				break;

			case ThreemaActivity.ACTIVITY_ID_RESTORE_KEY:
				if (resultCode == RESULT_OK) {
					setResult(RESULT_OK);
					startNextWizard();
				}
				break;

			case ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER:
				if (resultCode == RESULT_OK) {
					setResult(RESULT_OK);
					if (resultData != null) {
						Uri uri;

						uri = resultData.getData();
						if (uri != null) {
							doDataBackupRestore(uri);
						}
					}
				}
				break;
		}
		super.onActivityResult(requestCode, resultCode, resultData);
	}

	@Override
	public void onBackPressed() {
		finish();
	}

	@Override
	public void onYes(String tag, String text, boolean isChecked, Object data) {
		if (DIALOG_TAG_PASSWORD.equals(tag)) {
			// safe backup restore
			if (!TestUtil.empty(text)) {
				reallySafeRestore(text);
			}
		} else {
			// data backup restore
			Intent intent = new Intent(this, RestoreService.class);
			intent.putExtra(RestoreService.EXTRA_RESTORE_BACKUP_FILE, (File) data);
			intent.putExtra(RestoreService.EXTRA_RESTORE_BACKUP_PASSWORD, text);
			ContextCompat.startForegroundService(this, intent);

			finish();
		}
	}

	@Override
	public void onYes(String tag, String id) {
		// return from id search
		if (id != null && id.length() == ProtocolDefines.IDENTITY_LEN) {
			this.identityEditText.setText("");
			this.identityEditText.append(id);
		}
	}

	@Override
	public void onYes(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM:
				Intent intent = new Intent(this, DisableBatteryOptimizationsActivity.class);
				intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_NAME, getString(R.string.restore));
				intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_WIZARD, true);
				startActivityForResult(intent, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS);
				break;
			case DIALOG_TAG_NO_INTERNET:
				performDataBackupRestore((File) data);
				break;
		}
	}

	@Override
	public void onYes(String tag, ThreemaSafeServerInfo serverInfo) {
		this.serverInfo = serverInfo;
	}

	@Override
	public void onNo(String tag) {
		if (DIALOG_TAG_RESTORE_SELECTOR.equals(tag)) {
			if (safeMDMConfig.isRestoreDisabled()) {
				finish();
			}
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		if (DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM.equals(tag)) {
			if (safeMDMConfig.isRestoreDisabled()) {
				finish();
			}
		}
	}

	@Override
	public void onDataBackupRestore() {
		showDisableEnergySaveDialog();
	}

	@Override
	public void onIdBackupRestore() {
		launchIdRecovery(null, null);
	}

}
