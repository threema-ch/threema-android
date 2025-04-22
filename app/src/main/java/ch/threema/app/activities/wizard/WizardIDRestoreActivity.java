/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.wizard.components.WizardButtonXml;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.FetchIdentityException;
import ch.threema.domain.protocol.connection.ServerConnection;

public class WizardIDRestoreActivity extends WizardBackgroundActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WizardIDRestoreActivity");
    private static final String DIALOG_TAG_RESTORE_PROGRESS = "rp";
    private static final int PERMISSION_REQUEST_CAMERA = 1;

    private EditText backupIdText;
    private EditText passwordEditText;
    private boolean passwordOK = false;
    private boolean idOK = false;
    private WizardButtonXml nextButtonCompose;
    final private int BACKUP_STRING_LENGTH = 99;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_wizard_restore_id);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        backupIdText = findViewById(R.id.restore_id_edittext);
        backupIdText.setImeOptions(EditorInfo.IME_ACTION_SEND);
        backupIdText.setRawInputType(InputType.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        backupIdText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                idOK = s.length() > 0 && s.toString().trim().length() == BACKUP_STRING_LENGTH;
                setRestoreButtonEnabled(idOK && passwordOK);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        passwordEditText = findViewById(R.id.restore_password);
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                passwordOK = s.length() >= ThreemaApplication.MIN_PW_LENGTH_ID_EXPORT_LEGACY;
                setRestoreButtonEnabled(idOK && passwordOK);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        findViewById(R.id.wizard_cancel_compose).setOnClickListener(v -> finish());

        nextButtonCompose = findViewById(R.id.wizard_finish_compose);
        nextButtonCompose.setOnClickListener(v -> restoreID());
        setRestoreButtonEnabled(false);

        findViewById(R.id.wizard_scan_compose).setOnClickListener(v -> {
            if (ConfigUtils.requestCameraPermissions(WizardIDRestoreActivity.this, null, PERMISSION_REQUEST_CAMERA)) {
                scanQR();
            }
        });

        Intent intent = getIntent();
        if (intent.hasExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP) &&
            intent.hasExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW)) {
            backupIdText.setText(intent.getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP));
            passwordEditText.setText(intent.getStringExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP_PW));
            restoreID();
        }
    }

    private void setRestoreButtonEnabled(final boolean isEnabled) {
        if (nextButtonCompose != null) {
            nextButtonCompose.setButtonEnabled(isEnabled);
        }
    }

    public void scanQR() {
        QRScannerUtil.getInstance().initiateScan(this, null, QRCodeServiceImpl.QR_TYPE_ID_EXPORT);
    }

    @SuppressLint("StaticFieldLeak")
    public void restoreID() {
        EditTextUtil.hideSoftKeyboard(backupIdText);
        EditTextUtil.hideSoftKeyboard(passwordEditText);

        new AsyncTask<Void, Void, RestoreResult>() {
            String password, backupString;

            @Override
            protected void onPreExecute() {
                GenericProgressDialog.newInstance(R.string.restoring_backup, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_RESTORE_PROGRESS);
                password = passwordEditText.getText().toString();
                backupString = backupIdText.getText().toString().trim();
            }

            @Override
            protected RestoreResult doInBackground(Void... params) {
                try {
                    ServerConnection connection = serviceManager.getConnection();
                    if (connection.isRunning()) {
                        connection.stop();
                    }
                    if (serviceManager.getUserService().restoreIdentity(backupString, password)) {
                        return RestoreResult.success();
                    }
                } catch (InterruptedException e) {
                    logger.error("Interrupted", e);
                    Thread.currentThread().interrupt();
                } catch (FetchIdentityException e) {
                    return RestoreResult.failure(e.getMessage());
                } catch (Exception e) {
                    logger.error("Exception", e);
                }
                return RestoreResult.failure(getString(R.string.wrong_backupid_or_password_or_no_internet_connection));
            }

            @Override
            protected void onPostExecute(RestoreResult result) {
                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_RESTORE_PROGRESS, true);

                if (result.isSuccess()) {
                    // ID successfully restored from ID backup - cancel reminder
                    serviceManager.getPreferenceService().incrementIDBackupCount();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    getSupportFragmentManager().beginTransaction().add(SimpleStringAlertDialog.newInstance(R.string.error, result.getErrorMessage()), "er").commitAllowingStateLoss();
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
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    protected boolean enableOnBackPressedCallback() {
        // Override the behavior of WizardBackgroundActivity to allow normal back navigation
        return false;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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

    private static class RestoreResult {
        private final @Nullable String errorMessage;

        public static RestoreResult success() {
            return new RestoreResult(null);
        }

        public static RestoreResult failure(@Nullable String errorMessage) {
            return new RestoreResult(errorMessage);
        }

        private RestoreResult(@Nullable String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return errorMessage == null;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
