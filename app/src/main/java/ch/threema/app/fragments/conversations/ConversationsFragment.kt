package ch.threema.app.fragments.conversations

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.launch
import androidx.annotation.AnyThread
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.appcompat.widget.SearchView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuItemCompat
import androidx.fragment.app.setFragmentResultListener
import ch.threema.app.AppConstants
import ch.threema.app.AppConstants.MAX_PW_LENGTH_BACKUP
import ch.threema.app.AppConstants.MIN_PW_LENGTH_BACKUP
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.activities.DistributionListAddActivity
import ch.threema.app.activities.RecipientListBaseActivity
import ch.threema.app.activities.ThreemaActivity
import ch.threema.app.applock.AppLockUtil
import ch.threema.app.applock.CheckAppLockContract
import ch.threema.app.archive.ArchiveActivity
import ch.threema.app.availabilitystatus.AvailabilityStatusOwnBanner
import ch.threema.app.availabilitystatus.edit.EditAvailabilityStatusBottomSheetDialog
import ch.threema.app.backuprestore.BackupChatService
import ch.threema.app.compose.common.LocalDayOfYear
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.buttons.ButtonIconInfo
import ch.threema.app.compose.common.buttons.ButtonOutlined
import ch.threema.app.compose.common.buttons.primary.ExtendedFloatingActionButtonPrimary
import ch.threema.app.compose.common.list.swipe.ListItemSwipeFeature
import ch.threema.app.compose.common.list.swipe.ListItemSwipeFeatureState
import ch.threema.app.compose.common.rememberRefreshingLocalDayOfYear
import ch.threema.app.compose.conversation.ConversationListItem
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.contactdetails.ContactDetailActivity
import ch.threema.app.dialogs.CancelableGenericProgressDialog
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.dialogs.PasswordEntryDialog
import ch.threema.app.dialogs.SelectorDialog
import ch.threema.app.dialogs.SimpleStringAlertDialog
import ch.threema.app.dialogs.loadingtimeout.LoadingWithTimeoutDialogXml
import ch.threema.app.fragments.MainFragment
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.groupflows.GROUP_FLOWS_LOADING_DIALOG_TIMEOUT_SECONDS
import ch.threema.app.groupflows.GroupDisbandIntent
import ch.threema.app.groupflows.GroupFlowResult
import ch.threema.app.groupflows.GroupLeaveIntent
import ch.threema.app.listeners.ContactListener
import ch.threema.app.listeners.DistributionListListener
import ch.threema.app.listeners.GroupListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.SettingsActivity
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.LockAppService
import ch.threema.app.services.UserService
import ch.threema.app.ui.ResumePauseHandler
import ch.threema.app.ui.SelectorDialogItem
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.EditTextUtil
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.MimeUtil
import ch.threema.app.utils.RuntimeUtil
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.app.widget.WidgetUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.onCompleted
import ch.threema.common.consume
import ch.threema.common.toIntCapped
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.DistributionListReceiverIdentifier
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.ConversationUID
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.domain.types.toIdentityOrNull
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.ConversationTag
import ch.threema.storage.models.group.GroupModelOld
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.lang.ref.WeakReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import org.slf4j.Logger

private val logger: Logger = getThreemaLogger("ConversationsFragment")

/**
 * This is one of the tabs in the home screen. It shows the current conversations.
 */
class ConversationsFragment :
    MainFragment(),
    DialogClickListener,
    CancelableGenericProgressDialog.ProgressDialogClickListener,
    SelectorDialog.SelectorDialogClickListener {

    private val userService: UserService by inject()
    private val conversationService: ConversationService by inject()
    private val groupService: GroupService by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val groupFlowDispatcher: GroupFlowDispatcher by inject()
    private val backupChatService: BackupChatService by inject()
    private val conversationCategoryService: ConversationCategoryService by inject()
    private val conversationTagService: ConversationTagService by inject()
    private val fileService: FileService by inject()
    private val preferenceService: PreferenceService by inject()
    private val lockAppService: LockAppService by inject()
    private val appLockUtil: AppLockUtil by inject()
    private val appRestrictions: AppRestrictions by inject()
    private val widgetUpdater: WidgetUpdater by inject()

    private var tempMessagesFile: File? = null
    private var searchView: SearchView? = null
    private var searchMenuItemRef: WeakReference<MenuItem>? = null
    private var toggleHiddenMenuItemRef: WeakReference<MenuItem>? = null
    private lateinit var resumePauseHandler: ResumePauseHandler

    private var restoredFilterQuery: String? = null

    private var myIdentity: Identity? = null

    private var archiveSnackbar: ArchiveSnackbar? = null

    private var selectedConversation: ConversationModel? = null

    private val viewModel: ConversationsViewModel by viewModel {
        val initiallyOpenedConversationUID: ConversationUID? = arguments?.getString(OPENED_CONVERSATION_UID)
        parametersOf(isMultiPaneEnabled(), initiallyOpenedConversationUID)
    }

    // TODO(ANDR-4275): Remove this listener from the UI layer and handle this trigger somewhere else
    // Right now it is used to keep Threema-Web up-to-date regarding new groups (created by user or added by contact)
    private val groupListener: GroupListener = object : GroupListener {
        override fun onNewMember(groupIdentity: GroupIdentity, identityNew: IdentityString?) {
            if (myIdentity != null && myIdentity!!.value == identityNew) {
                groupService.getByGroupIdentity(groupIdentity)?.let { groupModel ->
                    fireReceiverUpdate(groupService.createReceiver(groupModel))
                }
            }
        }
    }

    private val queryTextListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextChange(filterQuery: String?) = consume {
            viewModel.onFilterQueryChange(filterQuery)
        }

        override fun onQueryTextSubmit(query: String?) = true
    }

    private val checkLockToShowPrivateConversationLauncher = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            viewModel.onUnlockSuccessToShowPrivateConversations()
        }
    }
    private val checkLockToUnmarkConversationAsPrivateLauncher = registerForActivityResult(CheckAppLockContract()) { unlocked ->
        if (unlocked) {
            selectedConversation?.let { conversationModel ->
                viewModel.onUnlockSuccessToUnmarkConversationAsPrivate(conversationModel)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logger.info("onCreate")

        setRetainInstance(true)
        setHasOptionsMenu(true)

        ListenerManager.groupListeners.add(groupListener)

        this.resumePauseHandler = ResumePauseHandler.getByActivity(this, requireActivity())

        if (ConfigUtils.supportsAvailabilityStatus()) {
            setFragmentResultListener(
                requestKey = REQUEST_KEY_EDIT_AVAILABILITY_STATUS,
            ) { _, bundle ->
                val didChangeStatus = bundle.getBoolean(EditAvailabilityStatusBottomSheetDialog.RESULT_KEY_DID_CHANGE_STATUS)
                if (didChangeStatus) {
                    viewModel.onAvailabilityStatusWasChanged()
                }
            }
        }
    }

    override fun onDestroy() {
        ListenerManager.groupListeners.remove(groupListener)
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logger.info("onViewCreated")
        if (savedInstanceState != null) {
            restoredFilterQuery = savedInstanceState.getString(BUNDLE_FILTER_QUERY)
        }
    }

    override fun onDestroyView() {
        logger.info("onDestroyView")

        searchView = null
        searchMenuItemRef?.clear()

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

        // Wrong state direction required here right now. As long as the options menu is still xml and created by the fragment
        viewModel.viewState.value?.hasPrivateConversations?.let { hasPrivateConversations ->
            setShowOrHidePrivateConversationsMenuItemVisible(
                isVisible = hasPrivateConversations,
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        logger.debug("onCreateOptionsMenu")

        if (activity == null || isMultiPaneEnabled()) {
            return
        }

        var searchMenuItem: MenuItem? = menu.findItem(R.id.menu_search_messages)
        if (searchMenuItem == null) {
            inflater.inflate(R.menu.fragment_conversations, menu)
            if (isAdded) {
                searchMenuItem = menu.findItem(R.id.menu_search_messages)
                searchView = searchMenuItem.actionView as SearchView?
                searchView?.let { searchView ->
                    searchView.setQueryHint(getString(R.string.hint_filter_list))
                    searchView.setOnQueryTextListener(queryTextListener)
                    if (!restoredFilterQuery.isNullOrEmpty()) {
                        // restore filter
                        MenuItemCompat.expandActionView(searchMenuItem)
                        searchView.setQuery(restoredFilterQuery, false)
                        searchView.clearFocus()
                    }
                }
            }
        }

        if (searchMenuItem != null) {
            searchMenuItemRef = WeakReference<MenuItem>(searchMenuItem)
        } else {
            searchMenuItemRef?.clear()
        }
        val togglePrivateConversationsMenuItem: MenuItem? = menu.findItem(R.id.menu_toggle_private_chats)
        if (togglePrivateConversationsMenuItem != null) {
            toggleHiddenMenuItemRef = WeakReference<MenuItem>(togglePrivateConversationsMenuItem)
        } else {
            toggleHiddenMenuItemRef?.clear()
        }
        toggleHiddenMenuItemRef?.get()?.let { menuItem: MenuItem ->
            if (!isAdded) {
                return@let
            }
            menuItem.setOnMenuItemClickListener { _ ->
                consume {
                    logger.info("Clicked on menu item to toggle hide/show private conversations")
                    viewModel.onClickHideOrShowPrivateConversations()
                }
            }
        }
    }

    private fun openConversation(receiverIdentifier: ReceiverIdentifier) {
        if (!isAdded) {
            return
        }

        // Close keyboard if search view is expanded
        if (searchView?.isIconified == false) {
            EditTextUtil.hideSoftKeyboard(searchView)
        }

        val openConversationIntent: Intent = ComposeMessageActivity.createIntent(requireActivity(), receiverIdentifier)
        if (isMultiPaneEnabled()) {
            openConversationIntent.setFlags(
                Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            startActivityForResult(openConversationIntent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE)
            requireActivity().overridePendingTransition(0, 0)
        } else {
            requireActivity().startActivityForResult(openConversationIntent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE)
        }
    }

    private fun onOpenConversationActionDialog(conversationModel: ConversationModel) {
        val labels: MutableList<SelectorDialogItem> = mutableListOf()
        val tags: MutableList<Int> = mutableListOf()

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
            tags.add(TAG_UNMARK_AS_PRIVATE)
        } else {
            labels.add(SelectorDialogItem(getString(R.string.set_private), R.drawable.ic_privacy_outline))
            tags.add(TAG_MARK_AS_PRIVATE)
        }

        if (!isPrivate && !appRestrictions.isExportDisabled()) {
            labels.add(SelectorDialogItem(getString(R.string.share_chat), R.drawable.ic_share_outline))
            tags.add(TAG_SHARE)
        }

        labels.add(SelectorDialogItem(getString(R.string.archive_chat), R.drawable.ic_archive_outline))
        tags.add(TAG_ARCHIVE_CONVERSATION)

        if (conversationModel.messageCount > 0) {
            labels.add(SelectorDialogItem(getString(R.string.empty_chat_title), R.drawable.ic_outline_delete_sweep))
            tags.add(TAG_EMPTY_CONVERSATION)
        }
        if (conversationModel.isContactConversation) {
            labels.add(SelectorDialogItem(getString(R.string.delete_chat_title), R.drawable.ic_delete_outline))
            tags.add(TAG_DELETE_CONVERSATION)
        }

        if (conversationModel.isDistributionListConversation) {
            // distribution lists
            labels.add(SelectorDialogItem(getString(R.string.really_delete_distribution_list), R.drawable.ic_delete_outline))
            tags.add(TAG_DELETE_DISTRIBUTION_LIST)
        } else if (conversationModel.isGroupConversation) {
            // group conversations
            val groupModel = conversationModel.groupModel ?: run {
                logger.error("Cannot access the group from the conversation model")
                return
            }
            val isCreator: Boolean = groupModel.isCreator()
            val isMember: Boolean = groupModel.isMember()
            // Check also if the user is a group member, because left groups should not be editable.
            if (groupModel.isCreator() && groupModel.isMember()) {
                labels.add(SelectorDialogItem(getString(R.string.group_edit_title), R.drawable.ic_pencil_outline))
                tags.add(TAG_EDIT_GROUP)
            }
            // Members (except the creator) can leave the group
            if (groupModel.isLeavable()) {
                labels.add(SelectorDialogItem(getString(R.string.action_leave_group), R.drawable.ic_outline_directions_run))
                tags.add(TAG_LEAVE_GROUP)
            }
            if (groupModel.isDisbandable()) {
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
            receiver.getDisplayName(preferenceService.getContactNameFormat()),
            ArrayList(labels),
            ArrayList(tags),
            getString(R.string.cancel),
        )
        selectorDialog.setData(conversationModel)
        selectorDialog.setTargetFragment(this, 0)
        selectorDialog.show(parentFragmentManager, DIALOG_TAG_SELECT_DELETE_ACTION)
    }

    private fun onConversationArchived(conversationModel: ConversationModel) {
        archiveSnackbar = ArchiveSnackbar(archiveSnackbar, conversationModel)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ThreemaActivity.ACTIVITY_ID_SHARE_CONVERSATION -> tempMessagesFile?.let { file ->
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

            REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_MARK_CONVERSATION_PRIVATE -> {
                if (ConfigUtils.hasProtection(preferenceService)) {
                    selectedConversation?.let { conversationModel ->
                        viewModel.onLockingMechanismConfiguredToMarkConversationAsPrivate(conversationModel)
                    }
                }
            }

            REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_UN_MARK_CONVERSATION_PRIVATE -> {
                if (ConfigUtils.hasProtection(preferenceService)) {
                    selectedConversation?.let { conversationModel ->
                        viewModel.onLockingMechanismConfiguredToUnmarkConversationAsPrivate(conversationModel)
                    }
                }
            }

            REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_HIDE_PRIVATE_CONVERSATIONS -> viewModel.onLockingMechanismConfiguredToHidePrivateConversations()

            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun shareConversation(conversationModel: ConversationModel?, password: String, includeMedia: Boolean) {
        if (conversationModel == null) {
            showSharingConversationFailedDialog()
            return
        }

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
            if (backupChatService.backupChatToZip(conversationModel, tempMessagesFile!!, password, includeMedia)) {
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
                            ThreemaActivity.ACTIVITY_ID_SHARE_CONVERSATION,
                        )
                    }
                }
            } else {
                RuntimeUtil.runOnUiThread {
                    DialogUtil.dismissDialog(parentFragmentManager, DIALOG_TAG_PREPARING_MESSAGES, true)
                    showSharingConversationFailedDialog()
                }
            }
        }.start()
    }

    private fun showLockingMechanismRequiredToUpdateConversationPrivateMarkDialog(
        conversationModel: ConversationModel,
        targetValueIsMarkedAsPrivate: Boolean,
    ) {
        logger.info("Showing dialog to explain private conversations")
        val dialog = GenericAlertDialog.newInstance(
            R.string.hide_chat,
            R.string.hide_chat_message_explain,
            R.string.set_lock,
            R.string.cancel,
        )
        dialog.setCallback { _, _ ->
            logger.info("Clicked on button to open settings screen to set up a locking mechanism (goal: toggle private mark for a conversation)")
            selectedConversation = conversationModel
            val intent = SettingsActivity.createIntent(requireContext(), SettingsActivity.InitialScreen.SECURITY)
            val requestCode = if (targetValueIsMarkedAsPrivate) {
                REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_MARK_CONVERSATION_PRIVATE
            } else {
                REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_UN_MARK_CONVERSATION_PRIVATE
            }
            startActivityForResult(intent, requestCode)
        }
        dialog.show(parentFragmentManager)
    }

    private fun showConfirmationDialogToMarkConversationAsPrivate(conversationModel: ConversationModel) {
        logger.info("Showing dialog to confirm mark conversation as private")
        val dialog = GenericAlertDialog.newInstance(
            R.string.hide_chat,
            R.string.really_hide_chat_message,
            R.string.ok,
            R.string.cancel,
        )
        dialog.setCallback { _, _ ->
            logger.info("Clicked on button to confirm marking conversation as private")
            viewModel.onClickConfirmMarkConversationAsPrivate(conversationModel)
        }
        dialog.show(parentFragmentManager)
    }

    private fun onUnlockRequiredToUnmarkConversationAsPrivate(conversationModel: ConversationModel) {
        selectedConversation = conversationModel
        checkLockToUnmarkConversationAsPrivateLauncher.launch()
    }

    private fun showShareConversationSetPasswordDialog(conversationModel: ConversationModel) {
        val passwordEntryDialog: PasswordEntryDialog = PasswordEntryDialog.newInstance(
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
        passwordEntryDialog.setData(conversationModel)
        passwordEntryDialog.setCallback { _, password: String, includeMedia: Boolean, data: Any? ->
            logger.info("Clicked on confirm button to share conversation")
            shareConversation(
                conversationModel = data as ConversationModel?,
                password = password,
                includeMedia = includeMedia,
            )
        }
        passwordEntryDialog.show(parentFragmentManager)
    }

    private fun showConfirmationDialogBeforeEmpty(conversationModel: ConversationModel) {
        val dialog: GenericAlertDialog = GenericAlertDialog.newInstance(
            R.string.empty_chat_title,
            R.string.empty_chat_confirm,
            R.string.ok,
            R.string.cancel,
        )
        dialog.setData(conversationModel)
        dialog.setCallback { _, data ->
            logger.info("Clicked on confirm button to empty conversation")
            (data as? ConversationModel)?.let { conversationModel ->
                viewModel.onClickedConfirmEmptyConversation(
                    conversationModel = conversationModel,
                    fragmentManager = parentFragmentManager,
                )
            }
        }
        dialog.show(parentFragmentManager)
    }

    private fun showConfirmDeleteContactConversationDialog(conversationModel: ConversationModel) {
        val dialog: GenericAlertDialog = GenericAlertDialog.newInstance(
            R.string.delete_chat_title,
            R.string.delete_chat_confirm,
            R.string.ok,
            R.string.cancel,
        )
        dialog.setData(conversationModel)
        dialog.setCallback { _, data ->
            logger.info("Clicked on confirm button to delete contact conversation")
            (data as? ConversationModel)?.let { conversationModel ->
                viewModel.onClickedConfirmDeleteContactConversation(
                    conversationModel = conversationModel,
                    fragmentManager = parentFragmentManager,
                )
            }
        }
        dialog.show(parentFragmentManager)
    }

    private fun showConfirmDeleteDistributionListConversationDialog(conversationModel: ConversationModel) {
        val dialog: GenericAlertDialog = GenericAlertDialog.newInstance(
            R.string.really_delete_distribution_list,
            R.string.really_delete_distribution_list_message,
            R.string.ok,
            R.string.cancel,
        )
        dialog.setData(conversationModel)
        dialog.setCallback { _, data ->
            logger.info("Clicked on confirm button to delete distribution list conversation")
            (data as? ConversationModel)?.let { conversationModel ->
                viewModel.onClickedConfirmDeleteDistributionListConversation(
                    conversationModel = conversationModel,
                    fragmentManager = parentFragmentManager,
                )
            }
        }
        dialog.show(parentFragmentManager)
    }

    @UiThread
    private fun showSharingConversationFailedDialog() {
        if (!isAdded) {
            return
        }
        SimpleStringAlertDialog
            .newInstance(R.string.share_via, getString(R.string.an_error_occurred))
            .show(parentFragmentManager)
    }

    private fun onLockingMechanismRequiredToHidePrivateConversationsEvent() {
        val dialog = GenericAlertDialog.newInstance(
            R.string.hide_chat,
            R.string.hide_chat_message_explain,
            R.string.set_lock,
            R.string.cancel,
        )
        dialog.setTargetFragment(this, 0)
        dialog.setCallback { _, _ ->
            logger.info("Clicked on button to open settings screen to set up a locking mechanism (goal: hide private conversations)")
            val intent = SettingsActivity.createIntent(requireContext(), SettingsActivity.InitialScreen.SECURITY)
            startActivityForResult(intent, REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_HIDE_PRIVATE_CONVERSATIONS)
        }
        dialog.show(parentFragmentManager)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        myIdentity = userService.getIdentity()?.toIdentityOrNull()
        return ComposeView(requireContext()).apply {
            setContent {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                EventHandler(viewModel) { event ->
                    handleEvent(
                        event = event,
                        requestShowSnackbar = { messageRes ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = getString(messageRes),
                                )
                            }
                        },
                    )
                }

                val contentWindowInsets: WindowInsets =
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(
                            if (isMultiPaneEnabled()) {
                                WindowInsetsSides.Left + WindowInsetsSides.Bottom
                            } else {
                                WindowInsetsSides.Horizontal
                            },
                        )

                ThreemaTheme {
                    val lazyListState = rememberLazyListState()

                    Scaffold(
                        snackbarHost = {
                            SnackbarHost(snackbarHostState)
                        },
                        contentWindowInsets = contentWindowInsets,
                        floatingActionButton = {
                            ExtendedFloatingActionButtonPrimary(
                                expanded = lazyListState.isScrollingUp(),
                                icon = ButtonIconInfo(
                                    icon = R.drawable.ic_chat_bubble,
                                    contentDescription = R.string.title_compose_message,
                                ),
                                text = stringResource(R.string.title_compose_message),
                                onClick = ::onFABClicked,
                            )
                        },
                    ) { insetsPadding ->
                        WithViewState(viewModel) { conversationsViewState ->
                            if (conversationsViewState != null) {
                                LaunchedEffect(conversationsViewState.hasPrivateConversations) {
                                    setShowOrHidePrivateConversationsMenuItemVisible(
                                        isVisible = conversationsViewState.hasPrivateConversations,
                                    )
                                }

                                val emojiStyle: Int = remember {
                                    preferenceService.getEmojiStyle()
                                }

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (conversationsViewState.availabilityStatus is AvailabilityStatus.Set) {
                                        val layoutDirection = LocalLayoutDirection.current
                                        AvailabilityStatusOwnBanner(
                                            modifier = Modifier
                                                .padding(
                                                    start = insetsPadding.calculateStartPadding(layoutDirection),
                                                    end = insetsPadding.calculateEndPadding(layoutDirection),
                                                )
                                                .padding(
                                                    all = GridUnit.x1,
                                                )
                                                .widthIn(
                                                    max = 550.dp,
                                                )
                                                .fillMaxWidth(),
                                            status = conversationsViewState.availabilityStatus,
                                            onClickEdit = {
                                                EditAvailabilityStatusBottomSheetDialog
                                                    .newInstance(
                                                        requestKey = REQUEST_KEY_EDIT_AVAILABILITY_STATUS,
                                                    )
                                                    .show(
                                                        /* manager = */
                                                        parentFragmentManager,
                                                        /* tag = */
                                                        "edit-availability-status-from-conversations-list",
                                                    )
                                            },
                                            emojiStyle = emojiStyle,
                                        )
                                    }

                                    when (val itemsState = conversationsViewState.itemsState) {
                                        is ItemsState.Loaded -> {
                                            if (itemsState.items.isNotEmpty()) {
                                                ConversationList(
                                                    insetsPadding = insetsPadding,
                                                    myIdentity = myIdentity!!,
                                                    lazyListState = lazyListState,
                                                    emojiStyle = emojiStyle,
                                                    items = itemsState.items,
                                                    contactNameFormat = conversationsViewState.contactNameFormat,
                                                    archivedCount = conversationsViewState.archivedConversationsCount,
                                                    filterQuery = conversationsViewState.filterQuery,
                                                )
                                            }

                                            AnimatedVisibility(
                                                visible = itemsState.items.isEmpty(),
                                                enter = fadeIn(
                                                    animationSpec = spring(
                                                        stiffness = Spring.StiffnessVeryLow,
                                                    ),
                                                ),
                                                exit = fadeOut(
                                                    animationSpec = spring(
                                                        stiffness = Spring.StiffnessMedium,
                                                    ),
                                                ),
                                            ) {
                                                EmptyContent(
                                                    insetsPadding = insetsPadding,
                                                    conversationsViewState = conversationsViewState,
                                                )
                                            }
                                        }
                                        ItemsState.Failed -> {
                                            FailureContent(
                                                insetsPadding = insetsPadding,
                                                onClickContactSupport = viewModel::onClickContactSupport,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     *  @param requestShowSnackbar Block receiving [StringRes]
     */
    private fun handleEvent(
        event: ConversationsViewEvent,
        requestShowSnackbar: (Int) -> Unit,
    ) {
        when (event) {
            is ConversationsViewEvent.OpenConversationActionDialog ->
                onOpenConversationActionDialog(
                    conversationModel = event.conversationModel,
                )

            is ConversationsViewEvent.ConversationArchived ->
                onConversationArchived(
                    conversationModel = event.conversationModel,
                )

            is ConversationsViewEvent.LockingMechanismRequiredToUpdatePrivateConversationMark ->
                showLockingMechanismRequiredToUpdateConversationPrivateMarkDialog(
                    conversationModel = event.conversationModel,
                    targetValueIsMarkedAsPrivate = event.targetValueIsMarkedAsPrivate,
                )

            is ConversationsViewEvent.ConfirmationRequiredToMarkConversationAsPrivate ->
                showConfirmationDialogToMarkConversationAsPrivate(
                    conversationModel = event.conversationModel,
                )

            ConversationsViewEvent.ConversationMarkAsPrivateSuccess -> requestShowSnackbar(R.string.chat_hidden)

            is ConversationsViewEvent.UnlockRequiredToUnmarkConversationAsPrivate ->
                onUnlockRequiredToUnmarkConversationAsPrivate(
                    conversationModel = event.conversationModel,
                )

            ConversationsViewEvent.ConversationUnmarkAsPrivateSuccess -> requestShowSnackbar(R.string.chat_visible)

            ConversationsViewEvent.UnlockRequiredToShowPrivateConversations ->
                checkLockToShowPrivateConversationLauncher.launch()

            ConversationsViewEvent.LockingMechanismRequiredToHidePrivateConversations ->
                onLockingMechanismRequiredToHidePrivateConversationsEvent()

            is ConversationsViewEvent.ConfirmationRequiredToEmptyConversation ->
                showConfirmationDialogBeforeEmpty(
                    conversationModel = event.conversationModel,
                )

            is ConversationsViewEvent.ConfirmationRequiredToDeleteContactConversation ->
                showConfirmDeleteContactConversationDialog(
                    conversationModel = event.conversationModel,
                )

            is ConversationsViewEvent.ConfirmationRequiredToDeleteDistributionListConversation ->
                showConfirmDeleteDistributionListConversationDialog(
                    conversationModel = event.conversationModel,
                )

            ConversationsViewEvent.OnSystemLockWasRemoved -> requestShowSnackbar(R.string.no_lockscreen_set)

            is ConversationsViewEvent.OnSupportContactAvailable -> openConversation(event.receiverIdentifier)

            is ConversationsViewEvent.OnSupportContactUnavailable -> requestShowSnackbar(event.message)

            ConversationsViewEvent.UpdateWidgets -> widgetUpdater.updateWidgets()

            ConversationsViewEvent.OnAvailabilityStatusChanged -> requestShowSnackbar(R.string.edit_availability_status_did_change)

            ConversationsViewEvent.InternalError -> requestShowSnackbar(R.string.an_error_occurred)
        }
    }

    @Composable
    private fun ConversationList(
        insetsPadding: PaddingValues,
        myIdentity: Identity,
        lazyListState: LazyListState,
        @EmojiStyle emojiStyle: Int,
        items: List<ConversationListItemUiModel>,
        contactNameFormat: ContactNameFormat,
        archivedCount: Long,
        filterQuery: String?,
    ) {
        val localDayOfYear: LocalDayOfYear by rememberRefreshingLocalDayOfYear()

        var wasAtTop by remember { mutableStateOf(true) }

        LaunchedEffect(lazyListState) {
            snapshotFlow {
                lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            }
                .distinctUntilChanged()
                .collect { isAtTop -> wasAtTop = isAtTop }
        }

        LaunchedEffect(items.firstOrNull()) {
            if (wasAtTop && items.isNotEmpty()) {
                lazyListState.scrollToItem(0)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(insetsPadding)
                .testTag("lazy_column_conversations"),
            contentPadding = PaddingValues(
                bottom = GridUnit.x15,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            state = lazyListState,
        ) {
            items(
                items = items,
                key = { conversationListItemUiModel ->
                    conversationListItemUiModel.model.conversationUID
                },
                contentType = { "conversation" },
            ) { conversationListItemUiModel ->
                ConversationListItem(
                    modifier = Modifier.animateItem(),
                    conversationListItemUiModel = conversationListItemUiModel,
                    avatarIteration = conversationListItemUiModel.model.avatarIteration,
                    localDayOfYear = localDayOfYear,
                    avatarBitmapProvider = viewModel::provideAvatarBitmap,
                    ownIdentity = myIdentity,
                    emojiStyle = emojiStyle,
                    contactNameFormat = contactNameFormat,
                    onClick = { conversationUiModel ->
                        if (!isConversationOpenedInMultiPaneMode(conversationListItemUiModel)) {
                            openConversation(conversationUiModel.receiverIdentifier)
                        }
                    },
                    onLongClick = { conversationUiModel ->
                        if (!isMultiPaneEnabled()) {
                            viewModel.onLongClickConversationItem(conversationUiModel)
                        }
                    },
                    onClickAvatar = ::onAvatarClicked,
                    onClickJoinOrOpenGroupCall = { groupReceiverIdentifier ->
                        startActivity(
                            GroupCallActivity.createJoinCallIntent(
                                requireActivity(),
                                groupReceiverIdentifier.groupDatabaseId.toInt(),
                            ),
                        )
                    },
                    swipeFeatureStartToEnd = ListItemSwipeFeature.StartToEnd(
                        onSwipe = viewModel::onSwipedListItemPin,
                        containerColor = colorResource(R.color.message_list_pin_color),
                        contentColor = Color.White,
                        state = ListItemSwipeFeatureState(
                            icon = if (conversationListItemUiModel.model.isPinned) {
                                R.drawable.ic_unpin
                            } else {
                                R.drawable.ic_pin
                            },
                            text = stringResource(
                                if (conversationListItemUiModel.model.isPinned) {
                                    R.string.unpin
                                } else {
                                    R.string.pin
                                },
                            ),
                            enabled = !isMultiPaneEnabled(),
                        ),
                    ),
                    swipeFeatureEndToStart = ListItemSwipeFeature.EndToStart(
                        onSwipe = viewModel::onSwipedListItemArchive,
                        containerColor = colorResource(R.color.message_list_archive_color),
                        contentColor = Color.White,
                        state = ListItemSwipeFeatureState(
                            icon = R.drawable.ic_archive_outline,
                            text = stringResource(R.string.to_archive),
                            enabled = !isMultiPaneEnabled(),
                        ),
                    ),
                )
            }
            if (archivedCount > 0) {
                item(
                    key = archivedCount,
                    contentType = "archive_button",
                ) {
                    ArchivedButton(
                        modifier = Modifier.padding(top = GridUnit.x3),
                        archivedCount = archivedCount,
                        filterQuery = filterQuery,
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyContent(
        insetsPadding: PaddingValues,
        conversationsViewState: ConversationsViewState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(
                    state = rememberScrollState(),
                )
                .padding(insetsPadding)
                .padding(horizontal = GridUnit.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SpacerVertical(GridUnit.x4)
            Icon(
                modifier = Modifier.size(GridUnit.x7),
                painter = painterResource(R.drawable.ic_no_conversations),
                contentDescription = null,
                tint = LocalContentColor.current,
            )
            SpacerVertical(GridUnit.x2)
            ThemedText(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.no_recent_conversations),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            AnimatedVisibility(
                visible = conversationsViewState.archivedConversationsCount > 0L,
            ) {
                ArchivedButton(
                    modifier = Modifier
                        .padding(horizontal = GridUnit.x3)
                        .padding(top = GridUnit.x6),
                    archivedCount = conversationsViewState.archivedConversationsCount,
                    filterQuery = conversationsViewState.filterQuery,
                )
            }
            SpacerVertical(GridUnit.x15)
        }
    }

    @Composable
    private fun FailureContent(
        modifier: Modifier = Modifier,
        insetsPadding: PaddingValues,
        onClickContactSupport: () -> Unit,
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(
                    state = rememberScrollState(),
                )
                .padding(insetsPadding)
                .padding(horizontal = GridUnit.x2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SpacerVertical(GridUnit.x4)
            Icon(
                modifier = Modifier.size(GridUnit.x7),
                painter = painterResource(R.drawable.ic_error_chats),
                contentDescription = null,
                tint = LocalContentColor.current.copy(
                    alpha = 0.6f,
                ),
            )
            SpacerVertical(GridUnit.x4)
            ThemedText(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.failed_to_load_conversations),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (!BuildFlavor.current.isOnPrem) {
                SpacerVertical(GridUnit.x6)
                ButtonOutlined(
                    onClick = onClickContactSupport,
                    text = stringResource(R.string.contact_support),
                    maxLines = 2,
                )
            }
            SpacerVertical(GridUnit.x15)
        }
    }

    @Composable
    private fun ArchivedButton(
        modifier: Modifier = Modifier,
        archivedCount: Long,
        filterQuery: String?,
    ) {
        val archivedCountInt: Int = remember(archivedCount) {
            archivedCount.toIntCapped()
        }

        ButtonOutlined(
            modifier = modifier,
            onClick = {
                startActivity(
                    ArchiveActivity.createIntent(
                        context = requireActivity(),
                        filterQuery = filterQuery,
                    ),
                )
            },
            text = pluralStringResource(
                id = R.plurals.num_archived_chats,
                count = archivedCountInt,
                archivedCountInt,
            ),
            leadingIcon = ButtonIconInfo(
                icon = R.drawable.ic_archive_outline,
                contentDescription = null,
            ),
        )
    }

    private fun isConversationOpenedInMultiPaneMode(
        conversationListItemUiModel: ConversationListItemUiModel,
    ): Boolean {
        return isMultiPaneEnabled() && conversationListItemUiModel.isHighlighted
    }

    private fun onFABClicked() {
        logger.info("Clicked on button to create a new conversation")
        val intent: Intent = Intent(context, RecipientListBaseActivity::class.java).apply {
            putExtra(AppConstants.INTENT_DATA_HIDE_RECENTS, true)
            putExtra(RecipientListBaseActivity.INTENT_DATA_MULTISELECT, false)
            putExtra(RecipientListBaseActivity.INTENT_DATA_MULTISELECT_FOR_COMPOSE, true)
        }
        requireActivity().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_COMPOSE_MESSAGE)
    }

    private fun onAvatarClicked(conversationUiModel: ConversationUiModel) {
        val intent = when (val receiverIdentifier = conversationUiModel.receiverIdentifier) {
            is ContactReceiverIdentifier -> {
                ContactDetailActivity.createIntent(requireContext(), receiverIdentifier.identity)
            }

            is GroupReceiverIdentifier -> {
                groupService.getGroupDetailIntent(receiverIdentifier.groupDatabaseId, requireActivity())
            }

            is DistributionListReceiverIdentifier -> {
                DistributionListAddActivity.createIntent(requireContext(), receiverIdentifier.id)
            }
        }
        requireActivity().startActivity(intent)
    }

    override fun onProgressbarCanceled(tag: String?) {
        backupChatService.cancel()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        logger.debug("*** onHiddenChanged: {}", hidden)
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
        viewModel.onViewResumed(
            isAndroidSystemLockConfigured = appLockUtil.hasDeviceLock(),
        )
        super.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        logger.info("saveInstance")
        restoredFilterQuery = null
        searchView?.query?.takeIf(CharSequence::isNotBlank)?.let { existingFilterQuery ->
            outState.putString(BUNDLE_FILTER_QUERY, existingFilterQuery.toString())
        }
        super.onSaveInstanceState(outState)
    }

    override fun onClick(tag: String?, which: Int, data: Any?) {
        val conversationModel: ConversationModel = (data as? ConversationModel) ?: return

        when (which) {
            TAG_ARCHIVE_CONVERSATION -> {
                logger.info("Clicked on button to archive conversation")
                viewModel.onClickedArchiveConversation(conversationModel)
            }

            TAG_EMPTY_CONVERSATION -> {
                logger.info("Clicked on button to empty conversation")
                viewModel.onClickedEmptyConversation(conversationModel)
            }

            TAG_DELETE_CONVERSATION -> {
                logger.info("Clicked on button to delete contact conversation")
                viewModel.onClickedDeleteContactConversation(conversationModel)
            }

            TAG_DELETE_DISTRIBUTION_LIST -> {
                logger.info("Clicked on button to delete distribution list conversation")
                viewModel.onClickedDeleteDistributionListConversation(conversationModel)
            }

            TAG_EDIT_GROUP -> {
                logger.info("Clicked on button to edit group")
                conversationModel.group?.let { groupModel ->
                    requireActivity().startActivity(
                        groupService.getGroupDetailIntent(groupModel, requireActivity()),
                    )
                }
            }

            TAG_LEAVE_GROUP -> {
                logger.info("Clicked on button to leave group")
                val dialog = GenericAlertDialog.newInstance(
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
                logger.info("Clicked on button to dissolve group")
                val dialog = GenericAlertDialog.newInstance(
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
                logger.info("Clicked on button to delete own group")
                val dialog = GenericAlertDialog.newInstance(
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
                logger.info("Clicked on button to delete group")
                val dialog = GenericAlertDialog.newInstance(
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
                logger.info("Clicked on button to delete already left group")
                val dialog = GenericAlertDialog.newInstance(
                    R.string.action_delete_group,
                    R.string.delete_left_group_message,
                    R.string.ok,
                    R.string.cancel,
                )
                dialog.setTargetFragment(this, 0)
                dialog.setData(conversationModel.group!!)
                dialog.show(parentFragmentManager, DIALOG_TAG_REALLY_DELETE_GROUP)
            }

            TAG_MARK_AS_PRIVATE -> {
                logger.info("Clicked on button mark conversation as private")
                viewModel.onClickMarkConversationAsPrivate(conversationModel)
            }

            TAG_UNMARK_AS_PRIVATE -> {
                logger.info("Clicked on button to remove private mark from conversation")
                viewModel.onClickUnmarkConversationAsPrivate(conversationModel)
            }

            TAG_SHARE -> {
                logger.info("Clicked on button to share conversation")
                onClickedShareConversation(conversationModel)
            }

            TAG_MARK_READ -> {
                logger.info("Clicked on button to mark conversation as read")
                viewModel.onClickedMarkConversationAsRead(conversationModel)
            }

            TAG_MARK_UNREAD -> {
                logger.info("Clicked button to mark conversation as unread")
                viewModel.onClickedMarkConversationAsUnread(conversationModel)
            }
        }
    }

    private fun onClickedShareConversation(conversationModel: ConversationModel) {
        val hasWriteExternalStoragePermission: Boolean = ConfigUtils.requestWriteStoragePermissions(
            requireActivity(),
            this,
            PERMISSION_REQUEST_SHARE_THREAD,
        )
        if (hasWriteExternalStoragePermission) {
            showShareConversationSetPasswordDialog(conversationModel)
        }
    }

    override fun onYes(tag: String?, data: Any?) {
        when (tag) {
            DIALOG_TAG_REALLY_LEAVE_GROUP -> {
                logger.info("Clicked on confirm button to leave group")
                leaveGroup(GroupLeaveIntent.LEAVE, getNewGroupModel(data as GroupModelOld?))
            }

            DIALOG_TAG_REALLY_DISSOLVE_GROUP -> {
                logger.info("Clicked on confirm button to dissolve group")
                disbandGroup(GroupDisbandIntent.DISBAND, getNewGroupModel(data as GroupModelOld?))
            }

            DIALOG_TAG_REALLY_DELETE_MY_GROUP, DIALOG_TAG_REALLY_DELETE_GROUP -> {
                logger.info("Clicked on confirm button to delete (own) group")
                removeGroup(getNewGroupModel(data as GroupModelOld?))
            }

            else -> Unit
        }
    }

    private fun leaveGroup(
        intent: GroupLeaveIntent,
        groupModel: GroupModel?,
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
        groupDisbandIntent: GroupDisbandIntent,
        groupModel: GroupModel?,
    ) {
        if (groupModel == null) {
            logger.error("Cannot disband group: group model is null")
            SimpleStringAlertDialog
                .newInstance(R.string.error, R.string.error_disbanding_group_internal)
                .show(getParentFragmentManager())
            return
        }

        val disbandGroupFlowResultDeferred: Deferred<GroupFlowResult?> = groupFlowDispatcher
            .runDisbandGroupFlow(groupDisbandIntent, groupModel)

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

    private fun removeGroup(groupModel: GroupModel?) {
        if (groupModel == null) {
            // Group already removed
            return
        }

        val groupModelData: GroupModelData = groupModel.data ?: return
        if (groupModelData.isMember) {
            // Disband or leave if the user is still part of the group.
            if (groupModelData.groupIdentity.creatorIdentity == myIdentity?.value) {
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
        groupModel: GroupModel,
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

    private fun getNewGroupModel(groupModel: GroupModelOld?): GroupModel? {
        if (groupModel == null) {
            logger.error("Provided group model is null")
            return null
        }
        val newGroupModel: GroupModel? = groupModelRepository.getByCreatorIdentityAndId(
            creatorIdentity = groupModel.creatorIdentity,
            groupId = groupModel.apiGroupId,
        )
        if (newGroupModel == null) {
            logger.error("New group model is null")
        }
        return newGroupModel
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_SHARE_THREAD) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showShareConversationSetPasswordDialog(selectedConversation!!)
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ConfigUtils.showPermissionRationale(context, view, R.string.permission_storage_required)
            }
        }
    }

    private fun setShowOrHidePrivateConversationsMenuItemVisible(isVisible: Boolean) {
        val toggleHiddenMenuItem: MenuItem? = toggleHiddenMenuItemRef?.get()
        if (isAdded && toggleHiddenMenuItem != null) {
            toggleHiddenMenuItem.isVisible = isVisible
        }
    }

    private fun isMultiPaneEnabled(): Boolean = ConfigUtils.isTabletLayout() && activity is ComposeMessageActivity

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

    /**
     * Keeps track of the last archive conversations. This class is used for the undo action.
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
                    logger.info("Clicked on button to undo archiving conversation")
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

    companion object {

        const val OPENED_CONVERSATION_UID = "opened_conversation_uid"

        private const val PERMISSION_REQUEST_SHARE_THREAD = 1
        private const val REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_MARK_CONVERSATION_PRIVATE = 33211
        private const val REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_UN_MARK_CONVERSATION_PRIVATE = 33212
        private const val REQUEST_CODE_SET_UP_LOCKING_MECHANISM_TO_HIDE_PRIVATE_CONVERSATIONS = 33213
        private const val TEMP_MESSAGES_FILE_DELETE_WAIT_TIME = 2 * 60 * 1000

        private const val DIALOG_TAG_PREPARING_MESSAGES = "progressMsgs"
        private const val DIALOG_TAG_SELECT_DELETE_ACTION = "sel"
        private const val DIALOG_TAG_REALLY_LEAVE_GROUP = "rlg"
        private const val DIALOG_TAG_REALLY_DISSOLVE_GROUP = "reallyDissolveGroup"
        private const val DIALOG_TAG_REALLY_DELETE_MY_GROUP = "rdmg"
        private const val DIALOG_TAG_REALLY_DELETE_GROUP = "rdgcc"

        private const val REQUEST_KEY_EDIT_AVAILABILITY_STATUS = "edit-availability-status-from-conversation-list"

        private const val TAG_EMPTY_CONVERSATION = 1
        private const val TAG_DELETE_DISTRIBUTION_LIST = 2
        private const val TAG_LEAVE_GROUP = 3
        private const val TAG_DISSOLVE_GROUP = 4
        private const val TAG_DELETE_MY_GROUP = 5
        private const val TAG_DELETE_GROUP = 6
        private const val TAG_MARK_AS_PRIVATE = 7
        private const val TAG_UNMARK_AS_PRIVATE = 8
        private const val TAG_SHARE = 9
        private const val TAG_DELETE_LEFT_GROUP = 10
        private const val TAG_EDIT_GROUP = 11
        private const val TAG_MARK_READ = 12
        private const val TAG_MARK_UNREAD = 13
        private const val TAG_DELETE_CONVERSATION = 14
        private const val TAG_ARCHIVE_CONVERSATION = 15

        private val DURATION_ARCHIVE_UNDO_SNACKBAR: Duration = 7.seconds

        private const val BUNDLE_FILTER_QUERY = "filterQuery"
    }
}

/**
 * Returns whether the lazy list is currently scrolling up.
 */
@Composable
private fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}
