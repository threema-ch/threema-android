package ch.threema.app.storagemanagement

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.ArrayRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.threema.android.ToastDuration
import ch.threema.android.awaitLayout
import ch.threema.android.buildActivityIntent
import ch.threema.android.context
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.di.DIJavaCompat.isSessionScopeReady
import ch.threema.app.dialogs.CancelableHorizontalProgressDialog
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.reset.ResetAppTask
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ConversationService
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.services.UserService
import ch.threema.app.startup.finishAndRestartLaterIfNotReady
import ch.threema.app.storagemanagement.usecases.GetStorageSizeUseCase
import ch.threema.app.ui.InsetSides.Companion.lbr
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.AutoDeleteUtil.getDifferenceDays
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.logScreenVisibility
import ch.threema.app.workers.AutoDeleteWorker
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.consume
import ch.threema.storage.models.MessageType
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.util.Date
import kotlin.system.exitProcess
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("StorageManagementActivity")

class StorageManagementActivity : ThreemaToolbarActivity(), DialogClickListener, CancelableHorizontalProgressDialog.ProgressDialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val conversationService: ConversationService by inject()
    private val fileService: FileService by inject()
    private val messageService: MessageService by inject()
    private val preferenceService: PreferenceService by inject()
    private val userService: UserService by inject()
    private val getStorageSizeUseCase: GetStorageSizeUseCase by inject()
    private val resetAppTask: ResetAppTask by inject()
    private val appRestrictions: AppRestrictions by inject()
    private val autoDeleteWorkerScheduler: AutoDeleteWorker.Scheduler by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private lateinit var totalView: TextView
    private lateinit var usageView: TextView
    private lateinit var freeView: TextView
    private lateinit var messageView: TextView
    private lateinit var inUseView: TextView
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var keepMessagesSpinner: MaterialAutoCompleteTextView
    private lateinit var storageFull: FrameLayout
    private lateinit var storageThreema: FrameLayout
    private lateinit var storageEmpty: FrameLayout
    private lateinit var coordinatorLayout: CoordinatorLayout

    private var messageDeletionJob: Job? = null
    private var mediaDeletionJob: Job? = null

    private var selectedSpinnerItem = 0
    private var selectedMessageSpinnerItem = 0
    private var selectedKeepMessageSpinnerItem = 0

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishAndRestartLaterIfNotReady()) {
            return
        }
        setUpActionBar()
        if (!userService.hasIdentity()) {
            showDeleteAllDialog()
            return
        }
        setUpViews()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateStorageDisplay()
            }
        }
    }

    private fun setUpViews() {
        coordinatorLayout = findViewById(R.id.content)
        totalView = findViewById(R.id.total_view)
        usageView = findViewById(R.id.usage_view)
        freeView = findViewById(R.id.free_view)
        inUseView = findViewById(R.id.in_use_view)
        messageView = findViewById(R.id.num_messages_view)
        keepMessagesSpinner = findViewById(R.id.keep_messages_spinner)
        storageFull = findViewById(R.id.storage_full)
        storageThreema = findViewById(R.id.storage_threema)
        storageEmpty = findViewById(R.id.storage_empty)
        progressBar = findViewById(R.id.progressbar)

        findViewById<ImageButton>(R.id.auto_delete_info).setOnClickListener {
            showAutoDeleteDialog()
        }

        selectedSpinnerItem = 0
        selectedMessageSpinnerItem = 0

        findViewById<TextView>(R.id.used_by_threema).text = getString(R.string.storage_threema, getString(R.string.app_name))

        findViewById<Button>(R.id.delete_button).setOnClickListener {
            showDeleteDataNowDialog()
        }

        findViewById<Button>(R.id.delete_button_messages).setOnClickListener {
            showDeleteMessagesNowDialog()
        }

        findViewById<Button>(R.id.delete_everything_button).let { deleteAllButton ->
            if (appRestrictions.isReadOnlyProfile()) {
                // In readonly profile the user should not be able to delete their ID
                deleteAllButton.isVisible = false
            } else {
                deleteAllButton.setOnClickListener {
                    showDeleteIdentityDialog()
                }
            }
        }

        findViewById<MaterialAutoCompleteTextView>(R.id.time_spinner).let { timeSpinner ->
            val adapter = createSimpleDropdownAdapter(R.array.storagemanager_timeout)
            timeSpinner.setAdapter(adapter)
            timeSpinner.setText(adapter.getItem(selectedSpinnerItem), false)
            timeSpinner.setOnItemClickListener { _, _, position: Int, _ ->
                selectedSpinnerItem = position
            }
        }

        findViewById<MaterialAutoCompleteTextView>(R.id.time_spinner_messages).let { messageTimeSpinner ->
            val messageCleanupAdapter = createSimpleDropdownAdapter(R.array.storagemanager_timeout)
            messageTimeSpinner.setAdapter(messageCleanupAdapter)
            messageTimeSpinner.setText(messageCleanupAdapter.getItem(selectedMessageSpinnerItem), false)
            messageTimeSpinner.setOnItemClickListener { _, _, position: Int, _ ->
                selectedMessageSpinnerItem = position
            }
        }

        val keepMessagesForDays = appRestrictions.getKeepMessagesDays()
        if (keepMessagesForDays != null) {
            findViewById<View>(R.id.keep_messages_spinner_layout).isEnabled = false
            keepMessagesSpinner.isEnabled = false
            findViewById<View>(R.id.disabled_by_policy).isVisible = true
            keepMessagesSpinner.setText(
                if (keepMessagesForDays <= 0) {
                    getString(R.string.forever)
                } else {
                    getString(R.string.number_of_days, keepMessagesForDays)
                },
            )
        } else {
            val autoDeleteDays = preferenceService.getAutoDeleteDays()
            selectedKeepMessageSpinnerItem = keepMessagesValues
                .drop(1)
                .indexOfFirst { keepMessagesValue ->
                    keepMessagesValue <= autoDeleteDays
                }
                .plus(1)

            val keepMessagesAdapter = createSimpleDropdownAdapter(R.array.keep_messages_timeout)
            keepMessagesSpinner.setAdapter(keepMessagesAdapter)
            keepMessagesSpinner.setText(keepMessagesAdapter.getItem(selectedKeepMessageSpinnerItem), false)
            keepMessagesSpinner.setOnItemClickListener { _, _, position: Int, _ ->
                if (position == selectedKeepMessageSpinnerItem) {
                    return@setOnItemClickListener
                }
                val selectedDays = keepMessagesValues[position]
                if (selectedDays > 0) {
                    showAutoDeleteConfirmDialog(position)
                } else {
                    selectedKeepMessageSpinnerItem = position
                    preferenceService.setAutoDeleteDays(selectedDays)
                    showToast(R.string.autodelete_disabled, ToastDuration.LONG)
                    autoDeleteWorkerScheduler.scheduleAutoDelete()
                }
            }
        }
    }

    private fun setUpActionBar() {
        supportActionBar?.let { actionBar ->
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.storage_management)
        }
    }

    private fun showDeleteAllDialog() {
        GenericAlertDialog.newInstance(
            R.string.delete_data,
            R.string.delete_all_data_prompt,
            R.string.delete_data,
            R.string.cancel,
        ).show(supportFragmentManager, DELETE_ALL_APP_DATA_TAG)
    }

    private fun createSimpleDropdownAdapter(@ArrayRes values: Int): ArrayAdapter<CharSequence> =
        ArrayAdapter.createFromResource(this, values, android.R.layout.simple_spinner_dropdown_item)

    private fun showAutoDeleteDialog() {
        SimpleStringAlertDialog.newInstance(
            R.string.delete_automatically,
            R.string.autodelete_explain,
        ).show(supportFragmentManager, "autoDel")
    }

    private fun showDeleteDataNowDialog() {
        GenericAlertDialog.newInstance(R.string.delete_data, R.string.delete_date_confirm_message, R.string.delete_data, R.string.cancel)
            .show(supportFragmentManager, DELETE_CONFIRM_TAG)
    }

    private fun showDeleteMessagesNowDialog() {
        GenericAlertDialog.newInstance(R.string.delete_message, R.string.really_delete_messages, R.string.delete_message, R.string.cancel)
            .show(supportFragmentManager, DELETE_MESSAGES_CONFIRM_TAG)
    }

    private fun showDeleteIdentityDialog() {
        GenericAlertDialog.newInstance(
            R.string.delete_id_title,
            R.string.delete_id_message,
            R.string.delete_everything,
            R.string.cancel,
        ).show(supportFragmentManager, DIALOG_TAG_DELETE_ID)
    }

    private fun showAutoDeleteConfirmDialog(data: Int) {
        val dialog = GenericAlertDialog.newInstance(
            R.string.delete_automatically,
            getString(R.string.autodelete_confirm, keepMessagesSpinner.getText()),
            R.string.yes,
            R.string.no,
        )
        dialog.setData(data)
        dialog.show(supportFragmentManager, DIALOG_TAG_SET_AUTO_DELETE)
    }

    private suspend fun updateStorageDisplay() {
        progressBar.isVisible = true

        try {
            val storageSizes = getStorageSizeUseCase.call()

            messageView.text = storageSizes.messageCount.toString()
            totalView.text = Formatter.formatFileSize(context, storageSizes.totalBytes)
            usageView.text = Formatter.formatFileSize(context, storageSizes.usedBytes)
            freeView.text = Formatter.formatFileSize(context, storageSizes.freeBytes)

            if (storageSizes.totalBytes > 0) {
                inUseView.text = Formatter.formatFileSize(context, storageSizes.totalBytes - storageSizes.freeBytes)
                storageFull.awaitLayout()
                val fullWidth = storageFull.width
                storageThreema.setLayoutParams(
                    FrameLayout.LayoutParams(
                        (fullWidth * storageSizes.usedBytes / storageSizes.totalBytes).toInt(),
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                val params = FrameLayout.LayoutParams(
                    (fullWidth * storageSizes.freeBytes / storageSizes.totalBytes).toInt(),
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
                params.gravity = Gravity.RIGHT
                storageEmpty.setLayoutParams(params)
            } else {
                inUseView.text = Formatter.formatFileSize(context, 0)
                storageFull.isVisible = false
                storageThreema.isVisible = false
                storageEmpty.isVisible = false
            }
        } finally {
            progressBar.isVisible = false
        }
    }

    private fun deleteMessages(days: Int) {
        messageDeletionJob?.cancel()
        messageDeletionJob = lifecycleScope.launch {
            try {
                showMessageDeletionProgressDialog()

                val deletionCount = withContext(dispatcherProvider.worker) {
                    val today = Date()
                    var deletionCount = 0
                    val conversationModels = conversationService.getAll(true).toList()
                    val conversationsCount = conversationModels.size
                    conversationModels.forEachIndexed { index, conversationModel ->
                        ensureActive()
                        updateMessageDeletionProgressDialog(percent = (index * 100) / conversationsCount)
                        messageService.getMessagesForReceiver(conversationModel.messageReceiver, null)
                            .let { messageModels ->
                                if (days != 0) {
                                    messageModels.filter { messageModel ->
                                        val postedDate = messageModel.postedAt ?: messageModel.createdAt
                                        postedDate != null && getDifferenceDays(postedDate, today) > days
                                    }
                                } else {
                                    messageModels
                                }
                            }
                            .forEach { messageModel ->
                                ensureActive()
                                messageService.remove(messageModel, true)
                                deletionCount++
                            }
                    }
                    deletionCount
                }

                Snackbar.make(
                    coordinatorLayout,
                    resources.getQuantityString(R.plurals.message_deleted, deletionCount, deletionCount),
                    Snackbar.LENGTH_LONG,
                ).show()
            } finally {
                dismissMessageDeletionProgressDialog()
                updateStorageDisplay()
                conversationService.reset()
                ListenerManager.conversationListeners.handle { listener -> listener.onModifiedAll() }
            }
        }
    }

    private fun showMessageDeletionProgressDialog() {
        CancelableHorizontalProgressDialog.newInstance(R.string.delete_message, R.string.cancel, 100)
            .show(supportFragmentManager, DELETE_MESSAGES_PROGRESS_TAG)
    }

    private suspend fun updateMessageDeletionProgressDialog(percent: Int) = withContext(dispatcherProvider.main) {
        DialogUtil.updateProgress(supportFragmentManager, DELETE_MESSAGES_PROGRESS_TAG, percent)
    }

    private fun dismissMessageDeletionProgressDialog() {
        DialogUtil.dismissDialog(supportFragmentManager, DELETE_MESSAGES_PROGRESS_TAG, true)
    }

    private fun deleteMediaFiles(days: Int) {
        mediaDeletionJob?.cancel()
        mediaDeletionJob = lifecycleScope.launch {
            showMediaDeletionProgressDialog()

            try {
                val deletionCount = withContext(dispatcherProvider.worker) {
                    val today = Date()
                    var deletionCount = 0
                    val conversationModels = conversationService.getAll(true).toList()
                    val conversationCount = conversationModels.size
                    val messageFilter = getMessageFilterForMediaDeletion()

                    conversationModels.forEachIndexed { index, conversationModel ->
                        ensureActive()
                        updateMediaDeletionProgressDialog(percent = (index * 100) / conversationCount)

                        messageService.getMessagesForReceiver(conversationModel.messageReceiver, messageFilter)
                            .let { messageModels ->
                                if (days != 0) {
                                    messageModels.filter { messageModel ->
                                        val postedDate = messageModel.postedAt ?: messageModel.createdAt
                                        (postedDate != null && getDifferenceDays(postedDate, today) > days)
                                    }
                                } else {
                                    messageModels
                                }
                            }
                            .forEach { messageModel ->
                                ensureActive()
                                val fileRemoved = fileService.removeMessageFiles(messageModel, false)
                                if (fileRemoved) {
                                    deletionCount++
                                }
                            }
                    }
                    deletionCount
                }

                Snackbar.make(
                    coordinatorLayout,
                    resources.getQuantityString(R.plurals.media_files_deleted, deletionCount, deletionCount),
                    Snackbar.LENGTH_LONG,
                ).show()
            } finally {
                dismissMediaDeletionProgressDialog()
                updateStorageDisplay()
                conversationService.reset()
                ListenerManager.conversationListeners.handle { listener -> listener.onModifiedAll() }
            }
        }
    }

    private fun showMediaDeletionProgressDialog() {
        CancelableHorizontalProgressDialog.newInstance(R.string.delete_data, R.string.cancel, 100)
            .show(supportFragmentManager, DELETE_MEDIA_PROGRESS_TAG)
    }

    private suspend fun updateMediaDeletionProgressDialog(percent: Int) = withContext(dispatcherProvider.main) {
        DialogUtil.updateProgress(supportFragmentManager, DELETE_MEDIA_PROGRESS_TAG, percent)
    }

    private fun dismissMediaDeletionProgressDialog() {
        DialogUtil.dismissDialog(supportFragmentManager, DELETE_MEDIA_PROGRESS_TAG, true)
    }

    override fun onYes(tag: String?, data: Any?) {
        when (tag) {
            DELETE_CONFIRM_TAG -> {
                deleteMediaFiles(dayValues[selectedSpinnerItem])
            }
            DELETE_MESSAGES_CONFIRM_TAG -> {
                deleteMessages(dayValues[selectedMessageSpinnerItem])
            }
            DIALOG_TAG_DELETE_ID -> {
                showDeleteEverythingDialog()
            }
            DIALOG_TAG_REALLY_DELETE -> {
                showDeletingDialog()
                runAppResetTask()
            }
            DIALOG_TAG_SET_AUTO_DELETE -> {
                if (data != null) {
                    val spinnerItemPosition = data as Int
                    selectedKeepMessageSpinnerItem = spinnerItemPosition
                    configureAutoDelete(keepMessagesValues[spinnerItemPosition])
                }
            }
            DELETE_ALL_APP_DATA_TAG -> {
                clearAppData()
            }
        }
    }

    private fun showDeleteEverythingDialog() {
        GenericAlertDialog.newInstance(
            R.string.delete_id_title,
            R.string.delete_id_message2,
            R.string.delete_everything,
            R.string.cancel,
        ).show(supportFragmentManager, DIALOG_TAG_REALLY_DELETE)
    }

    private fun showDeletingDialog() {
        GenericProgressDialog.newInstance(R.string.delete_id_title, R.string.please_wait)
            .show(supportFragmentManager, DIALOG_TAG_DELETING_ID)
    }

    private fun runAppResetTask() {
        lifecycleScope.launch {
            try {
                resetAppTask.execute()
            } catch (_: Exception) {
                // if the app reset fails, it is likely that the app is left in a broken state, so we better stop it altogether.
                exitProcess(0)
            }
        }
    }

    private fun configureAutoDelete(days: Int) {
        showToast(R.string.autodelete_activated, ToastDuration.LONG)
        preferenceService.setAutoDeleteDays(days)
        autoDeleteWorkerScheduler.scheduleAutoDelete()
    }

    private fun clearAppData() {
        getSystemService<ActivityManager>()?.clearApplicationUserData()
    }

    override fun onNo(tag: String?, data: Any?) {
        when (tag) {
            DIALOG_TAG_SET_AUTO_DELETE -> {
                keepMessagesSpinner.setText(
                    (keepMessagesSpinner.adapter as ArrayAdapter<CharSequence>).getItem(selectedKeepMessageSpinnerItem),
                    false,
                )
            }
            DELETE_ALL_APP_DATA_TAG -> {
                finish()
            }
        }
    }

    override fun onCancel(tag: String, `object`: Any?) {
        when (tag) {
            DELETE_MEDIA_PROGRESS_TAG -> mediaDeletionJob?.cancel()
            DELETE_MESSAGES_PROGRESS_TAG -> messageDeletionJob?.cancel()
        }
    }

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()
        findViewById<View?>(R.id.scroll_container).applyDeviceInsetsAsPadding(lbr())
    }

    override fun getLayoutResource() =
        when {
            !isSessionScopeReady() || !userService.hasIdentity() -> R.layout.activity_storagemanagement_empty
            else -> R.layout.activity_storagemanagement
        }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home -> consume {
                finish()
            }
            else -> super.onOptionsItemSelected(item)
        }

    companion object {
        private const val DELETE_ALL_APP_DATA_TAG = "delallappdata"
        private const val DELETE_CONFIRM_TAG = "delconf"
        private const val DELETE_MEDIA_PROGRESS_TAG = "delprog"
        private const val DELETE_MESSAGES_CONFIRM_TAG = "delmsgsconf"
        private const val DELETE_MESSAGES_PROGRESS_TAG = "delmsgs"
        private const val DIALOG_TAG_DELETE_ID = "delid"
        private const val DIALOG_TAG_REALLY_DELETE = "rlydelete"
        private const val DIALOG_TAG_SET_AUTO_DELETE = "autodelete"
        private const val DIALOG_TAG_DELETING_ID = "di"

        private val dayValues = intArrayOf(730, 365, 183, 92, 31, 7, 0)
        private val keepMessagesValues = intArrayOf(0, 365, 183, 92, 31, 7)

        fun createIntent(context: Context) = buildActivityIntent<StorageManagementActivity>(context)

        private fun getMessageFilterForMediaDeletion() = object : MessageService.MessageFilter {
            override fun getPageSize() = 0L

            override fun getPageReferenceId() = null

            override fun withStatusMessages() = false

            override fun withUnsaved() = true

            override fun onlyUnread() = false

            override fun onlyDownloaded() = true

            override fun types() = arrayOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.VOICEMESSAGE, MessageType.FILE)

            override fun contentTypes() = null

            override fun displayTags() = null
        }
    }
}
