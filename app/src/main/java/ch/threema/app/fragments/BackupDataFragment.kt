package ch.threema.app.fragments

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import ch.threema.android.ToastDuration
import ch.threema.android.isFileUri
import ch.threema.android.registerPermissionResultContract
import ch.threema.android.registerSimpleActivityResultContract
import ch.threema.android.showToast
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.activities.DisableBatteryOptimizationsActivity
import ch.threema.app.backuprestore.BackupRestoreDataConfig
import ch.threema.app.backuprestore.csv.BackupService
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.dialogs.PasswordEntryDialog
import ch.threema.app.dialogs.PasswordEntryDialog.PasswordEntryDialogClickListener
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.app.ui.InsetSides.Companion.lbr
import ch.threema.app.ui.SpacingValues.Companion.all
import ch.threema.app.ui.SpacingValues.Companion.bottom
import ch.threema.app.ui.applyDeviceInsetsAsMargin
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.LocaleUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.minus
import ch.threema.common.takeUnlessEmpty
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("BackupDataFragment")

class BackupDataFragment : Fragment(), DialogClickListener, PasswordEntryDialogClickListener {

    private val fileService: FileService by inject()
    private val preferenceService: PreferenceService by inject()

    private var backupUri: Uri? = null
    private var launchedFromFAB = false

    private var fragmentView: View? = null
    private var pathTextView: TextView? = null

    private val pickBackupDirectoryLauncher = registerSimpleActivityResultContract { result ->
        logger.info("Document picker returned, {}", result.resultCode)
        preferenceService.setDataBackupPickerLaunched(null)
        if (result.resultCode == Activity.RESULT_OK) {
            handlePickedDirectoryResult(result.data?.data)
        }
    }
    private val disableBatteryOptimizationsLauncher = registerSimpleActivityResultContract {
        showEnergySavingDialog()
    }
    private val requestStorageWritePermissionLauncher = registerPermissionResultContract { isGranted ->
        if (isGranted) {
            checkBatteryOptimizations()
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ConfigUtils.showPermissionRationale(requireContext(), fragmentView, R.string.permission_storage_required)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backupUri = fileService.getBackupUri()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (this.fragmentView == null) {
            val fragmentView = inflater.inflate(R.layout.fragment_backup_data, container, false)
            this.fragmentView = fragmentView

            val fab = fragmentView.findViewById<ExtendedFloatingActionButton>(R.id.floating)
            fab.setOnClickListener {
                logger.info("FAB clicked, initiating backup")
                launchedFromFAB = true
                initiateBackup()
            }
            fab.show()
            fab.applyDeviceInsetsAsMargin(lbr(), all(R.dimen.grid_unit_x2))

            val scrollView = fragmentView.findViewById<NestedScrollView>(R.id.scroll_parent)
            scrollView.setOnScrollChangeListener { nestedScrollView: NestedScrollView, _, scrollY: Int, _, _ ->
                if (nestedScrollView.top == scrollY) {
                    fab.extend()
                } else {
                    fab.shrink()
                }
            }

            scrollView.applyDeviceInsetsAsPadding(lbr(), bottom(R.dimen.grid_unit_x10))

            fragmentView.findViewById<View>(R.id.info).setOnClickListener { showInfoDialog() }
            fragmentView.findViewById<View>(R.id.restore).setOnClickListener { showRestoreDialog() }

            fragmentView.findViewById<View>(R.id.backup_path_change_btn).setOnClickListener {
                logger.info("Path change button clicked")
                launchedFromFAB = false
                startPathSelection()
            }

            val pathTextView = fragmentView.findViewById<TextView>(R.id.backup_path)
            this.pathTextView = pathTextView
            pathTextView.text = getDirectoryName(backupUri)
        }

        val fragmentView = fragmentView!!
        val backupTimestamp = preferenceService.getLastDataBackupTimestamp()
        if (backupTimestamp != null) {
            fragmentView.findViewById<View>(R.id.last_backup_layout).isVisible = true
            fragmentView.findViewById<View>(R.id.intro_layout).isVisible = false
            fragmentView.findViewById<TextView>(R.id.last_backup_date).text =
                LocaleUtil.formatTimeStampStringAbsolute(requireContext(), backupTimestamp)
        } else {
            fragmentView.findViewById<View>(R.id.last_backup_layout).isVisible = false
            fragmentView.findViewById<View>(R.id.intro_layout).isVisible = true
        }

        return fragmentView
    }

    private fun getDirectoryName(backupUri: Uri?): String =
        when {
            backupUri == null -> getString(R.string.not_set)
            backupUri.isFileUri() -> backupUri.lastPathSegment
            else -> backupUri.getDocumentTreeDirectoryName(requireContext())
        }
            ?: backupUri.toString()

    private fun startPathSelection() {
        if (hasDocumentPickerPotentiallyFailedRecently()) {
            showFallbackPathSelectionDialog()
        } else {
            showPathSelectionIntro()
        }
    }

    private fun hasDocumentPickerPotentiallyFailedRecently(): Boolean {
        val lastLaunchTime = preferenceService.getDataBackupPickerLaunched()
            ?: return false
        return Instant.now() - lastLaunchTime < 10.minutes
    }

    private fun showPathSelectionIntro() {
        val dialog = GenericAlertDialog.newInstance(
            /* title = */
            R.string.set_backup_path,
            /* message = */
            R.string.set_backup_path_intro,
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
        )
        dialog.setTargetFragment(this)
        dialog.show(parentFragmentManager, DIALOG_TAG_PATH_INTRO)
    }

    private fun showFallbackPathSelectionDialog() {
        logger.info("Showing fallback path selection dialog")
        val dialog = GenericAlertDialog.newInstance(
            /* title = */
            R.string.set_backup_path,
            /* message = */
            R.string.backup_path_selection_message,
            /* positive = */
            R.string.backup_path_selection_default,
            /* negative = */
            R.string.backup_path_selection_documents,
        )
        dialog.setTargetFragment(this)
        dialog.show(parentFragmentManager, DIALOG_TAG_FALLBACK_PATH)
    }

    @UiThread
    private fun launchDocumentTree() {
        try {
            logger.info("Opening document picker")
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            if (!hasDocumentPickerPotentiallyFailedRecently()) {
                // undocumented APIs according to https://issuetracker.google.com/issues/72053350
                // if we detected that the document picker might have failed, we excluded these, as they might be part of the problem
                intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
                intent.putExtra("android.content.extra.FANCY", true)
                intent.putExtra("android.content.extra.SHOW_FILESIZE", true)
            }
            pickBackupDirectoryLauncher.launch(intent)
            preferenceService.setDataBackupPickerLaunched(Instant.now())
        } catch (e: Exception) {
            // TODO(ANDR-4447): This toast message should be localized
            showToast("Your device is missing an activity for Intent.ACTION_OPEN_DOCUMENT_TREE. Contact the manufacturer of the device.")
            logger.error("Broken device. No Activity for Intent.ACTION_OPEN_DOCUMENT_TREE", e)
        }
    }

    private fun handlePickedDirectoryResult(treeUri: Uri?) {
        if (treeUri == null) {
            // TODO(ANDR-4447): This toast message should be localized
            showToast("Unable to set new path", ToastDuration.LONG)
            return
        }

        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            requireContext().contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        } catch (e: SecurityException) {
            logger.error("Failed to take persistable uri permission", e)
        }
        setBackupUri(treeUri)

        if (launchedFromFAB) {
            checkBatteryOptimizations()
        }
    }

    private fun setBackupUri(backupUri: Uri) {
        this.backupUri = backupUri
        preferenceService.setDataBackupUri(backupUri)
        pathTextView?.text = getDirectoryName(backupUri)
    }

    private fun initiateBackup() {
        if (BackupService.isRunning()) {
            showToast(R.string.backup_in_progress)
            return
        }
        val backupUri = backupUri
        if (backupUri?.isFileUri() == true || backupUri?.isExistingDocumentTreeUri(requireContext()) == true) {
            checkBatteryOptimizations()
        } else {
            startPathSelection()
        }
    }

    private fun checkBatteryOptimizations() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestStorageWritePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            showDisableBatteryOptimizations()
        }
    }

    private fun showDisableBatteryOptimizations() {
        logger.info("Launching battery optimization settings")
        val intent = DisableBatteryOptimizationsActivity.createIntent(requireContext(), getString(R.string.backup_data))
        disableBatteryOptimizationsLauncher.launch(intent)
    }

    private fun launchDataBackup(password: String, includeMedia: Boolean) {
        logger.info("Launching data backup")
        val backupRestoreDataConfig = BackupRestoreDataConfig(password).apply {
            setBackupContactAndMessages(true)
            setBackupIdentity(true)
            setBackupAvatars(true)
            setBackupMedia(includeMedia)
            setBackupThumbnails(includeMedia)
            setBackupNonces(true)
            setBackupReactions(true)
        }

        val intent = Intent(requireContext(), BackupService::class.java)
        intent.putExtra(BackupService.EXTRA_BACKUP_RESTORE_DATA_CONFIG, backupRestoreDataConfig)
        ContextCompat.startForegroundService(requireContext(), intent)
        showToast(R.string.backup_started)
        requireActivity().finishAffinity()
    }

    private fun doBackup() {
        val dialogFragment: DialogFragment = PasswordEntryDialog.newInstance(
            /* title = */
            R.string.backup_data_new,
            /* message = */
            R.string.backup_data_password_msg,
            /* hint = */
            R.string.password_hint,
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
            /* minLength = */
            AppConstants.MIN_PW_LENGTH_BACKUP,
            /* maxLength = */
            AppConstants.MAX_PW_LENGTH_BACKUP,
            /* confirmHint = */
            R.string.backup_password_again_summary,
            /* inputType = */
            0,
            /* checkboxText = */
            R.string.backup_data_media,
            /* checkboxConfirmText = */
            R.string.backup_data_media_confirm,
        )
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_PASSWORD)
    }

    override fun onYes(tag: String?, text: String, isChecked: Boolean, data: Any?) {
        if (tag == DIALOG_TAG_PASSWORD) {
            launchDataBackup(password = text, includeMedia = isChecked)
        }
    }

    override fun onYes(tag: String?, data: Any?) {
        when (tag) {
            DIALOG_TAG_ENERGY_SAVING_REMINDER -> doBackup()
            DIALOG_TAG_PATH_INTRO,
            DIALOG_TAG_FALLBACK_PATH,
            -> launchDocumentTree()
        }
    }

    override fun onNo(tag: String?, data: Any?) {
        when (tag) {
            DIALOG_TAG_DISABLE_ENERGY_SAVING -> doBackup()
            DIALOG_TAG_FALLBACK_PATH -> useFallbackPath()
        }
    }

    private fun useFallbackPath() {
        logger.info("Using Documents dir as fallback")
        preferenceService.setDataBackupPickerLaunched(null)
        val documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        documentsDirectory.mkdirs()
        setBackupUri(documentsDirectory.toUri())
    }

    private fun showInfoDialog() {
        SimpleStringAlertDialog.newInstance(R.string.backup_data, R.string.data_backup_explain)
            .show(parentFragmentManager, "tse")
    }

    private fun showRestoreDialog() {
        SimpleStringAlertDialog.newInstance(R.string.restore, R.string.restore_data_backup_explain)
            .show(parentFragmentManager, "re")
    }

    private fun showEnergySavingDialog() {
        val dialog = GenericAlertDialog.newInstance(R.string.backup_data_title, R.string.restore_disable_energy_saving, R.string.ok, R.string.cancel)
        dialog.setTargetFragment(this, 0)
        dialog.show(parentFragmentManager, DIALOG_TAG_ENERGY_SAVING_REMINDER)
    }

    companion object {
        private const val DIALOG_TAG_ENERGY_SAVING_REMINDER = "esr"
        private const val DIALOG_TAG_DISABLE_ENERGY_SAVING = "des"
        private const val DIALOG_TAG_PASSWORD = "pwd"
        private const val DIALOG_TAG_PATH_INTRO = "pathintro"
        private const val DIALOG_TAG_FALLBACK_PATH = "pathfallback"

        private fun Uri.isExistingDocumentTreeUri(context: Context) =
            try {
                DocumentFile.fromTreeUri(context, this)?.exists() == true
            } catch (_: IllegalArgumentException) {
                false
            }

        private fun Uri.getDocumentTreeDirectoryName(context: Context) =
            try {
                DocumentFile.fromTreeUri(context, this)
                    ?.takeIf { it.isDirectory }
                    ?.name
                    ?.takeUnlessEmpty()
            } catch (_: Exception) {
                null
            }
    }
}
