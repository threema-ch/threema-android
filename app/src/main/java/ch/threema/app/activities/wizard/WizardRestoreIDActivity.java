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

package ch.threema.app.activities.wizard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.client.ThreemaConnection;

public class WizardRestoreIDActivity extends WizardBackgroundActivity {
	private static final Logger logger = LoggerFactory.getLogger(WizardRestoreIDActivity.class);
	private static final String DIALOG_TAG_RESTORE_PROGRESS = "rp";
	private static final int PERMISSION_REQUEST_CAMERA = 1;

	private EditText backupIdText;
	private EditText passwordEditText;
	private boolean passwordOK = false, idOK = false;
	private Button nextButton, scanButton;
	final private int BACKUP_STRING_LENGTH = 99;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_wizard_restore_id);

		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		nextButton = findViewById(R.id.wizard_finish);

		backupIdText = findViewById(R.id.restore_id_edittext);
		backupIdText.setImeOptions(EditorInfo.IME_ACTION_SEND);
		backupIdText.setRawInputType(InputType.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS);
		backupIdText.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				idOK = s.length() > 0 && s.toString().trim().length() == BACKUP_STRING_LENGTH;
				updateMenu();
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});

		passwordEditText = findViewById(R.id.restore_password);
		passwordEditText.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				passwordOK = s.length() >= ThreemaApplication.MIN_PW_LENGTH_ID_EXPORT_LEGACY;
				updateMenu();
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});

		scanButton = findViewById(R.id.wizard_scan);
		scanButton.getCompoundDrawables()[2].setColorFilter(getResources().getColor(R.color.wizard_button_text_inverse), PorterDuff.Mode.SRC_IN);

		Intent intent = getIntent();
		if (intent.hasExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP) &&
				intent.hasExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW)) {
			backupIdText.setText(intent.getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP));
			passwordEditText.setText(intent.getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW));
			restoreID(null);
		}

		findViewById(R.id.wizard_finish).setOnClickListener(this::restoreID);
		findViewById(R.id.wizard_cancel).setOnClickListener(this::onCancel);
		findViewById(R.id.wizard_scan).setOnClickListener(v -> {
			if (ConfigUtils.requestCameraPermissions(WizardRestoreIDActivity.this, null, PERMISSION_REQUEST_CAMERA)) {
				scanQR();
			}
		});
	}

	private void updateMenu() {
		if (nextButton == null) return;

		if (idOK && passwordOK) {
			nextButton.setEnabled(true);
		} else {
			nextButton.setEnabled(false);
		}
	}

	public void onCancel(View view) {
		finish();
	}

	public void scanQR() {
		QRScannerUtil.getInstance().initiateScan(this, false, null);
	}

	@SuppressLint("StaticFieldLeak")
	public void restoreID(View view) {
		EditTextUtil.hideSoftKeyboard(backupIdText);
		EditTextUtil.hideSoftKeyboard(passwordEditText);

		new AsyncTask<Void, Void, Boolean>() {
			String password, backupString;

			@Override
			protected void onPreExecute() {
				GenericProgressDialog.newInstance(R.string.restoring_backup, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_RESTORE_PROGRESS);
				password = passwordEditText.getText().toString();
				backupString = backupIdText.getText().toString().trim();
			}

			@Override
			protected Boolean doInBackground(Void... params) {
				try {
					ThreemaConnection connection = serviceManager.getConnection();
					if (connection.isRunning()) {
						connection.stop();
					}
					return serviceManager.getUserService().restoreIdentity(backupString, password);
				} catch (InterruptedException e) {
					logger.error("Interrupted", e);
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					logger.error("Exception", e);
				}
				return false;
			}

			@Override
			protected void onPostExecute(Boolean result) {
				DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_RESTORE_PROGRESS, true);

				if (result) {
					// ID successfully restored from ID backup - cancel reminder
					serviceManager.getPreferenceService().incrementIDBackupCount();
					setResult(RESULT_OK);
					finish();
				} else {
					getSupportFragmentManager().beginTransaction().add(SimpleStringAlertDialog.newInstance(R.string.error, getString(R.string.wrong_backupid_or_password_or_no_internet_connection)), "er").commitAllowingStateLoss();
				}
			}
		}.execute();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		String scanResult = QRScannerUtil.getInstance().parseActivityResult(this, requestCode, resultCode, intent);
		if (scanResult != null) {
			if (scanResult.length() == BACKUP_STRING_LENGTH) {
				backupIdText.setText(scanResult);
				backupIdText.invalidate();
			} else {
				logger.error(getString(R.string.invalid_barcode), this);
			}
		}
	}

	@Override
	public void onBackPressed() {
		finish();
	}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {
			case PERMISSION_REQUEST_CAMERA:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					scanQR();
				} else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
					ConfigUtils.showPermissionRationale(this, findViewById(R.id.top_view), R.string.permission_camera_qr_required);
				}
				break;
			default:
				break;
		}
	}
}
