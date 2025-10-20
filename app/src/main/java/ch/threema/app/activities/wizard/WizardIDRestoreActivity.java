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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.activities.wizard.components.WizardButtonXml;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SimpleTextWatcher;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.EditTextUtil;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.FetchIdentityException;
import ch.threema.domain.protocol.connection.ServerConnection;

import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardIDRestoreActivity extends WizardBackgroundActivity {
    private static final Logger logger = LoggingUtil.getThreemaLogger("WizardIDRestoreActivity");
    private static final String DIALOG_TAG_RESTORE_PROGRESS = "rp";
    private static final int PERMISSION_REQUEST_CAMERA = 1;

    /**
     * extremely ancient versions of the app on some platform accepted four-letter passwords when generating ID exports
     */
    private static final int MIN_PW_LENGTH_ID_EXPORT_LEGACY = 4;

    private EditText backupIdText;
    private EditText passwordEditText;
    private boolean passwordOK = false;
    private boolean idOK = false;
    private WizardButtonXml nextButtonCompose;
    private final int BACKUP_V1_STRING_LENGTH = 99;
    private final int BACKUP_V2_STRING_LENGTH = 129;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!dependencies.isAvailable()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_wizard_restore_id);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.content),
            InsetSides.all(),
            SpacingValues.symmetric(
                R.dimen.wizard_contents_padding,
                R.dimen.wizard_contents_padding_horizontal
            )
        );

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        backupIdText = findViewById(R.id.restore_id_edittext);
        backupIdText.setImeOptions(EditorInfo.IME_ACTION_SEND);
        backupIdText.setRawInputType(InputType.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        backupIdText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(@NonNull Editable editable) {
                int trimmedLength = editable.toString().trim().length();
                idOK = trimmedLength == BACKUP_V1_STRING_LENGTH || trimmedLength == BACKUP_V2_STRING_LENGTH;
                setRestoreButtonEnabled(idOK && passwordOK);
            }
        });

        passwordEditText = findViewById(R.id.restore_password);
        passwordEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(@NonNull Editable editable) {
                passwordOK = editable.length() >= MIN_PW_LENGTH_ID_EXPORT_LEGACY;
                setRestoreButtonEnabled(idOK && passwordOK);
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
        if (intent.hasExtra(AppConstants.INTENT_DATA_ID_BACKUP) &&
            intent.hasExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW)) {
            backupIdText.setText(intent.getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP));
            passwordEditText.setText(intent.getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW));
            restoreID();
        }
    }

    private void setRestoreButtonEnabled(final boolean isEnabled) {
        if (nextButtonCompose != null) {
            nextButtonCompose.setButtonEnabled(isEnabled);
        }
    }

    public void scanQR() {
        QRScannerUtil.getInstance().initiateScan(this, null);
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
                    ServerConnection connection = dependencies.getServerConnection();
                    if (connection.isRunning()) {
                        connection.stop();
                    }
                    if (dependencies.getUserService().restoreIdentity(backupString, password)) {
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
                    dependencies.getPreferenceService().incrementIDBackupCount();
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
            int scanResultLength = scanResult.length();
            if (scanResultLength == BACKUP_V1_STRING_LENGTH || scanResultLength == BACKUP_V2_STRING_LENGTH) {
                backupIdText.setText(scanResult);
                backupIdText.invalidate();
            } else {
                logger.error(getString(R.string.invalid_threema_qr_code), this);
            }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanQR();
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                ConfigUtils.showPermissionRationale(this, findViewById(R.id.top_view), R.string.permission_camera_qr_required);
            }
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
