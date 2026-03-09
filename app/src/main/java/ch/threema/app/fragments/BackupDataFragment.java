package ch.threema.app.fragments;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.slf4j.Logger;

import java.time.Instant;

import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.backuprestore.BackupRestoreDataConfig;
import ch.threema.app.backuprestore.csv.BackupService;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.FileService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.ui.InsetSides;
import ch.threema.app.ui.SpacingValues;
import ch.threema.app.ui.ViewExtensionsKt;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.TestUtil;

import static ch.threema.android.UriExtensionsKt.isFileUri;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class BackupDataFragment extends Fragment implements
    GenericAlertDialog.DialogClickListener,
    PasswordEntryDialog.PasswordEntryDialogClickListener {
    private static final Logger logger = getThreemaLogger("BackupDataFragment");

    public static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 441;
    private static final int REQUEST_CODE_DOCUMENT_TREE = 7222;

    private static final int PERMISSION_REQUEST_STORAGE_DOBACKUP = 2;

    private static final String DIALOG_TAG_ENERGY_SAVING_REMINDER = "esr";
    private static final String DIALOG_TAG_DISABLE_ENERGY_SAVING = "des";
    private static final String DIALOG_TAG_PASSWORD = "pwd";
    private static final String DIALOG_TAG_PATH_INTRO = "pathintro";
    private static final String DIALOG_TAG_FALLBACK_PATH = "pathfallback";

    private View fragmentView;
    private ExtendedFloatingActionButton fab;
    private FileService fileService;
    private PreferenceService preferenceService;
    private Uri backupUri;
    private TextView pathTextView;
    private NestedScrollView scrollView;
    private MaterialButton pathChangeButton;

    private boolean launchedFromFAB = false;

    @Override
    public void onDestroyView() {
        this.fab.setOnClickListener(null);
        this.fab = null;
        this.scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) null);
        this.scrollView = null;
        this.pathChangeButton.setOnClickListener(null);
        this.pathChangeButton = null;
        this.pathTextView = null;

        fragmentView.findViewById(R.id.info).setOnClickListener(null);
        fragmentView.findViewById(R.id.restore).setOnClickListener(null);

        this.fragmentView = null;

        super.onDestroyView();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        try {
            ServiceManager serviceManager = ThreemaApplication.getServiceManager();
            this.fileService = serviceManager.getFileService();
            this.preferenceService = serviceManager.getPreferenceService();
        } catch (Exception e) {
            logger.error("Exception", e);
            getActivity().finish();
        }

        backupUri = fileService.getBackupUri();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (this.fragmentView == null) {
            this.fragmentView = inflater.inflate(R.layout.fragment_backup_data, container, false);

            fab = fragmentView.findViewById(R.id.floating);
            fab.setOnClickListener(v -> {
                logger.info("FAB clicked, initiating backup");
                launchedFromFAB = true;
                initiateBackup();
            });
            fab.show();

            ViewExtensionsKt.applyDeviceInsetsAsMargin(
                fab,
                InsetSides.lbr(),
                SpacingValues.all(R.dimen.grid_unit_x2)
            );

            scrollView = fragmentView.findViewById(R.id.scroll_parent);
            scrollView.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (nestedScrollView, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (nestedScrollView.getTop() == scrollY) {
                        fab.extend();
                    } else {
                        fab.shrink();
                    }
                }
            );

            ViewExtensionsKt.applyDeviceInsetsAsPadding(
                scrollView,
                InsetSides.lbr(),
                SpacingValues.bottom(R.dimen.grid_unit_x10)
            );

            fragmentView.findViewById(R.id.info).setOnClickListener(v -> onInfoButtonClicked(v));
            fragmentView.findViewById(R.id.restore).setOnClickListener(v -> onRestoreButtonClicked(v));

            pathChangeButton = fragmentView.findViewById(R.id.backup_path_change_btn);
            pathChangeButton.findViewById(R.id.backup_path_change_btn).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    logger.info("Path change button clicked");
                    launchedFromFAB = false;
                    showPathSelectionIntro();
                }
            });

            pathTextView = fragmentView.findViewById(R.id.backup_path);
            pathTextView.setText(getDirectoryName(backupUri));
        }

        var backupTimestamp = preferenceService.getLastDataBackupTimestamp();
        if (backupTimestamp != null) {
            this.fragmentView.findViewById(R.id.last_backup_layout).setVisibility(View.VISIBLE);
            this.fragmentView.findViewById(R.id.intro_layout).setVisibility(View.GONE);
            ((TextView) this.fragmentView.findViewById(R.id.last_backup_date)).setText(LocaleUtil.formatTimeStampStringAbsolute(getContext(), backupTimestamp));
        } else {
            this.fragmentView.findViewById(R.id.last_backup_layout).setVisibility(View.GONE);
            this.fragmentView.findViewById(R.id.intro_layout).setVisibility(View.VISIBLE);
        }

        return this.fragmentView;
    }

    /**
     * Get the name of a directory from an Uri selected with Intent.ACTION_OPEN_DOCUMENT_TREE
     *
     * @param directoryTreeUri Uri returned by ACTION_OPEN_DOCUMENT_TREE
     * @return Name of directory, a localized string "not set" if an empty Uri was provided, or the complete Uri as a String as a fallback
     */
    private @NonNull String getDirectoryName(Uri directoryTreeUri) {
        if (directoryTreeUri == null) {
            return getString(R.string.not_set);
        } else {
            if (isFileUri(directoryTreeUri)) {
                var dirName = directoryTreeUri.getLastPathSegment();
                if (dirName != null) {
                    return dirName;
                }
            }
            try {
                DocumentFile documentFile = DocumentFile.fromTreeUri(getContext(), directoryTreeUri);
                if (documentFile != null && documentFile.isDirectory()) {
                    String name = documentFile.getName();
                    if (!TestUtil.isEmptyOrNull(name)) {
                        return name;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return directoryTreeUri.toString();
    }

    private void showPathSelectionIntro() {
        if (hasDocumentPickerPotentiallyFailedRecently()) {
            var dialog = GenericAlertDialog.newInstance(
                R.string.set_backup_path,
                R.string.backup_path_selection_message,
                R.string.backup_path_selection_default,
                R.string.backup_path_selection_documents
            );
            dialog.setTargetFragment(this);
            dialog.show(getFragmentManager(), DIALOG_TAG_FALLBACK_PATH);
            return;
        }

        GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.set_backup_path, R.string.set_backup_path_intro, R.string.ok, R.string.cancel);
        dialog.setTargetFragment(this);
        dialog.show(getFragmentManager(), DIALOG_TAG_PATH_INTRO);
    }

    private boolean hasDocumentPickerPotentiallyFailedRecently() {
        var lastLaunchTime = preferenceService.getDataBackupPickerLaunched();
        if (lastLaunchTime == null) {
            return false;
        }
        return Instant.now().toEpochMilli() - lastLaunchTime.toEpochMilli() < 10 * DateUtils.MINUTE_IN_MILLIS;
    }

    @UiThread
    private void launchDocumentTree() {
        try {
            logger.info("Opening document picker");
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            if (!hasDocumentPickerPotentiallyFailedRecently()) {
                // undocumented APIs according to https://issuetracker.google.com/issues/72053350
                // if we detected that the document picker might have failed, we excluded these, as they might be part of the problem
                i.putExtra("android.content.extra.SHOW_ADVANCED", true);
                i.putExtra("android.content.extra.FANCY", true);
                i.putExtra("android.content.extra.SHOW_FILESIZE", true);
            }
            startActivityForResult(i, REQUEST_CODE_DOCUMENT_TREE);
            preferenceService.setDataBackupPickerLaunched(Instant.now());
        } catch (Exception e) {
            Toast.makeText(getContext(), "Your device is missing an activity for Intent.ACTION_OPEN_DOCUMENT_TREE. Contact the manufacturer of the device.", Toast.LENGTH_SHORT).show();
            logger.error("Broken device. No Activity for Intent.ACTION_OPEN_DOCUMENT_TREE", e);
        }
    }

    private void initiateBackup() {
        if (BackupService.isRunning()) {
            //show toast
            Toast.makeText(ThreemaApplication.getAppContext(), R.string.backup_in_progress, Toast.LENGTH_SHORT).show();
        } else {
            if (backupUri == null) {
                showPathSelectionIntro();
            } else {
                if (isFileUri(backupUri)) {
                    checkBatteryOptimizations();
                    return;
                }
                DocumentFile documentFile = null;
                try {
                    documentFile = DocumentFile.fromTreeUri(ThreemaApplication.getAppContext(), backupUri);
                } catch (IllegalArgumentException e) {
                    logger.error("DocumentFile.fromTreeUri failed", e);
                }
                if (documentFile == null || !documentFile.exists()) {
                    showPathSelectionIntro();
                } else {
                    checkBatteryOptimizations();
                }
            }
        }
    }

    private void checkBatteryOptimizations() {
        if (ConfigUtils.requestWriteStoragePermissions(getActivity(), this, PERMISSION_REQUEST_STORAGE_DOBACKUP)) {
            logger.info("Launching battery optimization settings");
            Intent intent = new Intent(getActivity(), DisableBatteryOptimizationsActivity.class);
            intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_NAME, getString(R.string.backup_data));
            startActivityForResult(intent, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS);
        }
    }

    private void launchDataBackup(@Nullable String password, boolean includeMedia) {
        if (password != null) {
            logger.info("Launching data backup");
            final BackupRestoreDataConfig backupRestoreDataConfig = new BackupRestoreDataConfig(password);
            backupRestoreDataConfig
                .setBackupContactAndMessages(true)
                .setBackupIdentity(true)
                .setBackupAvatars(true)
                .setBackupMedia(includeMedia)
                .setBackupThumbnails(includeMedia)

                .setBackupNonces(true).setBackupReactions(true);

            Intent intent = new Intent(getActivity(), BackupService.class);
            intent.putExtra(BackupService.EXTRA_BACKUP_RESTORE_DATA_CONFIG, backupRestoreDataConfig);
            ContextCompat.startForegroundService(getActivity(), intent);
            Toast.makeText(getActivity(), R.string.backup_started, Toast.LENGTH_SHORT).show();
            getActivity().finishAffinity();
        }
    }

    private void doBackup() {
        DialogFragment dialogFragment = PasswordEntryDialog.newInstance(
            R.string.backup_data_new,
            R.string.backup_data_password_msg,
            R.string.password_hint,
            R.string.ok,
            R.string.cancel,
            AppConstants.MIN_PW_LENGTH_BACKUP,
            AppConstants.MAX_PW_LENGTH_BACKUP,
            R.string.backup_password_again_summary,
            0,
            R.string.backup_data_media,
            R.string.backup_data_media_confirm);
        dialogFragment.setTargetFragment(this, 0);
        dialogFragment.show(getFragmentManager(), DIALOG_TAG_PASSWORD);
    }

    @Override
    public void onYes(String tag, String text, boolean isChecked, Object data) {
        switch (tag) {
            case DIALOG_TAG_PASSWORD:
                launchDataBackup(text, isChecked);
                break;
        }
    }

    @Override
    public void onYes(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_ENERGY_SAVING_REMINDER:
                doBackup();
                break;
            case DIALOG_TAG_FALLBACK_PATH:
            case DIALOG_TAG_PATH_INTRO:
                launchDocumentTree();
                break;
            default:
                break;
        }
    }

    @Override
    public void onNo(String tag, Object data) {
        switch (tag) {
            case DIALOG_TAG_DISABLE_ENERGY_SAVING:
                doBackup();
                break;
            case DIALOG_TAG_FALLBACK_PATH:
                useFallbackPath();
                break;
            default:
                break;
        }
    }

    private void useFallbackPath() {
        logger.info("Using Documents dir as fallback");
        preferenceService.setDataBackupPickerLaunched(null);
        var documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        documentsDirectory.mkdirs();
        setBackupUri(Uri.fromFile(documentsDirectory));
    }

    private void setBackupUri(Uri backupUri) {
        this.backupUri = backupUri;
        preferenceService.setDataBackupUri(backupUri);
        if (pathTextView != null) {
            pathTextView.setText(getDirectoryName(backupUri));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_STORAGE_DOBACKUP:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkBatteryOptimizations();
                } else {
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        ConfigUtils.showPermissionRationale(getContext(), fragmentView, R.string.permission_storage_required);
                    }
                }
                break;
        }
    }

    @UiThread
    private void onInfoButtonClicked(View v) {
        SimpleStringAlertDialog.newInstance(R.string.backup_data, R.string.data_backup_explain).show(getFragmentManager(), "tse");
    }

    private void onRestoreButtonClicked(View v) {
        SimpleStringAlertDialog.newInstance(R.string.restore, R.string.restore_data_backup_explain).show(getFragmentManager(), "re");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
                GenericAlertDialog dialog;
                dialog = GenericAlertDialog.newInstance(R.string.backup_data_title, R.string.restore_disable_energy_saving, R.string.ok, R.string.cancel);
                dialog.setTargetFragment(this, 0);
                dialog.show(getFragmentManager(), DIALOG_TAG_ENERGY_SAVING_REMINDER);
                break;
            case REQUEST_CODE_DOCUMENT_TREE:
                logger.info("Document picker returned, {}", resultCode);
                preferenceService.setDataBackupPickerLaunched(null);
                if (resultCode == RESULT_OK) {
                    if (data != null) {
                        Uri treeUri = data.getData();
                        if (treeUri != null) {
                            // Persist access permissions.
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                            try {
                                getContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                            } catch (SecurityException e) {
                                logger.error("Exception", e);
                            }
                            backupUri = treeUri;
                            preferenceService.setDataBackupUri(treeUri);
                            pathTextView.setText(getDirectoryName(treeUri));

                            if (launchedFromFAB) {
                                checkBatteryOptimizations();
                            }

                            return;
                        }
                    }
                    Toast.makeText(getContext(), "Unable to set new path", Toast.LENGTH_LONG).show();
                }
        }
    }
}
