/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

package ch.threema.app.archive

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.asynctasks.EmptyOrDeleteConversationsAsyncTask
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.conversation.ConversationListItem
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.listeners.ConversationListener
import ch.threema.app.listeners.MessageListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.AvatarCacheService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.ThreemaSearchView
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.TestUtil
import ch.threema.base.ThreemaException
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ConversationModel
import com.google.android.material.appbar.MaterialToolbar
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("ArchiveActivity")

class ArchiveActivity : ThreemaToolbarActivity(), DialogClickListener, SearchView.OnQueryTextListener {
    // Services
    private lateinit var conversationService: ConversationService
    private lateinit var conversationCategoryService: ConversationCategoryService
    private lateinit var contactService: ContactService
    private lateinit var groupService: GroupService
    private lateinit var distributionListService: DistributionListService
    private lateinit var avatarCacheService: AvatarCacheService
    private lateinit var ringtoneService: RingtoneService
    private var groupModelRepository: GroupModelRepository? = null
    private var groupFlowDispatcher: GroupFlowDispatcher? = null

    private var actionMode: ActionMode? = null

    private val viewModel: ArchiveViewModel by viewModels<ArchiveViewModel>()

    override fun getLayoutResource(): Int = R.layout.activity_archive

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ListenerManager.conversationListeners.add(this.conversationListener)
        ListenerManager.messageListeners.add(this.messageListener)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ConfigUtils.adjustToolbar(this, toolbar)
    }

    override fun onDestroy() {
        ListenerManager.conversationListeners.remove(this.conversationListener)
        ListenerManager.messageListeners.remove(this.messageListener)
        super.onDestroy()
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        try {
            conversationService = serviceManager.conversationService
            conversationCategoryService = serviceManager.conversationCategoryService
            groupService = serviceManager.groupService
            contactService = serviceManager.contactService
            distributionListService = serviceManager.distributionListService
            avatarCacheService = serviceManager.avatarCacheService
            ringtoneService = serviceManager.ringtoneService
            groupModelRepository = serviceManager.modelRepositories.groups
            groupFlowDispatcher = serviceManager.groupFlowDispatcher
        } catch (e: ThreemaException) {
            logger.error("Exception", e)
            return false
        }

        appBarLayout?.applyDeviceInsetsAsPadding(
            insetSides = InsetSides.ltr(),
        )

        val toolbar = findViewById<MaterialToolbar>(R.id.material_toolbar)
        toolbar.setNavigationOnClickListener { _ -> finish() }
        toolbar.setTitle(R.string.archived_chats)

        val filterQuery = intent.getStringExtra(AppConstants.INTENT_DATA_ARCHIVE_FILTER)

        val filterMenu = toolbar.menu.findItem(R.id.menu_filter_archive)
        val searchView = filterMenu.actionView as ThreemaSearchView?

        if (searchView != null) {
            searchView.queryHint = getString(R.string.hint_filter_list)
            if (!TestUtil.isEmptyOrNull(filterQuery)) {
                filterMenu.expandActionView()
                searchView.setQuery(filterQuery, false)
            }
            searchView.post(Runnable { searchView.setOnQueryTextListener(this@ArchiveActivity) })
        } else {
            filterMenu.setVisible(false)
        }

        findViewById<ComposeView>(R.id.conversation_list).setContent {
            val conversationUiModels: List<ConversationUiModel> by viewModel.conversationUiModels.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (!filterQuery.isNullOrBlank()) {
                    viewModel.onFilterQueryChanged(filterQuery)
                } else {
                    viewModel.refresh()
                }
            }

            LaunchedEffect(conversationUiModels) {
                actionMode?.invalidate()
            }

            ThreemaTheme {
                Scaffold(
                    contentWindowInsets = WindowInsets
                        .safeDrawing
                        .only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) { contentPadding ->
                    ArchiveActivityContent(
                        contentPadding = contentPadding,
                        conversationUiModels = conversationUiModels,
                        onClickConversation = { conversationModel ->
                            actionMode?.let { currentActionMode ->
                                val hasAnyCheckedConversations = viewModel.toggleConversationChecked(
                                    conversationUid = conversationModel.uid,
                                )
                                if (hasAnyCheckedConversations) {
                                    currentActionMode.invalidate()
                                } else {
                                    currentActionMode.finish()
                                }
                            } ?: run {
                                showConversation(conversationModel)
                            }
                        },
                        onLongClickConversation = { conversationModel ->
                            actionMode?.finish()
                            val hasAnyCheckedConversations = viewModel.toggleConversationChecked(
                                conversationUid = conversationModel.uid,
                            )
                            if (hasAnyCheckedConversations) {
                                actionMode = startSupportActionMode(ArchiveActionCallback())
                            }
                        },
                    )
                }
            }
        }

        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return false
    }

    override fun onQueryTextChange(newText: String): Boolean {
        viewModel.onFilterQueryChanged(newText)
        return true
    }

    inner class ArchiveActionCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.action_archive, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val checkedCount = viewModel.checkedCount
            if (checkedCount > 0) {
                mode.title = checkedCount.toString()
                return true
            }
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.menu_delete -> {
                    delete(viewModel.checkedConversationModels)
                    true
                }

                R.id.menu_unarchive -> {
                    unarchive(viewModel.checkedConversationModels)
                    true
                }

                R.id.menu_select_all -> {
                    val hasAnyCheckedConversations = viewModel.checkAll()
                    actionMode?.let { currentActionMode ->
                        if (hasAnyCheckedConversations) {
                            currentActionMode.invalidate()
                        } else {
                            currentActionMode.finish()
                        }
                    }
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            viewModel.uncheckAll()
            actionMode = null
        }
    }

    override fun enableOnBackPressedCallback(): Boolean {
        return true
    }

    override fun handleOnBackPressed() {
        actionMode?.let(ActionMode::finish) ?: run { finish() }
    }

    private fun showConversation(conversationModel: ConversationModel) {
        val intent = IntentDataUtil.getShowConversationIntent(conversationModel, this) ?: return
        startActivityForResult(intent, ACTIVITY_ID_COMPOSE_MESSAGE)
    }

    private fun unarchive(checkedItems: List<ConversationModel>) {
        conversationService.unarchive(checkedItems, TriggerSource.LOCAL)
        viewModel.refresh()
        actionMode?.let(ActionMode::finish)
    }

    @SuppressLint("StringFormatInvalid")
    private fun delete(checkedItems: List<ConversationModel>) {
        val num = checkedItems.size

        var title = resources.getString(if (num > 1) R.string.really_delete_multiple_threads else R.string.really_delete_thread)
        var message = (
            ConfigUtils.getSafeQuantityString(this, R.plurals.really_delete_thread_message, num, num) + " " +
                getString(R.string.messages_cannot_be_recovered)
            )

        val conversationModel = checkedItems[0]
        if (num == 1 && conversationModel.isGroupConversation) {
            // If only one conversation is deleted, and it's a group, show a more specific message.
            val groupModel = conversationModel.group
            if (groupModel != null && groupService.isGroupMember(groupModel)) {
                title = resources.getString((R.string.action_delete_group))
                message = if (groupService.isGroupCreator(groupModel)) {
                    getString(R.string.delete_my_group_message)
                } else {
                    getString(R.string.delete_group_message)
                }
            }
        } else if (checkedItems.any(ConversationModel::isGroupConversation)) {
            // If multiple conversations are deleted and at least one of them is a group,
            // show a hint about the leave/dissolve behavior.
            message += " " + getString(R.string.groups_left_or_dissolved)
        }

        val dialog = GenericAlertDialog.newInstance(
            title,
            message,
            R.string.ok,
            R.string.cancel,
        )
        dialog.setData(checkedItems)
        dialog.show(supportFragmentManager, DIALOG_TAG_REALLY_DELETE_CHATS)
    }

    override fun onYes(tag: String, data: Any) {
        @Suppress("UNCHECKED_CAST")
        reallyDelete(data as List<ConversationModel>)
    }

    private fun reallyDelete(checkedItems: List<ConversationModel>) {
        val receivers: Array<MessageReceiver<*>> = checkedItems
            .map(ConversationModel::messageReceiver)
            .toTypedArray()
        EmptyOrDeleteConversationsAsyncTask(
            EmptyOrDeleteConversationsAsyncTask.Mode.DELETE,
            receivers,
            conversationService,
            distributionListService,
            groupModelRepository!!,
            groupFlowDispatcher!!,
            myIdentity,
            supportFragmentManager,
            findViewById(R.id.parent_layout),
        ) {
            actionMode?.finish()
            viewModel.refresh()
        }.execute()
    }

    private val conversationListener: ConversationListener = object : ConversationListener {
        override fun onNew(newConversationModel: ConversationModel) {
            viewModel.conversationListenerOnNew(newConversationModel)
        }

        override fun onModified(modifiedConversationModel: ConversationModel, oldPosition: Int?) {
            viewModel.conversationListenerOnModified(modifiedConversationModel)
        }

        override fun onRemoved(removedConversationModel: ConversationModel) {
            viewModel.conversationListenerOnRemoved(removedConversationModel)
        }

        override fun onModifiedAll() {
            viewModel.conversationListenerOnModifiedAll()
        }
    }

    private val messageListener: MessageListener = object : MessageListener {
        override fun onNew(newMessage: AbstractMessageModel) {
            viewModel.messageListenerOnNew(newMessage)
        }

        override fun onModified(modifiedMessageModel: List<AbstractMessageModel>) {
        }

        override fun onRemoved(removedMessageModel: AbstractMessageModel) {
        }

        override fun onRemoved(removedMessageModels: List<AbstractMessageModel>) {
        }

        override fun onProgressChanged(messageModel: AbstractMessageModel, newProgress: Int) {
        }

        override fun onResendDismissed(messageModel: AbstractMessageModel) {
        }
    }

    @Composable
    private fun ArchiveActivityContent(
        contentPadding: PaddingValues,
        conversationUiModels: List<ConversationUiModel>,
        onClickConversation: (ConversationModel) -> Unit,
        onLongClickConversation: (ConversationModel) -> Unit,
    ) {
        if (conversationUiModels.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = dimensionResource(R.dimen.tablet_additional_padding_horizontal),
                    ),
                contentPadding = contentPadding,
            ) {
                items(
                    count = conversationUiModels.size,
                    key = { index ->
                        conversationUiModels[index].conversation.uid
                    },
                ) { index ->
                    ConversationListItem(
                        modifier = Modifier.animateItem(),
                        conversationModel = conversationUiModels[index].conversation,
                        contactService = contactService,
                        groupService = groupService,
                        distributionListService = distributionListService,
                        conversationCategoryService = conversationCategoryService,
                        avatarCacheService = avatarCacheService,
                        ringtoneService = ringtoneService,
                        preferenceService = preferenceService,
                        isChecked = conversationUiModels[index].isChecked,
                        onClick = onClickConversation,
                        onLongClick = onLongClickConversation,
                        onClickJoinCall = {},
                    )
                }
                item {
                    SpacerVertical(GridUnit.x5)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpacerVertical(GridUnit.x2)
                Icon(
                    modifier = Modifier.size(GridUnit.x7),
                    painter = painterResource(R.drawable.ic_archive_outline),
                    contentDescription = null,
                    tint = LocalContentColor.current,
                )
                SpacerVertical(GridUnit.x2)
                Text(
                    modifier = Modifier
                        .padding(horizontal = GridUnit.x5),
                    text = stringResource(R.string.no_archived_chats),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                SpacerVertical(GridUnit.x2)
            }
        }
    }

    companion object {
        private const val DIALOG_TAG_REALLY_DELETE_CHATS = "delc"
    }
}
