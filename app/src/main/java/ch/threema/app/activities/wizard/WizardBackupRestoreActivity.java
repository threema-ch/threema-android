/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.slf4j.Logger;

import java.io.File;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaAppCompatActivity;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public class WizardBackupRestoreActivity extends ThreemaAppCompatActivity implements GenericAlertDialog.DialogClickListener,
	PasswordEntryDialog.PasswordEntryDialogClickListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("WizardBackupRestoreActivity");

	private static final String DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM = "de";
	private static final String DIALOG_TAG_DOWNLOADING_BACKUP = "dwnldBkp";
	private static final String DIALOG_TAG_NO_INTERNET = "nin";

	public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 541;

	private ThreemaSafeMDMConfig safeMDMConfig;
	private FileService fileService;
	private UserService userService;
	private PreferenceService preferenceService;

	@Override
	protected void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// directly forward to ID restore activity
		Intent intent = getIntent();
		if (intent.hasExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP) &&
			intent.hasExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW)) {

			restoreIDExport(intent.getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP),
				intent.getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW));
		}

		initServices();
		initLayout();
		initListeners();
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

	private void initServices() {
		this.safeMDMConfig = ThreemaSafeMDMConfig.getInstance();

		try {
			ServiceManager serviceManager = ThreemaApplication.getServiceManager();
			if (serviceManager != null) {
				fileService = serviceManager.getFileService();
				userService = serviceManager.getUserService();
				preferenceService = serviceManager.getPreferenceService();
			}
		} catch (Exception e) {
			logger.error("Exception ", e);
			finish();
		}
	}

	private void initLayout() {
		setContentView(R.layout.activity_backup_restore);

		String faqURL = String.format(getString(R.string.backup_faq_url), LocaleUtil.getAppLanguage());
		TextView backupSubtitle = findViewById(R.id.backup_restore_subtitle);
		backupSubtitle.setText(Html.fromHtml(
			String.format(getString(R.string.backup_restore_type), faqURL))
		);
		backupSubtitle.setMovementMethod(LinkMovementMethod.getInstance());

		if (ConfigUtils.isWorkRestricted()) {
			if (safeMDMConfig.isRestoreDisabled()) {
				findViewById(R.id.safe_backup).setVisibility(View.GONE);
			}
		}
	}

	private void initListeners() {
		findViewById(R.id.safe_backup).setOnClickListener(v -> restoreSafe());
		findViewById(R.id.data_backup).setOnClickListener(v -> showDisableEnergySaveDialog());
		findViewById(R.id.id_backup).setOnClickListener(v -> restoreIDExport(null, null));
		findViewById(R.id.cancel).setOnClickListener(v -> finish());
	}

	public void restoreSafe() {
		startActivity(new Intent(this, WizardSafeRestoreActivity.class));
		overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
	}

	public void restoreIDExport(String backupString, String backupPassword) {
		Intent intent = new Intent(this, WizardIDRestoreActivity.class);

		if (!TestUtil.empty(backupString) && !TestUtil.empty(backupPassword)) {
			intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP, backupString);
			intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW, backupPassword);
		}
		startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_RESTORE_KEY);
		overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
	}

	private void restoreBackup(final Uri uri) {
		if (!ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme()) && this.fileService != null) {
			// copy "file" to cache directory first
			GenericProgressDialog.newInstance(R.string.importing_files, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING_BACKUP);

			new Thread(() -> {
				final File file = fileService.copyUriToTempFile(uri, "file", "zip", true);

				RuntimeUtil.runOnUiThread(() -> {
					DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING_BACKUP, true);

					if (file != null) {
						restoreBackupFile(file);
						file.deleteOnExit();
					} else {
						Toast.makeText(this, "Unable to access/copy selected file to temporary directory", Toast.LENGTH_LONG).show();
					}
				});
			}).start();

		} else {
			String path = FileUtil.getRealPathFromURI(this, uri);
			if (path != null && !path.isEmpty()) {
				File file = new File(path);
				if (file.exists()) {
					restoreBackupFile(file);
				}
			}
		}
	}

	private void restoreBackupFile(File file) {
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

	private void showDisableEnergySaveDialog() {
		GenericAlertDialog.newInstance(R.string.menu_restore, R.string.restore_disable_energy_saving, R.string.ok, R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM);
	}

	private void confirmRestore(File file) {
		PasswordEntryDialog dialogFragment = PasswordEntryDialog.newInstance(
			R.string.backup_data_title,
			R.string.restore_data_password_msg,
			R.string.password_hint,
			R.string.ok,
			R.string.cancel,
			ThreemaApplication.MIN_PW_LENGTH_BACKUP,
			ThreemaApplication.MAX_PW_LENGTH_BACKUP,
			0, 0, 0, PasswordEntryDialog.ForgotHintType.PIN_PASSPHRASE);
		dialogFragment.setData(file);
		dialogFragment.show(getSupportFragmentManager(), "restorePW");
	}

	@UiThread
	private void showNoInternetDialog(File file) {
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.menu_restore, R.string.new_wizard_need_internet, R.string.retry, R.string.cancel);
		dialog.setData(file);
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_NO_INTERNET);
	}

	// start generic alert dialog callbacks
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
				restoreBackupFile((File) data);
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) {
		if (safeMDMConfig.isRestoreDisabled()) {
			finish();
		}
	}
	// end generic alert dialog callbacks

	// start password dialog callbacks
	@Override
	public void onYes(String tag, String text, boolean isChecked, Object data) {
		Intent intent = new Intent(this, RestoreService.class);
		intent.putExtra(RestoreService.EXTRA_RESTORE_BACKUP_FILE, (File) data);
		intent.putExtra(RestoreService.EXTRA_RESTORE_BACKUP_PASSWORD, text);
		ContextCompat.startForegroundService(this, intent);
		finish();
	}

	@Override
	public void onNo(String tag) {
		if (safeMDMConfig.isRestoreDisabled()) {
			finish();
		}
	}
	// end password dialog callbacks

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
		if (resultCode != RESULT_OK) {
			if (requestCode != REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS && requestCode != ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER) {
				if (safeMDMConfig.isRestoreDisabled()) {
					finish();
				}
			}
		}

		switch (requestCode) {
			case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
				FileUtil.selectFile(WizardBackupRestoreActivity.this, null, new String[]{MimeUtil.MIME_TYPE_ZIP}, ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER, false, 0, fileService.getBackupPath().getPath());
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
							restoreBackup(uri);
						}
					}
				}
				break;
		}
		super.onActivityResult(requestCode, resultCode, resultData);
	}

	private void startNextWizard() {
		if (this.userService.hasIdentity()) {
			this.preferenceService.setWizardRunning(true);
			startActivity(new Intent(this, WizardBaseActivity.class));
			overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
			finish();
		}
	}
}
