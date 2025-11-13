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

package ch.threema.app.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.AsyncTask
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import ch.threema.app.AppConstants
import ch.threema.app.AppConstants.MAX_PW_LENGTH_BACKUP
import ch.threema.app.AppConstants.MIN_PW_LENGTH_BACKUP
import ch.threema.app.R
import ch.threema.app.ThreemaApplication.Companion.getAppContext
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.activities.DistributionListAddActivity.Companion.createIntent
import ch.threema.app.activities.RecipientListBaseActivity
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.adapters.MessageListAdapter
import ch.threema.app.adapters.MessageListAdapterItem
import ch.threema.app.adapters.MessageListViewHolder
import ch.threema.app.archive.ArchiveActivity
import ch.threema.app.asynctasks.EmptyOrDeleteConversationsAsyncTask
import ch.threema.app.backuprestore.BackupChatService
import ch.threema.app.contactdetails.ContactDetailActivity
import ch.threema.app.dialogs.CancelableGenericProgressDialog
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.dialogs.PasswordEntryDialog
import ch.threema.app.dialogs.PasswordEntryDialog.PasswordEntryDialogClickListener
import ch.threema.app.dialogs.SelectorDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.dialogs.loadingtimeout.LoadingWithTimeoutDialogXml
import ch.threema.app.groupflows.GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS
import ch.threema.app.groupflows.GroupDisbandIntent
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.groupflows.GroupLeaveIntent
import ch.threema.app.listeners.ChatListener
import ch.threema.app.listeners.ContactListener
import ch.threema.app.listeners.ContactSettingsListener
import ch.threema.app.listeners.ConversationListener
import ch.threema.app.listeners.DistributionListListener
import ch.threema.app.listeners.GroupListener
import ch.threema.app.listeners.SynchronizeContactsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.SettingsActivity
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictionUtil
import ch.threema.app.routines.SynchronizeContactsRoutine
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.MessageService
import ch.threema.app.services.RingtoneService
import ch.threema.app.services.SystemScreenLockService
import ch.threema.app.services.UserService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.ui.EmptyRecyclerView
import ch.threema.app.ui.EmptyView
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.ResumePauseHandler
import ch.threema.app.ui.SelectorDialogItem
import ch.threema.app.ui.SpacingValues
import ch.threema.app.ui.SpacingValues.Companion.all
import ch.threema.app.ui.applyDeviceInsetsAsMargin
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.EditTextUtil
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.HiddenChatUtil
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.LogUtil
import ch.threema.app.utils.MimeUtil
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.utils.WidgetUtil
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.base.utils.LoggingUtil
import ch.threema.base.utils.onCompleted
import ch.threema.common.consume
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.ConversationTag
import ch.threema.storage.models.GroupModel
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.lang.ref.WeakReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Deferred
import org.koin.android.ext.android.inject
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("ConversationsFragment")

/**
 * This is one of the tabs in the home screen. It shows the current conversations.
 */
class ConversationsFragment :
    MainFragment(),
    PasswordEntryDialogClickListener,
    DialogClickListener,
    CancelableGenericProgressDialog.ProgressDialogClickListener,
    MessageListAdapter.ItemClickListener,
    SelectorDialog.SelectorDialogClickListener {

    private val userService: UserService by inject()
    private val conversationService: ConversationService by inject()
    private val contactService: ContactService by inject()
    private val groupService: GroupService by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val groupFlowDispatcher: GroupFlowDispatcher by inject()
    private val groupCallManager: GroupCallManager by inject()
    private val messageService: MessageService by inject()
    private val distributionListService: DistributionListService by inject()
    private val backupChatService: BackupChatService by inject()
    private val conversationCategoryService: ConversationCategoryService by inject()
    private val conversationTagService: ConversationTagService by inject()
    private val ringtoneService: RingtoneService by inject()
    private val fileService: FileService by inject()
    private val preferenceService: PreferenceService by inject()
    private val lockAppService: LockAppService by inject()
    private val screenLockService: SystemScreenLockService by inject()
    private val notificationService: NotificationService by inject()

    private var activity: Activity? = null
    private var tempMessagesFile: File? = null
    private var messageListAdapter: MessageListAdapter? = null
    private var recyclerView: EmptyRecyclerView? = null
    private var searchView: SearchView? = null
    private var searchMenuItemRef: WeakReference<MenuItem>? = null
    private var toggleHiddenMenuItemRef: WeakReference<MenuItem>? = null
    private lateinit var resumePauseHandler: ResumePauseHandler
    private var currentFullSyncs = 0
    private var filterQuery: String? = null
    private var cornerRadius = 0
    private val messageListAdapterItemCache: MutableMap<ConversationModel?, MessageListAdapterItem?> = HashMap()

    private var myIdentity: Identity? = null

    private var archiveSnackbar: ArchiveSnackbar? = null

    private var selectedConversation: ConversationModel? = null
    private var floatingButtonView: ExtendedFloatingActionButton? = null

    private val messageListAdapterLock = Any()

    private val synchronizeContactsListener: SynchronizeContactsListener = object : SynchronizeContactsListener {
        override fun onStarted(startedRoutine: SynchronizeContactsRoutine) {
            if (startedRoutine.isFullSync) {
                currentFullSyncs++
            }
        }

        override fun onFinished(finishedRoutine: SynchronizeContactsRoutine) {
            if (finishedRoutine.isFullSync) {
                currentFullSyncs--

                logger.debug("synchronizeContactsListener.onFinished")
                refreshListEvent()
            }
        }

        override fun onError(finishedRoutine: SynchronizeContactsRoutine) {
            if (finishedRoutine.isFullSync) {
                currentFullSyncs--
                logger.debug("synchronizeContactsListener.onError")
                refreshListEvent()
            }
        }
    }

    private val conversationListener: ConversationListener = object : ConversationListener {
        override fun onNew(conversationModel: ConversationModel) {
            logger.debug("on new conversation")
            if (messageListAdapter == null || recyclerView == null) {
                return
            }
            // If the first item of the recycler view is visible, then scroll up
            val scrollToPosition: Int? = recyclerView!!.layoutManager?.let { layoutManager ->
                if (layoutManager is LinearLayoutManager && layoutManager.findFirstVisibleItemPosition() == 0) {
                    // By passing a large integer we simulate a "moving up" change that triggers scrolling up
                    Int.Companion.MAX_VALUE
                } else {
                    null
                }
            }
            updateList(
                scrollToPosition = scrollToPosition,
                changedPositions = listOf(conversationModel),
                runAfterSetData = null,
            )
        }

        override fun onModified(modifiedConversationModel: ConversationModel, oldPosition: Int?) {
            logger.debug("on modified conversation. old position = {}", oldPosition)
            if (messageListAdapter == null || recyclerView == null) {
                return
            }
            synchronized(messageListAdapterItemCache) {
                messageListAdapterItemCache.remove(modifiedConversationModel)
            }

            // Scroll if position changed (to top)
            updateList(
                scrollToPosition = oldPosition,
                changedPositions = listOf(modifiedConversationModel),
                runAfterSetData = null,
            )
        }

        override fun onRemoved(conversationModel: ConversationModel) {
            if (messageListAdapter != null) {
                updateList()
            }
        }

        override fun onModifiedAll() {
            logger.debug("on modified all")
            if (messageListAdapter != null && recyclerView != null) {
                updateList(
                    scrollToPosition = 0,
                    changedPositions = null,
                    runAfterSetData = {
                        RuntimeUtil.runOnUiThread { messageListAdapter!!.notifyDataSetChanged() }
                    },
                )
            }
        }
    }

    private val groupListener: GroupListener = object : GroupListener {
        override fun onNewMember(groupIdentity: GroupIdentity, identityNew: Identity?) {
            // If this user is added to an existing group
            if (myIdentity != null && myIdentity == identityNew) {
                groupService.getByGroupIdentity(groupIdentity)?.let { groupModel ->
                    fireReceiverUpdate(groupService.createReceiver(groupModel))
                }
            }
        }
    }

    private val chatListener = ChatListener { conversationUid ->
        highlightUid = conversationUid
        if (isMultiPaneEnabled(activity)) {
            messageListAdapter?.apply {
                setHighlightItem(conversationUid)
                notifyDataSetChanged()
            }
        }
    }

    private val contactSettingsListener = object : ContactSettingsListener {
        override fun onSortingChanged() {
            // ignore
        }

        override fun onNameFormatChanged() {
            logger.debug("contactSettingsListener.onNameFormatChanged")
            refreshListEvent()
        }

        override fun onAvatarSettingChanged() {
            logger.debug("contactSettingsListener.onAvatarSettingChanged")
            refreshListEvent()
        }

        override fun onInactiveContactsSettingChanged() {
        }

        override fun onNotificationSettingChanged(uid: String?) {
            logger.debug("contactSettingsListener.onNotificationSettingChanged")
            refreshListEvent()
        }
    }

    private val contactListener: ContactListener = object : ContactListener {
        override fun onModified(identity: String) {
            this.handleChange()
        }

        override fun onAvatarChanged(identity: String) {
            this.handleChange()
        }

        fun handleChange() {
            if (currentFullSyncs <= 0) {
                refreshListEvent()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        logger.info("onAttach")

        this.activity = activity
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logger.info("onCreate")

        setRetainInstance(true)
        setHasOptionsMenu(true)

        setupListeners()

        this.resumePauseHandler = ResumePauseHandler.getByActivity(this, this.activity)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logger.info("onViewCreated")

        try {
            updateList(
                scrollToPosition = null,
                changedPositions = null,
                runAfterSetData = null,
                recreate = true,
            )
        } catch (e: Exception) {
            LogUtil.exception(e, activity as AppCompatActivity?)
        }

        if (savedInstanceState != null && filterQuery.isNullOrEmpty()) {
            filterQuery = savedInstanceState.getString(BUNDLE_FILTER_QUERY)
        }
        messageListAdapter?.setFilterQuery(filterQuery)
    }

    override fun onDestroyView() {
        logger.info("onDestroyView")

        searchView = null
        searchMenuItemRef?.clear()
        messageListAdapter = null

        super.onDestroyView()
    }

    @Deprecated("Deprecated in Java")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        // move search item to popup if the lock item is visible
        searchMenuItemRef?.get()?.let { searchMenuItem ->
            if (lockAppService.isLockingEnabled()) {
                searchMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
            } else {
                searchMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        logger.debug("onCreateOptionsMenu")

        if (activity == null || isMultiPaneEnabled(activity)) {
            return
        }

        var searchMenuItem: MenuItem? = menu.findItem(R.id.menu_search_messages)
        if (searchMenuItem == null) {
            inflater.inflate(R.menu.fragment_messages, menu)

            if (this.isAdded) {
                searchMenuItem = menu.findItem(R.id.menu_search_messages)
                this.searchView = searchMenuItem.actionView as SearchView?
                this.searchView?.let { searchView ->
                    if (!filterQuery.isNullOrEmpty()) {
                        // restore filter
                        MenuItemCompat.expandActionView(searchMenuItem)
                        searchView.setQuery(filterQuery, false)
                        searchView.clearFocus()
                    }
                    searchView.setQueryHint(getString(R.string.hint_filter_list))
                    searchView.setOnQueryTextListener(queryTextListener)
                }
            }
        }

        if (searchMenuItem != null) {
            searchMenuItemRef = WeakReference<MenuItem>(searchMenuItem)
        } else {
            searchMenuItemRef?.clear()
        }
        val togglePrivateChatsMenuItem: MenuItem? = menu.findItem(R.id.menu_toggle_private_chats)
        if (togglePrivateChatsMenuItem != null) {
            toggleHiddenMenuItemRef = WeakReference<MenuItem>(togglePrivateChatsMenuItem)
        } else {
            toggleHiddenMenuItemRef?.clear()
        }
        toggleHiddenMenuItemRef?.get()?.let { menuItem: MenuItem ->
            if (!isAdded) {
                return@let
            }
            menuItem.setOnMenuItemClickListener { _ ->
                if (preferenceService.isPrivateChatsHidden()) {
                    logger.info("Requesting to show private chats")
                    requestUnhideChats()
                } else {
                    logger.info("Requesting to hide private chats")
                    preferenceService.setPrivateChatsHidden(true)
                    updateList(
                        scrollToPosition = null,
                        changedPositions = null,
                        runAfterSetData = Thread { this.firePrivateReceiverUpdate() },
                    )
                    context?.let(WidgetUtil::updateWidgets)
                }
                true
            }
            updateHiddenMenuVisibility()
        }
    }

    private fun requestUnhideChats() {
        HiddenChatUtil.launchLockCheckDialog(this, preferenceService)
    }

    val queryTextListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextChange(query: String?) = consume {
            filterQuery = query
            messageListAdapter?.let { adapter ->
                adapter.setFilterQuery(query)
                updateList(
                    scrollToPosition = 0,
                    changedPositions = null,
                    runAfterSetData = null,
                )
            }
        }

        override fun onQueryTextSubmit(query: String?): Boolean = true
    }

    private fun showConversation(conversationModel: ConversationModel) {
        conversationTagService.removeTagAndNotify(
            conversationModel,
            ConversationTag.MARKED_AS_UNREAD,
            TriggerSource.LOCAL,
        )
        conversationModel.unreadCount = 0

        // Close keyboard if search view is expanded
        if (searchView != null && !searchView!!.isIconified) {
            EditTextUtil.hideSoftKeyboard(searchView)
        }

        val intent = IntentDataUtil.getShowConversationIntent(conversationModel, activity) ?: return

        if (isMultiPaneEnabled(activity)) {
            if (this.isAdded) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE)
                requireActivity().overridePendingTransition(0, 0)
            }
        } else {
            requireActivity().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ThreemaActivity.ACTIVITY_ID_SHARE_CHAT -> tempMessagesFile?.let { file ->
                /* We cannot delete the file immediately as some apps (e.g. Dropbox)
            	   take some time until they read the file after the intent has been completed.
                   As we can't know for sure when they're done, we simply wait for one minute before
                   we delete the temporary file. */
                object : Thread() {
                    val tmpFilePath: String = file.absolutePath
                    override fun run() {
                        try {
                            sleep(TEMP_MESSAGES_FILE_DELETE_WAIT_TIME.toLong())
                        } catch (e: InterruptedException) {
                            logger.error("Exception", e)
                        } finally {
                            FileUtil.deleteFileOrWarn(tmpFilePath, "tempMessagesFile", logger)
                        }
                    }
                }.start()

                tempMessagesFile = null
            }

            ThreemaActivity.ACTIVITY_ID_CHECK_LOCK -> if (resultCode == Activity.RESULT_OK) {
                screenLockService.setAuthenticated(true)
                preferenceService.setPrivateChatsHidden(false)
                updateList(
                    scrollToPosition = 0,
                    changedPositions = null,
                    runAfterSetData = Thread { firePrivateReceiverUpdate() },
                )
                context?.let(WidgetUtil::updateWidgets)
            }

            ID_RETURN_FROM_SECURITY_SETTINGS -> if (ConfigUtils.hasProtection(preferenceService)) {
                reallyHideChat(selectedConversation)
            }

            ID_PRIVATE_TO_PUBLIC -> {
                if (resultCode == Activity.RESULT_OK) {
                    screenLockService.setAuthenticated(true)
                    if (selectedConversation != null) {
                        removePrivateMark(selectedConversation!!)
                    }
                }
                super.onActivityResult(requestCode, resultCode, data)
            }

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun removePrivateMark(conversationModel: ConversationModel) {
        val receiver: MessageReceiver<*> = conversationModel.messageReceiver

        if (!conversationCategoryService.removePrivateMark(receiver)) {
            logger.warn("Private mark couldn't be removed from conversation")
            return
        }

        view?.let { view ->
            Snackbar.make(view, R.string.chat_visible, Snackbar.LENGTH_SHORT).show()
        }

        this.fireReceiverUpdate(receiver)
        messageListAdapter?.clearSelections()
    }

    private fun markAsPrivate(conversationModel: ConversationModel) {
        val receiver: MessageReceiver<*> = conversationModel.messageReceiver

        if (conversationCategoryService.isPrivateChat(receiver.getUniqueIdString())) {
            if (ConfigUtils.hasProtection(preferenceService)) {
                // persist selection
                selectedConversation = conversationModel
                HiddenChatUtil.launchLockCheckDialog(null, this, preferenceService, ID_PRIVATE_TO_PUBLIC)
            } else {
                removePrivateMark(conversationModel)
            }
        } else {
            if (ConfigUtils.hasProtection(preferenceService)) {
                logger.info("Showing dialog for confirming making a chat private")
                val dialog = GenericAlertDialog.newInstance(
                    R.string.hide_chat,
                    R.string.really_hide_chat_message,
                    R.string.ok,
                    R.string.cancel,
                )

                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_HIDE_THREAD)
            } else {
                logger.info("Showing dialog to explain private chats")
                val dialog = GenericAlertDialog.newInstance(
                    R.string.hide_chat,
                    R.string.hide_chat_message_explain,
                    R.string.set_lock,
                    R.string.cancel,
                )

                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel)
                dialog.show(parentFragmentManager, DIALOG_TAG_HIDE_THREAD_EXPLAIN)
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private fun reallyHideChat(conversationModel: ConversationModel?) {
        object : AsyncTask<Unit, Unit, Boolean>() {
            override fun onPreExecute() {
                resumePauseHandler.onPause()
            }

            override fun doInBackground(vararg params: Unit): Boolean {
                if (conversationModel == null) {
                    return false
                }
                val messageReceiver: MessageReceiver<*> = conversationModel.messageReceiver
                if (!conversationCategoryService.markAsPrivate(messageReceiver)) {
                    logger.warn("Conversation hasn't been marked as private")
                    return false
                }

                fireReceiverUpdate(conversationModel.messageReceiver)
                return true
            }

            override fun onPostExecute(success: Boolean) {
                if (success) {
                    if (messageListAdapter == null) {
                        return
                    }
                    messageListAdapter!!.clearSelections()
                    if (view != null) {
                        Snackbar.make(requireView(), R.string.chat_hidden, Snackbar.LENGTH_SHORT).show()
                    }
                    resumePauseHandler.onResume()
                    updateHiddenMenuVisibility()
                    if (ConfigUtils.hasProtection(preferenceService) && preferenceService.isPrivateChatsHidden()) {
                        updateList(
                            scrollToPosition = null,
                            changedPositions = null,
                            runAfterSetData = Thread { firePrivateReceiverUpdate() },
                        )
                    }
                } else {
                    Toast.makeText(getAppContext(), R.string.an_error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }.execute()
    }

    private fun shareChat(conversationModel: ConversationModel?, password: String?, includeMedia: Boolean) {
        val progressDialog: CancelableGenericProgressDialog = CancelableGenericProgressDialog.newInstance(
            R.string.preparing_messages,
            0,
            R.string.cancel,
        )
        progressDialog.setTargetFragment(this, 0)
        progressDialog.show(parentFragmentManager, DIALOG_TAG_PREPARING_MESSAGES)

        Thread {
            tempMessagesFile = FileUtil.getUniqueFile(fileService.getTempPath().path, "threema-chat.zip")
            FileUtil.deleteFileOrWarn(tempMessagesFile!!, "tempMessagesFile", logger)

            if (backupChatService.backupChatToZip(conversationModel, tempMessagesFile, password, includeMedia)) {
                if (tempMessagesFile != null && tempMessagesFile!!.exists() && tempMessagesFile!!.length() > 0) {
                    val intent: Intent = Intent(Intent.ACTION_SEND).apply {
                        setType(MimeUtil.MIME_TYPE_ZIP)
                        putExtra(
                            Intent.EXTRA_SUBJECT,
                            resources.getString(R.string.share_subject, getString(R.string.app_name)),
                        )
                        putExtra(
                            Intent.EXTRA_TEXT,
                            getString(R.string.chat_history_attached) + "\n\n" + getString(R.string.share_conversation_body),
                        )
                        putExtra(
                            Intent.EXTRA_STREAM,
                            fileService.getShareFileUri(tempMessagesFile, null),
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    RuntimeUtil.runOnUiThread {
                        if (isAdded) {
                            DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_PREPARING_MESSAGES, true)
                        }
                        startActivityForResult(
                            Intent.createChooser(intent, getString(R.string.share_via)),
                            ThreemaActivity.ACTIVITY_ID_SHARE_CHAT,
                        )
                    }
                }
            } else {
                RuntimeUtil.runOnUiThread {
                    if (isAdded) {
                        DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_PREPARING_MESSAGES, true)
                        SimpleStringAlertDialog.newInstance(R.string.share_via, getString(R.string.an_error_occurred))
                            .show(parentFragmentManager, "diskfull")
                    }
                }
            }
        }.start()
    }

    private fun prepareShareChat(model: ConversationModel) {
        val dialogFragment = PasswordEntryDialog.newInstance(
            R.string.share_chat,
            R.string.enter_zip_password_body,
            R.string.password_hint,
            R.string.ok,
            R.string.cancel,
            MIN_PW_LENGTH_BACKUP,
            MAX_PW_LENGTH_BACKUP,
            R.string.backup_password_again_summary,
            0,
            R.string.backup_data_media,
            PasswordEntryDialog.ForgotHintType.NONE,
        )
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.setData(model)
        dialogFragment.show(parentFragmentManager, DIALOG_TAG_SHARE_CHAT)
    }

    private fun refreshListEvent() {
        logger.debug("refreshListEvent reloadData")
        resumePauseHandler.runOnActive("refresh_list") {
            messageListAdapter?.notifyDataSetChanged()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var fragmentView = view

        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.fragment_conversations, container, false)

            val linearLayoutManager = LinearLayoutManager(activity)

            this.recyclerView = fragmentView.findViewById<EmptyRecyclerView>(R.id.list).apply {
                setHasFixedSize(true)
                setLayoutManager(linearLayoutManager)
                setItemAnimator(DefaultItemAnimator())
                applyDeviceInsetsAsPadding(
                    insetSides = InsetSides(
                        top = false,
                        right = true,
                        bottom = isMultiPaneEnabled(activity),
                        left = true,
                    ),
                    ownPadding = SpacingValues(
                        top = null,
                        right = null,
                        bottom = R.dimen.grid_unit_x10,
                        left = null,
                    ),
                )
            }

            this.cornerRadius = resources.getDimensionPixelSize(R.dimen.messagelist_card_corner_radius)

            val swipeCallback: ItemTouchHelper.SimpleCallback =
                object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT) {
                    private val pinIconDrawable = VectorDrawableCompat.create(resources, R.drawable.ic_pin, null)
                    private val unpinIconDrawable = VectorDrawableCompat.create(resources, R.drawable.ic_unpin, null)
                    private val archiveDrawable = VectorDrawableCompat.create(resources, R.drawable.ic_archive_outline, null)

                    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.7f

                    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                        // disable swiping and dragging for footer views
                        if (viewHolder.itemViewType == MessageListAdapter.TYPE_FOOTER) {
                            return makeMovementFlags(0, 0)
                        }
                        return super.getMovementFlags(recyclerView, viewHolder)
                    }

                    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean =
                        false

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        if (messageListAdapter == null) {
                            return
                        }

                        // swipe has ended successfully

                        // required to clear swipe layout
                        messageListAdapter!!.notifyDataSetChanged()

                        val holder = viewHolder as MessageListViewHolder
                        val messageListAdapterItem = holder.messageListAdapterItem
                        val conversationModel = messageListAdapterItem?.conversationModel
                        if (conversationModel == null) {
                            logger.error("Conversation model is null")
                            return
                        }
                        val oldPosition = conversationModel.position

                        if (direction == ItemTouchHelper.RIGHT) {
                            logger.info("Chat swiped right for pinning")
                            conversationTagService.toggle(
                                conversationModel,
                                ConversationTag.PINNED,
                                true,
                                TriggerSource.LOCAL,
                            )
                            conversationModel.isPinTagged = !conversationModel.isPinTagged

                            updateList(
                                scrollToPosition = null,
                                changedPositions = listOf(conversationModel),
                                runAfterSetData = {
                                    ListenerManager.conversationListeners.handle { listener: ConversationListener ->
                                        listener.onModified(conversationModel, oldPosition)
                                    }
                                },
                            )
                        } else if (direction == ItemTouchHelper.LEFT) {
                            logger.info("Chat swiped right for archiving")
                            archiveChat(conversationModel)
                        }
                    }

                    override fun onChildDraw(
                        canvas: Canvas,
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        dX: Float,
                        dY: Float,
                        actionState: Int,
                        isCurrentlyActive: Boolean,
                    ) {
                        val itemView = viewHolder.itemView

                        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                            val paint = Paint()

                            if (dX > 0) {
                                val holder = viewHolder as MessageListViewHolder

                                val messageListAdapterItem = holder.messageListAdapterItem
                                val conversationModel = messageListAdapterItem?.conversationModel

                                val icon: VectorDrawableCompat? =
                                    if (conversationTagService.isTaggedWith(conversationModel, ConversationTag.PINNED)) {
                                        unpinIconDrawable
                                    } else {
                                        pinIconDrawable
                                    }
                                icon!!.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight())
                                icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

                                val label: String =
                                    if (conversationTagService.isTaggedWith(conversationModel, ConversationTag.PINNED)) {
                                        getString(R.string.unpin)
                                    } else {
                                        getString(
                                            R.string.pin,
                                        )
                                    }

                                paint.setColor(ContextCompat.getColor(requireContext(), R.color.message_list_pin_color))
                                canvas.drawRect(
                                    itemView.left.toFloat(),
                                    itemView.top.toFloat(),
                                    dX + cornerRadius,
                                    itemView.bottom.toFloat(),
                                    paint,
                                )
                                canvas.save()
                                canvas.translate(
                                    itemView.left.toFloat() + resources.getDimension(R.dimen.swipe_icon_inset),
                                    itemView.top.toFloat() + (
                                        itemView.bottom.toFloat() - itemView.top
                                            .toFloat() - icon.getIntrinsicHeight()
                                        ) / 2,
                                )
                                icon.draw(canvas)
                                canvas.restore()

                                val textPaint = Paint()
                                textPaint.setColor(Color.WHITE)
                                textPaint.textSize = resources.getDimension(R.dimen.swipe_text_size)

                                val rect = Rect()
                                textPaint.getTextBounds(label, 0, label.length, rect)

                                canvas.drawText(
                                    label,
                                    itemView.left + resources.getDimension(R.dimen.swipe_text_inset),
                                    (itemView.top + (itemView.bottom - itemView.top + rect.height()) / 2).toFloat(),
                                    textPaint,
                                )
                            } else if (dX < 0) {
                                val icon = archiveDrawable
                                icon!!.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight())
                                icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

                                val label = getString(R.string.to_archive)

                                paint.setColor(ContextCompat.getColor(requireContext(), R.color.message_list_archive_color))
                                canvas.drawRect(
                                    dX,
                                    itemView.top.toFloat(),
                                    itemView.right.toFloat(),
                                    itemView.bottom.toFloat(),
                                    paint,
                                )
                                canvas.save()
                                canvas.translate(
                                    itemView.right
                                        .toFloat() - resources.getDimension(R.dimen.swipe_icon_inset) - icon.getIntrinsicWidth(),
                                    itemView.top.toFloat() + (
                                        itemView.bottom.toFloat() - itemView.top
                                            .toFloat() - icon.getIntrinsicHeight()
                                        ) / 2,
                                )
                                icon.draw(canvas)
                                canvas.restore()

                                val textPaint = Paint()
                                textPaint.setColor(Color.WHITE)
                                textPaint.textSize = resources.getDimension(R.dimen.swipe_text_size)

                                val rect = Rect()
                                textPaint.getTextBounds(label, 0, label.length, rect)
                                var textStartX = itemView.right - resources.getDimension(R.dimen.swipe_text_inset) - rect.width()
                                if (textStartX < 0) {
                                    textStartX = 0f
                                }

                                canvas.drawText(
                                    label,
                                    textStartX,
                                    (itemView.top + (itemView.bottom - itemView.top + rect.height()) / 2).toFloat(),
                                    textPaint,
                                )
                            }
                        }
                        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    }

                    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
                        return defaultValue * 20
                    }

                    override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
                        return defaultValue * 5
                    }
                }
            val itemTouchHelper = ItemTouchHelper(swipeCallback)
            itemTouchHelper.attachToRecyclerView(recyclerView)

            // disable change animation to avoid avatar flicker FX
            (this.recyclerView!!.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

            this.floatingButtonView = fragmentView.findViewById<ExtendedFloatingActionButton>(R.id.floating).apply {
                setOnClickListener { onFABClicked() }
                applyDeviceInsetsAsMargin(
                    insetSides = InsetSides(
                        top = false,
                        right = true,
                        bottom = isMultiPaneEnabled(activity),
                        left = true,
                    ),
                    ownMargin = all(value = R.dimen.floating_button_margin),
                )
            }

            // add text view if contact list is empty
            val emptyView: EmptyView = EmptyView(activity).apply {
                setup(R.string.no_recent_conversations)
            }
            (recyclerView!!.parent as ViewGroup).addView(emptyView)
            recyclerView!!.setNumHeadersAndFooters(-1)
            recyclerView!!.setEmptyView(emptyView)
            recyclerView!!.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)

                        if (linearLayoutManager.findFirstVisibleItemPosition() == 0) {
                            floatingButtonView!!.extend()
                        } else {
                            floatingButtonView!!.shrink()
                        }
                    }
                },
            )

            // instantiate fragment
            myIdentity = userService.getIdentity()
        }
        return fragmentView
    }

    private fun archiveChat(conversationModel: ConversationModel) {
        conversationService.archive(conversationModel, TriggerSource.LOCAL)
        archiveSnackbar = ArchiveSnackbar(archiveSnackbar, conversationModel)
    }

    private fun onFABClicked() {
        logger.info("FAB clicked, opening new chat screen")
        // stop list fling to avoid crashes due to concurrent access to conversation data
        recyclerView!!.stopScroll()
        val intent: Intent = Intent(context, RecipientListBaseActivity::class.java).apply {
            putExtra(AppConstants.INTENT_DATA_HIDE_RECENTS, true)
            putExtra(RecipientListBaseActivity.INTENT_DATA_MULTISELECT, false)
            putExtra(RecipientListBaseActivity.INTENT_DATA_MULTISELECT_FOR_COMPOSE, true)
        }
        requireActivity().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE)
    }

    override fun onDestroy() {
        this.removeListeners()
        resumePauseHandler.onDestroy(this)
        super.onDestroy()
    }

    override fun onItemClick(view: View?, position: Int, model: ConversationModel) {
        logger.info("Conversation clicked")
        Thread { showConversation(model) }.start()
    }

    override fun onAvatarClick(view: View?, position: Int, model: ConversationModel) {
        var intent: Intent? = null
        if (model.isContactConversation) {
            logger.info("Contact avatar clicked")
            intent = Intent(activity, ContactDetailActivity::class.java)
            intent.putExtra(AppConstants.INTENT_DATA_CONTACT, model.contact!!.identity)
        } else if (model.isGroupConversation) {
            logger.info("Group avatar clicked")
            openGroupDetails(model)
        } else if (model.isDistributionListConversation) {
            logger.info("Distribution list avatar clicked")
            intent = createIntent(requireContext(), model.distributionList!!.id)
        }
        if (intent != null) {
            requireActivity().startActivity(intent)
        }
    }

    override fun onFooterClick(view: View?) {
        logger.info("Footer clicked, showing archive")
        val intent = Intent(activity, ArchiveActivity::class.java)
        intent.putExtra(AppConstants.INTENT_DATA_ARCHIVE_FILTER, filterQuery)
        requireActivity().startActivity(intent)
    }

    override fun onJoinGroupCallClick(conversationModel: ConversationModel) {
        logger.info("Join group call clicked")
        conversationModel.group?.let { group ->
            startActivity(GroupCallActivity.createJoinCallIntent(requireActivity(), group.id))
        }
    }

    private fun openGroupDetails(model: ConversationModel) {
        model.group?.let { group ->
            requireActivity().startActivity(
                groupService.getGroupDetailIntent(group, requireActivity()),
            )
        }
    }

    override fun onItemLongClick(view: View?, position: Int, conversationModel: ConversationModel?): Boolean {
        if (!isMultiPaneEnabled(activity) && messageListAdapter != null) {
            messageListAdapter!!.toggleItemChecked(conversationModel, position)
            showSelector()
            return true
        }
        return false
    }

    override fun onProgressbarCanceled(tag: String?) {
        backupChatService.cancel()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        logger.debug("*** onHiddenChanged: " + hidden)
        if (hidden) {
            if (searchView?.isShown() == true) {
                searchMenuItemRef?.get()?.collapseActionView()
            }
            resumePauseHandler.onPause()
        } else {
            resumePauseHandler.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        logger.info("*** onPause")
        resumePauseHandler.onPause()
    }

    override fun onResume() {
        logger.info("*** onResume")

        resumePauseHandler.onResume()

        if (PreferenceService.LockingMech_SYSTEM == preferenceService.getLockMechanism()) {
            val keyguardManager = requireActivity().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguardManager.isDeviceSecure) {
                Toast.makeText(activity, R.string.no_lockscreen_set, Toast.LENGTH_LONG).show()
                preferenceService.setLockMechanism(PreferenceService.LockingMech_NONE)
                preferenceService.setAppLockEnabled(false)
                preferenceService.setPrivateChatsHidden(false)
                updateList(
                    scrollToPosition = 0,
                    changedPositions = null,
                    runAfterSetData = null,
                )
            }
        }
        updateHiddenMenuVisibility()

        messageListAdapter?.updateDateView()

        super.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        logger.info("saveInstance")

        if (!filterQuery.isNullOrEmpty()) {
            outState.putString(BUNDLE_FILTER_QUERY, filterQuery)
        }

        super.onSaveInstanceState(outState)
    }

    override fun onYes(tag: String?, text: String?, isChecked: Boolean, data: Any?) {
        logger.info("Chat sharing confirmed")
        shareChat(
            conversationModel = data as ConversationModel?,
            password = text,
            includeMedia = isChecked,
        )
    }

    private fun showSelector() {
        val labels: MutableList<SelectorDialogItem> = mutableListOf()
        val tags: MutableList<Int> = mutableListOf()

        if (messageListAdapter == null || messageListAdapter!!.checkedItemCount != 1) {
            return
        }
        val conversationModel: ConversationModel = messageListAdapter!!.checkedItems[0] ?: return
        val receiver: MessageReceiver<*> = try {
            conversationModel.messageReceiver
        } catch (e: Exception) {
            logger.error("Could not get receiver of conversation model", e)
            return
        }

        val isPrivate: Boolean = conversationCategoryService.isPrivateChat(receiver.getUniqueIdString())
        val isMarkedAsUnread: Boolean = conversationTagService.isTaggedWith(conversationModel, ConversationTag.MARKED_AS_UNREAD)

        if (conversationModel.hasUnreadMessage() || isMarkedAsUnread) {
            labels.add(SelectorDialogItem(getString(R.string.mark_read), R.drawable.ic_outline_visibility))
            tags.add(TAG_MARK_READ)
        } else {
            labels.add(SelectorDialogItem(getString(R.string.mark_unread), R.drawable.ic_outline_visibility_off))
            tags.add(TAG_MARK_UNREAD)
        }

        if (isPrivate) {
            labels.add(SelectorDialogItem(getString(R.string.unset_private), R.drawable.ic_outline_shield_24))
            tags.add(TAG_UNSET_PRIVATE)
        } else {
            labels.add(SelectorDialogItem(getString(R.string.set_private), R.drawable.ic_privacy_outline))
            tags.add(TAG_SET_PRIVATE)
        }

        if (!isPrivate && !AppRestrictionUtil.isExportDisabled(activity)) {
            labels.add(SelectorDialogItem(getString(R.string.share_chat), R.drawable.ic_share_outline))
            tags.add(TAG_SHARE)
        }

        labels.add(SelectorDialogItem(getString(R.string.archive_chat), R.drawable.ic_archive_outline))
        tags.add(TAG_ARCHIVE_CHAT)

        if (conversationModel.messageCount > 0) {
            labels.add(SelectorDialogItem(getString(R.string.empty_chat_title), R.drawable.ic_outline_delete_sweep))
            tags.add(TAG_EMPTY_CHAT)
        }
        if (conversationModel.isContactConversation) {
            labels.add(SelectorDialogItem(getString(R.string.delete_chat_title), R.drawable.ic_delete_outline))
            tags.add(TAG_DELETE_CHAT)
        }

        if (conversationModel.isDistributionListConversation) {
            // distribution lists
            labels.add(SelectorDialogItem(getString(R.string.really_delete_distribution_list), R.drawable.ic_delete_outline))
            tags.add(TAG_DELETE_DISTRIBUTION_LIST)
        } else if (conversationModel.isGroupConversation) {
            // group chats
            val groupModelData: GroupModelData = conversationModel.groupModel?.data ?: run {
                logger.error("Cannot access the group (data) from the conversation model")
                return
            }
            val isCreator: Boolean = groupModelData.groupIdentity.creatorIdentity == myIdentity
            val isMember: Boolean = groupModelData.isMember
            val hasOtherMembers: Boolean = !groupModelData.otherMembers.isEmpty()
            // Check also if the user is a group member, because left groups should not be editable.
            if (isCreator && isMember) {
                labels.add(SelectorDialogItem(getString(R.string.group_edit_title), R.drawable.ic_pencil_outline))
                tags.add(TAG_EDIT_GROUP)
            }
            // Members (except the creator) can leave the group
            if (!isCreator && isMember) {
                labels.add(SelectorDialogItem(getString(R.string.action_leave_group), R.drawable.ic_outline_directions_run))
                tags.add(TAG_LEAVE_GROUP)
            }
            if (isCreator && isMember && hasOtherMembers) {
                labels.add(SelectorDialogItem(getString(R.string.action_dissolve_group), R.drawable.ic_outline_directions_run))
                tags.add(TAG_DISSOLVE_GROUP)
            }
            labels.add(SelectorDialogItem(getString(R.string.action_delete_group), R.drawable.ic_delete_outline))
            if (isMember) {
                if (isCreator) {
                    tags.add(TAG_DELETE_MY_GROUP)
                } else {
                    tags.add(TAG_DELETE_GROUP)
                }
            } else {
                tags.add(TAG_DELETE_LEFT_GROUP)
            }
        }

        val selectorDialog: SelectorDialog = SelectorDialog.newInstance(
            receiver.getDisplayName(),
            ArrayList(labels),
            ArrayList(tags),
            getString(R.string.cancel),
        )
        selectorDialog.setData(conversationModel)
        selectorDialog.setTargetFragment(this, 0)
        selectorDialog.show(parentFragmentManager, DIALOG_TAG_SELECT_DELETE_ACTION)
    }

    @SuppressLint("StringFormatInvalid")
    override fun onClick(tag: String?, which: Int, data: Any?) {
        val dialog: GenericAlertDialog?

        messageListAdapter?.clearSelections()
        val conversationModel: ConversationModel = data as ConversationModel

        when (which) {
            TAG_ARCHIVE_CHAT -> {
                logger.info("Archive chat clicked")
                archiveChat(conversationModel)
            }

            TAG_EMPTY_CHAT -> {
                logger.info("Empty chat clicked, showing dialog")
                dialog = GenericAlertDialog.newInstance(
                    R.string.empty_chat_title,
                    R.string.empty_chat_confirm,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setData(conversationModel)
                dialog.setTargetFragment(this, 0)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_EMPTY_CHAT)
            }

            TAG_DELETE_CHAT -> {
                logger.info("Delete chat clicked, showing dialog")
                dialog = GenericAlertDialog.newInstance(
                    R.string.delete_chat_title,
                    R.string.delete_chat_confirm,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setData(conversationModel)
                dialog.setTargetFragment(this, 0)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_DELETE_CHAT)
            }

            TAG_DELETE_DISTRIBUTION_LIST -> {
                logger.info("Delete distribution list clicked, showing dialog")
                dialog = GenericAlertDialog.newInstance(
                    R.string.really_delete_distribution_list,
                    R.string.really_delete_distribution_list_message,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST)
            }

            TAG_EDIT_GROUP -> {
                logger.info("Edit group clicked, opening details screen")
                openGroupDetails(conversationModel)
            }

            TAG_LEAVE_GROUP -> {
                logger.info("Leave group clicked, showing dialog")
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_leave_group,
                    R.string.really_leave_group_message,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel.group!!)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_LEAVE_GROUP)
            }

            TAG_DISSOLVE_GROUP -> {
                logger.info("Dissolve group clicked, showing dialog")
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_dissolve_group,
                    R.string.really_dissolve_group,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel.group!!)
                dialog.show(getParentFragmentManager(), DIALOG_TAG_REALLY_DISSOLVE_GROUP)
            }

            TAG_DELETE_MY_GROUP -> {
                logger.info("Delete my group clicked")
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_dissolve_and_delete_group,
                    R.string.delete_my_group_message,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel.group!!)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_DELETE_MY_GROUP)
            }

            TAG_DELETE_GROUP -> {
                logger.info("Delete group clicked")
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_delete_group,
                    R.string.delete_group_message,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel.group!!)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_DELETE_GROUP)
            }

            TAG_DELETE_LEFT_GROUP -> {
                logger.info("Leave group clicked")
                dialog = GenericAlertDialog.newInstance(
                    R.string.action_delete_group,
                    R.string.delete_left_group_message,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel.group!!)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_DELETE_GROUP)
            }

            TAG_SET_PRIVATE, TAG_UNSET_PRIVATE -> {
                logger.info("(un)private clicked")
                markAsPrivate(conversationModel)
            }

            TAG_SHARE -> {
                logger.info("Share clicked")
                if (
                    ConfigUtils.requestWriteStoragePermissions(
                        requireActivity(),
                        this,
                        PERMISSION_REQUEST_SHARE_THREAD,
                    )
                ) {
                    prepareShareChat(conversationModel)
                }
            }

            TAG_MARK_READ -> {
                logger.info("Mark read clicked")
                conversationTagService.removeTagAndNotify(
                    conversationModel,
                    ConversationTag.MARKED_AS_UNREAD,
                    TriggerSource.LOCAL,
                )
                conversationModel.isUnreadTagged = false
                conversationModel.unreadCount = 0
                Thread {
                    messageService.markConversationAsRead(conversationModel.messageReceiver, notificationService)
                }.start()
            }

            TAG_MARK_UNREAD -> {
                logger.info("Mark unread clicked")
                conversationTagService.addTagAndNotify(
                    conversationModel,
                    ConversationTag.MARKED_AS_UNREAD,
                    TriggerSource.LOCAL,
                )
                conversationModel.isUnreadTagged = true
            }
        }
    }

    override fun onCancel(tag: String?) {
        messageListAdapter?.clearSelections()
    }

    override fun onNo(tag: String?) {
        if (messageListAdapter != null && DIALOG_TAG_SELECT_DELETE_ACTION == tag) {
            messageListAdapter!!.clearSelections()
        }
    }

    override fun onYes(tag: String, data: Any?) {
        when (tag) {
            DIALOG_TAG_REALLY_HIDE_THREAD -> {
                logger.info("Make chat private confirmed")
                reallyHideChat(data as ConversationModel?)
            }

            DIALOG_TAG_HIDE_THREAD_EXPLAIN -> {
                selectedConversation = data as ConversationModel?
                val intent = Intent(activity, SettingsActivity::class.java)
                intent.putExtra(SettingsActivity.EXTRA_SHOW_SECURITY_FRAGMENT, true)
                startActivityForResult(intent, ID_RETURN_FROM_SECURITY_SETTINGS)
            }

            DIALOG_TAG_REALLY_LEAVE_GROUP -> {
                logger.info("Leave group confirmed")
                leaveGroup(GroupLeaveIntent.LEAVE, getNewGroupModel(data as GroupModel?))
            }

            DIALOG_TAG_REALLY_DISSOLVE_GROUP -> {
                logger.info("Dissolve group confirmed")
                disbandGroup(GroupDisbandIntent.DISBAND, getNewGroupModel(data as GroupModel?))
            }

            DIALOG_TAG_REALLY_DELETE_MY_GROUP, DIALOG_TAG_REALLY_DELETE_GROUP -> {
                logger.info("Delete group confirmed")
                removeGroup(getNewGroupModel(data as GroupModel?))
            }

            DIALOG_TAG_REALLY_EMPTY_CHAT,
            DIALOG_TAG_REALLY_DELETE_CHAT,
            DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST,
            -> {
                val ownIdentity = myIdentity ?: run {
                    logger.error("Cannot empty or remove chat when identity is null")
                    return
                }
                val mode: EmptyOrDeleteConversationsAsyncTask.Mode =
                    when (tag) {
                        DIALOG_TAG_REALLY_DELETE_CHAT,
                        DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST,
                        -> EmptyOrDeleteConversationsAsyncTask.Mode.DELETE
                        else -> EmptyOrDeleteConversationsAsyncTask.Mode.EMPTY
                    }
                val receiver: MessageReceiver<*> = (data as ConversationModel).messageReceiver
                logger.info("{} chat with receiver {} (type={}).", mode, receiver.getUniqueIdString(), receiver.getType())
                EmptyOrDeleteConversationsAsyncTask(
                    mode,
                    arrayOf(receiver),
                    conversationService,
                    distributionListService,
                    groupModelRepository,
                    groupFlowDispatcher,
                    ownIdentity,
                    parentFragmentManager,
                    null,
                    null,
                ).execute()
            }

            else -> Unit
        }
    }

    private fun leaveGroup(
        intent: GroupLeaveIntent,
        groupModel: ch.threema.data.models.GroupModel?,
    ) {
        if (groupModel == null) {
            logger.error("Cannot leave group: group model is null")
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_leaving_group_internal)
                .show(getParentFragmentManager())
            return
        }

        val loadingDialog: LoadingWithTimeoutDialogXml = LoadingWithTimeoutDialogXml.newInstance(
            timeoutSeconds = GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            titleText = R.string.leaving_group,
        )
        loadingDialog.show(getParentFragmentManager())

        val leaveGroupFlowResultDeferred: Deferred<GroupFlowResult?> = groupFlowDispatcher
            .runLeaveGroupFlow(intent, groupModel)

        leaveGroupFlowResultDeferred
            .onCompleted(
                onCompletedExceptionally = { exception: Throwable? ->
                    logger.error("leave-group-flow was completed exceptionally", exception)
                    onLeaveGroupFailed(GroupFlowResult.Failure.Other, loadingDialog)
                },
                onCompletedNormally = { result: GroupFlowResult? ->
                    if (result is GroupFlowResult.Success) {
                        RuntimeUtil.runOnUiThread { loadingDialog.dismiss() }
                    } else if (result is GroupFlowResult.Failure) {
                        onLeaveGroupFailed(result, loadingDialog)
                    }
                    Unit
                },
            )
    }

    @AnyThread
    private fun onLeaveGroupFailed(
        failureResult: GroupFlowResult.Failure,
        loadingDialog: LoadingWithTimeoutDialogXml,
    ) {
        RuntimeUtil.runOnUiThread {
            loadingDialog.dismiss()
            @StringRes val errorMessageRes: Int =
                if (failureResult is GroupFlowResult.Failure.Network) {
                    R.string.error_leaving_group_network
                } else {
                    R.string.error_leaving_group_internal
                }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getParentFragmentManager())
        }
    }

    private fun disbandGroup(
        intent: GroupDisbandIntent,
        groupModel: ch.threema.data.models.GroupModel?,
    ) {
        if (groupModel == null) {
            logger.error("Cannot disband group: group model is null")
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_disbanding_group_internal)
                .show(getParentFragmentManager())
            return
        }

        val disbandGroupFlowResultDeferred: Deferred<GroupFlowResult?> = groupFlowDispatcher
            .runDisbandGroupFlow(intent, groupModel)

        val loadingDialog: LoadingWithTimeoutDialogXml = LoadingWithTimeoutDialogXml.newInstance(
            timeoutSeconds = GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            titleText = R.string.disbanding_group,
        )
        loadingDialog.show(getParentFragmentManager())

        disbandGroupFlowResultDeferred
            .onCompleted(
                onCompletedExceptionally = { exception: Throwable? ->
                    logger.error("disband-group-flow was completed exceptionally", exception)
                    onDisbandGroupFailed(GroupFlowResult.Failure.Other, loadingDialog)
                },
                onCompletedNormally = { result: GroupFlowResult? ->
                    if (result is GroupFlowResult.Success) {
                        RuntimeUtil.runOnUiThread {
                            loadingDialog.dismiss()
                        }
                    } else if (result is GroupFlowResult.Failure) {
                        onDisbandGroupFailed(result, loadingDialog)
                    }
                },
            )
    }

    @AnyThread
    private fun onDisbandGroupFailed(
        failureResult: GroupFlowResult.Failure,
        loadingDialog: LoadingWithTimeoutDialogXml,
    ) {
        RuntimeUtil.runOnUiThread {
            loadingDialog.dismiss()
            @StringRes val errorMessageRes: Int =
                if (failureResult is GroupFlowResult.Failure.Network) {
                    R.string.error_disbanding_group_network
                } else {
                    R.string.error_disbanding_group_internal
                }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getParentFragmentManager())
        }
    }

    private fun removeGroup(groupModel: ch.threema.data.models.GroupModel?) {
        if (groupModel == null) {
            // Group already removed
            return
        }

        val groupModelData: GroupModelData = groupModel.data ?: return
        if (groupModelData.isMember) {
            // Disband or leave if the user is still part of the group.
            if (groupModelData.groupIdentity.creatorIdentity == myIdentity) {
                disbandGroup(GroupDisbandIntent.DISBAND_AND_REMOVE, groupModel)
            } else {
                leaveGroup(GroupLeaveIntent.LEAVE_AND_REMOVE, groupModel)
            }
        } else {
            // Just remove the group
            runGroupRemoveFlow(groupModel)
        }
    }

    /**
     * Note that this must only be run for groups that are already left or disbanded.
     */
    private fun runGroupRemoveFlow(
        groupModel: ch.threema.data.models.GroupModel,
    ) {
        val loadingDialog: LoadingWithTimeoutDialogXml = LoadingWithTimeoutDialogXml.newInstance(
            timeoutSeconds = GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS,
            titleText = R.string.removing_group,
        )
        loadingDialog.show(getParentFragmentManager())

        val removeGroupFlowResultDeferred: Deferred<GroupFlowResult?> = groupFlowDispatcher
            .runRemoveGroupFlow(groupModel)

        removeGroupFlowResultDeferred
            .onCompleted(
                onCompletedExceptionally = { exception: Throwable? ->
                    logger.error("remove-group-flow was completed exceptionally", exception)
                    onRemoveGroupFailed(GroupFlowResult.Failure.Other, loadingDialog)
                },
                onCompletedNormally = { result: GroupFlowResult? ->
                    if (result is GroupFlowResult.Success) {
                        RuntimeUtil.runOnUiThread { loadingDialog.dismiss() }
                    } else if (result is GroupFlowResult.Failure) {
                        onRemoveGroupFailed(result, loadingDialog)
                    }
                },
            )
    }

    @AnyThread
    private fun onRemoveGroupFailed(
        failureResult: GroupFlowResult.Failure,
        loadingDialog: LoadingWithTimeoutDialogXml,
    ) {
        RuntimeUtil.runOnUiThread {
            loadingDialog.dismiss()
            @StringRes val errorMessageRes: Int =
                if (failureResult is GroupFlowResult.Failure.Network) {
                    R.string.error_removing_group_network
                } else {
                    R.string.error_removing_group_internal
                }
            SimpleStringAlertDialog
                .newInstance(R.string.error, errorMessageRes)
                .show(getParentFragmentManager())
        }
    }

    private fun getNewGroupModel(groupModel: GroupModel?): ch.threema.data.models.GroupModel? {
        if (groupModel == null) {
            logger.error("Provided group model is null")
            return null
        }
        val newGroupModel: ch.threema.data.models.GroupModel? = groupModelRepository.getByCreatorIdentityAndId(
            creatorIdentity = groupModel.creatorIdentity,
            groupId = groupModel.apiGroupId,
        )
        if (newGroupModel == null) {
            logger.error("New group model is null")
        }
        return newGroupModel
    }

    @Deprecated("")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_SHARE_THREAD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                prepareShareChat(selectedConversation!!)
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ConfigUtils.showPermissionRationale(context, view, R.string.permission_storage_required)
            }
        }
    }

    private fun setupListeners() {
        logger.debug("*** setup listeners")
        ListenerManager.conversationListeners.add(this.conversationListener)
        ListenerManager.contactListeners.add(this.contactListener)
        ListenerManager.contactSettingsListeners.add(this.contactSettingsListener)
        ListenerManager.synchronizeContactsListeners.add(this.synchronizeContactsListener)
        ListenerManager.chatListener.add(this.chatListener)
        ListenerManager.groupListeners.add(this.groupListener)
    }

    private fun removeListeners() {
        logger.debug("*** remove listeners")
        ListenerManager.conversationListeners.remove(this.conversationListener)
        ListenerManager.contactListeners.remove(this.contactListener)
        ListenerManager.contactSettingsListeners.remove(this.contactSettingsListener)
        ListenerManager.synchronizeContactsListeners.remove(this.synchronizeContactsListener)
        ListenerManager.chatListener.remove(this.chatListener)
        ListenerManager.groupListeners.remove(this.groupListener)
    }

    private fun updateList(
        scrollToPosition: Int? = null,
        changedPositions: List<ConversationModel>? = null,
        runAfterSetData: Runnable? = null,
    ) {
        updateList(
            scrollToPosition = scrollToPosition,
            changedPositions = changedPositions,
            runAfterSetData = runAfterSetData,
            recreate = false,
        )
    }

    private fun updateList(
        scrollToPosition: Int?,
        changedPositions: List<ConversationModel>?,
        runAfterSetData: Runnable?,
        recreate: Boolean,
    ) {
        logger.debug("*** update list [" + scrollToPosition + ", " + (changedPositions?.size ?: "0") + "]")

        val updateListThread = Thread {
            val conversationModels: List<ConversationModel> = conversationService.getAll(
                false,
                object : ConversationService.Filter {
                    override fun noHiddenChats(): Boolean = preferenceService.isPrivateChatsHidden()

                    override fun filterQuery(): String? = filterQuery
                },
            )

            RuntimeUtil.runOnUiThread {
                val activity = activity ?: return@runOnUiThread
                synchronized(messageListAdapterLock) {
                    if (messageListAdapter == null || recreate) {
                        messageListAdapter = MessageListAdapter(
                            activity,
                            contactService,
                            groupService,
                            distributionListService,
                            conversationService,
                            ringtoneService,
                            conversationCategoryService,
                            preferenceService,
                            groupCallManager,
                            highlightUid,
                            this@ConversationsFragment,
                            messageListAdapterItemCache,
                            Glide.with(getAppContext()),
                        )

                        recyclerView!!.setAdapter(messageListAdapter)
                    }
                    try {
                        messageListAdapter!!.setData(conversationModels, changedPositions)
                    } catch (e: IndexOutOfBoundsException) {
                        logger.debug("Failed to set adapter data", e)
                    }
                    // make sure footer is refreshed
                    messageListAdapter!!.refreshFooter()
                    if (recyclerView != null && scrollToPosition != null) {
                        if (changedPositions != null && changedPositions.size == 1) {
                            val changedModel: ConversationModel? = changedPositions.firstOrNull()
                            if (
                                changedModel != null &&
                                scrollToPosition > changedModel.position &&
                                conversationModels.contains(changedModel)
                            ) {
                                recyclerView!!.scrollToPosition(changedModel.position)
                            }
                        }
                    }
                }
                runAfterSetData?.run()
            }
            synchronized(messageListAdapterItemCache) {
                for (conversationModel: ConversationModel in conversationModels) {
                    if (!messageListAdapterItemCache.containsKey(conversationModel)) {
                        messageListAdapterItemCache.put(
                            conversationModel,
                            MessageListAdapterItem(
                                conversationModel,
                                contactService,
                                ringtoneService,
                                conversationCategoryService,
                            ),
                        )
                    }
                }
            }
        }

        if (messageListAdapter == null) {
            // hack: run synchronously when setting up the adapter for the first time to avoid showing an empty list
            @Suppress("CallToThreadRun")
            updateListThread.run()
        } else {
            updateListThread.start()
        }
    }

    private fun updateHiddenMenuVisibility() {
        val toggleHiddenMenuItem: MenuItem? = toggleHiddenMenuItemRef?.get()
        if (isAdded && toggleHiddenMenuItem != null) {
            toggleHiddenMenuItem.isVisible = conversationCategoryService.hasPrivateChats() &&
                ConfigUtils.hasProtection(preferenceService)
        }
    }

    private fun isMultiPaneEnabled(activity: Activity?): Boolean {
        if (activity != null) {
            return ConfigUtils.isTabletLayout() && activity is ComposeMessageActivity
        }
        return false
    }

    private fun fireReceiverUpdate(receiver: MessageReceiver<*>) {
        when (receiver) {
            is GroupMessageReceiver -> {
                val groupIdentity = GroupIdentity(
                    creatorIdentity = receiver.group.creatorIdentity,
                    groupId = receiver.group.apiGroupId.toLong(),
                )
                ListenerManager.groupListeners.handle { listener: GroupListener -> listener.onUpdate(groupIdentity) }
            }

            is ContactMessageReceiver -> {
                ListenerManager.contactListeners.handle { listener: ContactListener ->
                    listener.onModified(receiver.contact.identity)
                }
            }

            is DistributionListMessageReceiver -> {
                ListenerManager.distributionListListeners.handle { listener: DistributionListListener ->
                    listener.onModify(receiver.distributionList)
                }
            }
        }
    }

    @WorkerThread
    private fun firePrivateReceiverUpdate() {
        // fire a update for every secret receiver (to update webclient data)
        conversationService.getAll(false)
            .filter { conversationModel ->
                conversationCategoryService.isPrivateChat(conversationModel.messageReceiver.getUniqueIdString())
            }
            .forEach { conversationModel ->
                fireReceiverUpdate(conversationModel.messageReceiver)
            }
    }

    fun onLogoClicked() {
        if (this.recyclerView != null) {
            logger.info("Logo clicked, scrolling to top")
            this.recyclerView!!.stopScroll()
            this.recyclerView!!.scrollToPosition(0)
        }
    }

    /**
     * Keeps track of the last archive chats. This class is used for the undo action.
     */
    private inner class ArchiveSnackbar(archiveSnackbar: ArchiveSnackbar?, archivedConversation: ConversationModel) {
        private val snackbar: Snackbar?
        private val conversationModels = ArrayList<ConversationModel>()

        /**
         * Creates an updated archive snackbar, dismisses the old snackbar (if available), and shows
         * the updated snackbar.
         *
         * @param archiveSnackbar      the currently shown archive snackbar (if available)
         * @param archivedConversation the conversation that just has been archived
         */
        init {
            this.conversationModels.add(archivedConversation)

            if (archiveSnackbar != null) {
                this.conversationModels.addAll(archiveSnackbar.conversationModels)
                archiveSnackbar.dismiss()
            }

            if (view != null) {
                val amountArchived: Int = this.conversationModels.size
                val snackText: String = ConfigUtils.getSafeQuantityString(
                    requireContext(),
                    R.plurals.message_archived,
                    amountArchived,
                    amountArchived,
                    this.conversationModels.size,
                )
                this.snackbar = Snackbar.make(
                    requireView(),
                    snackText,
                    DURATION_ARCHIVE_UNDO_SNACKBAR.inWholeMilliseconds.toInt(),
                )
                this.snackbar.setAction(R.string.undo) { _ ->
                    conversationService.unarchive(conversationModels, TriggerSource.LOCAL)
                }
                this.snackbar.addCallback(
                    object : Snackbar.Callback() {
                        override fun onDismissed(snackbar: Snackbar?, event: Int) {
                            super.onDismissed(snackbar, event)
                            if (this@ConversationsFragment.archiveSnackbar === this@ArchiveSnackbar) {
                                this@ConversationsFragment.archiveSnackbar = null
                            }
                        }
                    },
                )
                this.snackbar.show()
            } else {
                this.snackbar = null
            }
        }

        fun dismiss() {
            this.snackbar?.dismiss()
        }
    }

    private companion object {

        const val PERMISSION_REQUEST_SHARE_THREAD = 1
        const val ID_RETURN_FROM_SECURITY_SETTINGS = 33211
        const val TEMP_MESSAGES_FILE_DELETE_WAIT_TIME = 2 * 60 * 1000

        const val DIALOG_TAG_PREPARING_MESSAGES = "progressMsgs"
        const val DIALOG_TAG_SHARE_CHAT = "shareChat"
        const val DIALOG_TAG_REALLY_HIDE_THREAD = "lockC"
        const val DIALOG_TAG_HIDE_THREAD_EXPLAIN = "hideEx"
        const val DIALOG_TAG_SELECT_DELETE_ACTION = "sel"
        const val DIALOG_TAG_REALLY_LEAVE_GROUP = "rlg"
        const val DIALOG_TAG_REALLY_DISSOLVE_GROUP = "reallyDissolveGroup"
        const val DIALOG_TAG_REALLY_DELETE_MY_GROUP = "rdmg"
        const val DIALOG_TAG_REALLY_DELETE_GROUP = "rdgcc"
        const val DIALOG_TAG_REALLY_DELETE_DISTRIBUTION_LIST = "rddl"
        const val DIALOG_TAG_REALLY_EMPTY_CHAT = "remc"
        const val DIALOG_TAG_REALLY_DELETE_CHAT = "rdec"

        const val ID_PRIVATE_TO_PUBLIC = 8111

        const val TAG_EMPTY_CHAT = 1
        const val TAG_DELETE_DISTRIBUTION_LIST = 2
        const val TAG_LEAVE_GROUP = 3
        const val TAG_DISSOLVE_GROUP = 4
        const val TAG_DELETE_MY_GROUP = 5
        const val TAG_DELETE_GROUP = 6
        const val TAG_SET_PRIVATE = 7
        const val TAG_UNSET_PRIVATE = 8
        const val TAG_SHARE = 9
        const val TAG_DELETE_LEFT_GROUP = 10
        const val TAG_EDIT_GROUP = 11
        const val TAG_MARK_READ = 12
        const val TAG_MARK_UNREAD = 13
        const val TAG_DELETE_CHAT = 14
        const val TAG_ARCHIVE_CHAT = 15

        val DURATION_ARCHIVE_UNDO_SNACKBAR: Duration = 7.seconds

        const val BUNDLE_FILTER_QUERY = "filterQuery"
        var highlightUid: String? = null
    }
}
