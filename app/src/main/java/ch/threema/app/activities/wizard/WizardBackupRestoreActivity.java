/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2025 Threema GmbH
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

import android.app.Activity;
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

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.activities.ThreemaAppCompatActivity;
import ch.threema.app.activities.wizard.components.WizardButtonXml;
import ch.threema.app.backuprestore.csv.RestoreService;
import ch.threema.app.di.DependencyContainer;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.services.ActivityService;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.MimeUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static ch.threema.app.di.DIJavaCompat.isSessionScopeReady;
import static ch.threema.app.utils.ActiveScreenLoggerKt.logScreenVisibility;

public class WizardBackupRestoreActivity extends ThreemaAppCompatActivity implements GenericAlertDialog.DialogClickListener,
    PasswordEntryDialog.PasswordEntryDialogClickListener {
    private static final Logger logger = getThreemaLogger("WizardBackupRestoreActivity");

    private static final String DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM = "de";
    private static final String DIALOG_TAG_DOWNLOADING_BACKUP = "dwnldBkp";
    private static final String DIALOG_TAG_NO_INTERNET = "nin";
    private static final String DIALOG_TAG_ERROR_TMP_FILE_DIR = "tmpFileDialog";

    public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 541;

    @NonNull
    private final DependencyContainer dependencies = KoinJavaComponent.get(DependencyContainer.class);

    private File backupFile;
    private String backupPassword;

    private final ActivityResultLauncher<String> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            // Restore backup even if permission is not granted as we do not strictly require the
            // notification permission.
            if (Boolean.TRUE.equals(isGranted)) {
                logger.info("Notification permission granted, starting restore");
            } else {
                logger.info("Notification permission not granted, starting restore anyway");
            }
            startRestore();
        });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logScreenVisibility(this, logger);

        if (!isSessionScopeReady()) {
            finish();
            return;
        }

        // directly forward to ID restore activity
        Intent intent = getIntent();
        if (intent.hasExtra(AppConstants.INTENT_DATA_ID_BACKUP) &&
            intent.hasExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW)) {

            restoreIDExport(intent.getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP),
                intent.getStringExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW));
        }

        initLayout();

        cleanTempDirectories();
    }

    @Override
    protected void onPause() {
        ActivityService.activityPaused(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        ActivityService.activityResumed(this);
        super.onResume();
    }

    @Override
    public void onUserInteraction() {
        ActivityService.activityUserInteract(this);
        super.onUserInteraction();
    }

    private void initLayout() {
        setContentView(R.layout.activity_backup_restore);

        ViewExtensionsKt.applyDeviceInsetsAsPadding(
            findViewById(R.id.content),
            InsetSides.all(),
            SpacingValues.symmetric(R.dimen.wizard_contents_padding, R.dimen.wizard_contents_padding_horizontal)
        );

        String faqURL = String.format(getString(R.string.backup_faq_url), LocaleUtil.getAppLanguage());
        TextView backupSubtitle = findViewById(R.id.backup_restore_subtitle);
        backupSubtitle.setText(Html.fromHtml(
            String.format(getString(R.string.backup_restore_type), faqURL))
        );
        backupSubtitle.setMovementMethod(LinkMovementMethod.getInstance());

        final @NonNull WizardButtonXml safeBackupButtonCompose = findViewById(R.id.safe_backup_compose);
        if (ConfigUtils.isWorkRestricted() && dependencies.getThreemaSafeMDMConfig().isRestoreDisabled()) {
            safeBackupButtonCompose.setVisibility(View.GONE);
        } else {
            safeBackupButtonCompose.setOnClickListener(v -> {
                logger.info("Threema Safe Backup clicked");
                restoreSafe();
            });
        }
        findViewById(R.id.data_backup_compose).setOnClickListener(v -> {
            logger.info("Data Backup clicked");
            showDisableEnergySaveDialog();
        });
        findViewById(R.id.id_backup_compose).setOnClickListener(v -> {
            logger.info("Exported-ID clicked");
            restoreIDExport(null, null);
        });
        findViewById(R.id.cancel_compose).setOnClickListener(v -> {
            logger.info("Cancel clicked");
            finish();
        });
    }

    private void cleanTempDirectories() {
        RuntimeUtil.runOnWorkerThread(() -> {
            // Clean the temp directories to ensure that any backup files
            // from previous restore attempts are deleted.
            dependencies.getFileService().cleanTempDirs();
        });
    }

    private void restoreSafe() {
        startActivity(new Intent(this, WizardSafeRestoreActivity.class));
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
    }

    private void restoreIDExport(String backupString, String backupPassword) {
        Intent intent = new Intent(this, WizardIDRestoreActivity.class);

        if (!TestUtil.isEmptyOrNull(backupString) && !TestUtil.isEmptyOrNull(backupPassword)) {
            intent.putExtra(AppConstants.INTENT_DATA_ID_BACKUP, backupString);
            intent.putExtra(AppConstants.INTENT_DATA_ID_BACKUP_PW, backupPassword);
        }
        startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_RESTORE_KEY);
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
    }

    private void restoreBackup(final Uri uri) {
        if (!ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
            // copy "file" to cache directory first
            GenericProgressDialog.newInstance(R.string.importing_files, R.string.please_wait).show(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING_BACKUP);

            new Thread(() -> {
                final File file = dependencies.getFileService().copyUriToTempFile(uri, "backup_restore", ".zip");

                RuntimeUtil.runOnUiThread(() -> {
                    DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_DOWNLOADING_BACKUP, true);

                    if (file != null) {
                        logger.info("Backup file copied, starting restore");
                        restoreBackupFile(file);
                        file.deleteOnExit();
                    } else {
                        logger.warn("Failed to copy backup file");
                        SimpleStringAlertDialog.newInstance(R.string.an_error_occurred, R.string.missing_permission_external_storage).show(getSupportFragmentManager(), DIALOG_TAG_ERROR_TMP_FILE_DIR);
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

    private void restoreBackupFile(@NonNull File file) {
        if (file.exists()) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
                showNoInternetDialog(file);
            } else {
                confirmRestore(file);
            }
            return;
        }
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
            AppConstants.MIN_PW_LENGTH_BACKUP,
            AppConstants.MAX_PW_LENGTH_BACKUP,
            0, 0, 0, PasswordEntryDialog.ForgotHintType.PIN_PASSPHRASE);
        dialogFragment.setData(file);
        dialogFragment.show(getSupportFragmentManager(), "restorePW");
    }

    private void startRestore() {
        Intent intent = new Intent(this, RestoreService.class);
        intent.putExtra(RestoreService.EXTRA_RESTORE_BACKUP_FILE, backupFile);
        intent.putExtra(RestoreService.EXTRA_RESTORE_BACKUP_PASSWORD, backupPassword);
        ContextCompat.startForegroundService(this, intent);

        setResult(Activity.RESULT_OK);
        finish();
    }

    @UiThread
    private void showNoInternetDialog(File file) {
        logger.info("Showing no-internet dialog");
        GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.menu_restore, R.string.new_wizard_need_internet, R.string.retry, R.string.cancel);
        dialog.setData(file);
        dialog.show(getSupportFragmentManager(), DIALOG_TAG_NO_INTERNET);
    }

    // start generic alert dialog callbacks
    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_DISABLE_ENERGYSAVE_CONFIRM:
                logger.info("Showing disable-battery-optimizations settings");
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
        if (dependencies.getThreemaSafeMDMConfig().isRestoreDisabled()) {
            finish();
        }
    }
    // end generic alert dialog callbacks

    // start password dialog callbacks
    @Override
    public void onYes(String tag, String text, boolean isChecked, Object data) {
        this.backupFile = (File) data;
        this.backupPassword = text;

        // If the notification permission is already granted, then start the restore directly
        if (ConfigUtils.requestNotificationPermission(this, permissionLauncher, dependencies.getPreferenceService())) {
            logger.info("Password was entered and permission granted, starting restore");
            startRestore();
        }
    }

    @Override
    public void onNo(String tag) {
        if (dependencies.getThreemaSafeMDMConfig().isRestoreDisabled()) {
            finish();
        }
    }
    // end password dialog callbacks

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (resultCode != RESULT_OK) {
            if (requestCode != REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS && requestCode != ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER) {
                if (dependencies.getThreemaSafeMDMConfig().isRestoreDisabled()) {
                    finish();
                }
            }
        }

        switch (requestCode) {
            case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
                logger.info("Opening restore file picker");
                FileUtil.selectFile(
                    WizardBackupRestoreActivity.this,
                    null,
                    new String[]{MimeUtil.MIME_TYPE_ZIP},
                    ThreemaActivity.ACTIVITY_ID_BACKUP_PICKER,
                    false,
                    0,
                    dependencies.getFileService().getBackupPath().getPath()
                );
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
                            logger.info("Restore file selected, startup backup restore");
                            restoreBackup(uri);
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, resultData);
    }

    private void startNextWizard() {
        if (dependencies.getUserService().hasIdentity()) {
            logger.info("Starting wizard");
            dependencies.getNotificationPreferenceService().setWizardRunning(true);
            startActivity(new Intent(this, WizardBaseActivity.class));
            overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out);
            finish();
        }
    }
}
