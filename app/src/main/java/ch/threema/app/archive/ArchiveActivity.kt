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

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ch.threema.android.ResolvableString
import ch.threema.android.context
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.conversation.ConversationListItem
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.compose.conversation.models.ConversationNameStyle
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.compose.conversation.models.IconInfo
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.services.ContactService
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.ThreemaSearchView
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.app.voip.groupcall.LocalGroupId
import ch.threema.common.consume
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.GroupModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import com.google.android.material.appbar.MaterialToolbar
import java.util.Date
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class ArchiveActivity : ThreemaToolbarActivity(), DialogClickListener, SearchView.OnQueryTextListener {
    private val contactService: ContactService by inject()
    private val preferenceService: PreferenceService by inject()
    private val viewModel: ArchiveViewModel by viewModel()

    private var actionMode: ActionMode? = null

    override fun getLayoutResource(): Int = R.layout.activity_archive

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setObservers()
    }

    private fun setObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { archiveScreenEvent ->
                        when (archiveScreenEvent) {
                            ArchiveScreenEvent.ConversationsUnarchived -> actionMode?.finish()
                            ArchiveScreenEvent.ConversationsDeleted -> actionMode?.finish()
                            is ArchiveScreenEvent.OpenDistributionListConversation -> openComposeMessageActivity {
                                putExtra(AppConstants.INTENT_DATA_DISTRIBUTION_LIST_ID, archiveScreenEvent.distributionListId)
                            }

                            is ArchiveScreenEvent.OpenGroupConversation -> openComposeMessageActivity {
                                putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, archiveScreenEvent.groupDbId.toLong())
                            }

                            is ArchiveScreenEvent.OpenOneToOneConversation -> openComposeMessageActivity {
                                putExtra(AppConstants.INTENT_DATA_CONTACT, archiveScreenEvent.identity)
                            }

                            is ArchiveScreenEvent.ShowReallyDeleteConversationsDialog -> showReallyDeleteSelectedConversationsDialog(
                                archiveScreenEvent.content,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openComposeMessageActivity(applyExtras: Intent.() -> Unit) {
        val intent = Intent(context, ComposeMessageActivity::class.java)
            .apply {
                applyExtras()
            }
        startActivityForResult(intent, ACTIVITY_ID_COMPOSE_MESSAGE)
    }

    private fun showReallyDeleteSelectedConversationsDialog(content: ReallyDeleteConversationsDialogContent) {
        val dialog: GenericAlertDialog = GenericAlertDialog.newInstance(
            content.title,
            content.message,
            R.string.ok,
            R.string.cancel,
        )
        dialog.show(supportFragmentManager, DIALOG_TAG_REALLY_DELETE_CHATS)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ConfigUtils.adjustToolbar(this, toolbar)
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        appBarLayout?.applyDeviceInsetsAsPadding(
            insetSides = InsetSides.ltr(),
        )

        val toolbar = findViewById<MaterialToolbar>(R.id.material_toolbar)
        toolbar.setNavigationOnClickListener { _ -> finish() }
        toolbar.setTitle(R.string.archived_chats)

        val searchQuery = intent.getStringExtra(AppConstants.INTENT_DATA_ARCHIVE_FILTER)

        val filterMenu = toolbar.menu.findItem(R.id.menu_filter_archive)
        val searchView = filterMenu.actionView as ThreemaSearchView?
        if (searchView != null) {
            searchView.maxWidth = Integer.MAX_VALUE
            searchView.queryHint = getString(R.string.hint_filter_list)
            if (!searchQuery.isNullOrEmpty()) {
                filterMenu.expandActionView()
                searchView.setQuery(searchQuery, false)
            }
            searchView.post { searchView.setOnQueryTextListener(this@ArchiveActivity) }
        } else {
            filterMenu.isVisible = false
        }

        findViewById<ComposeView>(R.id.conversation_list).setContent {
            val conversationListItemUiModels: List<ConversationListItemUiModel>
                by viewModel.conversationListItemUiModels.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                if (!searchQuery.isNullOrBlank()) {
                    viewModel.setFilterQuery(searchQuery)
                }
            }

            LaunchedEffect(conversationListItemUiModels) {
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
                        conversationListItemUiModels = conversationListItemUiModels,
                        onClickConversation = { conversationUiModel ->
                            actionMode?.let { currentActionMode ->
                                val hasAnyCheckedConversations = viewModel.toggleSelected(
                                    conversationUID = conversationUiModel.conversationUID,
                                )
                                if (hasAnyCheckedConversations) {
                                    currentActionMode.invalidate()
                                } else {
                                    currentActionMode.finish()
                                }
                            } ?: run {
                                viewModel.onClickedConversation(conversationUiModel.conversationUID)
                            }
                        },
                        onLongClickConversation = { conversationUiModel ->
                            val hasAnyCheckedConversations: Boolean = viewModel.toggleSelected(
                                conversationUID = conversationUiModel.conversationUID,
                            )
                            if (actionMode == null && hasAnyCheckedConversations) {
                                actionMode = startSupportActionMode(ArchiveActionCallback())
                            }
                        },
                        onClickJoinOrOpenGroupCall = { localGroupId ->
                            startActivity(GroupCallActivity.createJoinCallIntent(this, localGroupId.id))
                        },
                        identityNameProvider = { identity ->
                            viewModel.getNameByIdentityOrNull(identity)
                        },
                        ownIdentity = remember {
                            contactService.me.identity
                        },
                        emojiStyle = preferenceService.emojiStyle,
                    )
                }
            }
        }

        return true
    }

    override fun onQueryTextSubmit(query: String) = false

    override fun onQueryTextChange(newText: String) = consume {
        viewModel.setFilterQuery(newText)
    }

    inner class ArchiveActionCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu) = consume {
            mode.menuInflater.inflate(R.menu.action_archive, menu)
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val selectedCount = viewModel.selectedCount
            if (selectedCount > 0) {
                mode.title = selectedCount.toString()
                return true
            }
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
            when (item.itemId) {
                R.id.menu_delete -> consume {
                    viewModel.onClickDeleteAllSelected()
                }

                R.id.menu_unarchive -> consume {
                    viewModel.unarchiveAllSelected()
                }

                R.id.menu_select_all -> consume {
                    val hasAnyCheckedConversations = viewModel.selectAll()
                    actionMode?.let { currentActionMode ->
                        if (hasAnyCheckedConversations) {
                            currentActionMode.invalidate()
                        } else {
                            currentActionMode.finish()
                        }
                    }
                }

                else -> false
            }

        override fun onDestroyActionMode(mode: ActionMode) {
            viewModel.deselectAll()
            actionMode = null
        }
    }

    override fun enableOnBackPressedCallback() = true

    override fun handleOnBackPressed() {
        actionMode?.let(ActionMode::finish) ?: run { finish() }
    }

    override fun onYes(tag: String, data: Any?) {
        if (tag == DIALOG_TAG_REALLY_DELETE_CHATS) {
            viewModel.confirmDeleteCurrentlySelected(
                supportFragmentManager = supportFragmentManager,
                snackbarFeedbackView = findViewById(R.id.parent_layout),
            )
        }
    }

    private companion object {
        const val DIALOG_TAG_REALLY_DELETE_CHATS = "delc"
    }
}

@Composable
private fun ArchiveActivityContent(
    contentPadding: PaddingValues,
    conversationListItemUiModels: List<ConversationListItemUiModel>,
    onClickConversation: (ConversationUiModel) -> Unit,
    onLongClickConversation: (ConversationUiModel) -> Unit,
    onClickJoinOrOpenGroupCall: (LocalGroupId) -> Unit,
    identityNameProvider: (Identity) -> ResolvableString?,
    ownIdentity: Identity,
    @EmojiStyle emojiStyle: Int,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (conversationListItemUiModels.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = dimensionResource(R.dimen.tablet_additional_padding_horizontal),
                    ),
                contentPadding = contentPadding,
            ) {
                item(
                    key = "top-spacer",
                    contentType = "top-spacer",
                ) {
                    SpacerVertical(GridUnit.x0_5)
                }
                items(
                    items = conversationListItemUiModels,
                    key = { conversationListItemUiModel ->
                        conversationListItemUiModel.model.conversationUID
                    },
                    contentType = { "conversation" },
                ) { conversationListItemUiModel ->
                    ConversationListItem(
                        modifier = Modifier.animateItem(),
                        conversationListItemUiModel = conversationListItemUiModel,
                        identityNameProvider = identityNameProvider,
                        ownIdentity = ownIdentity,
                        emojiStyle = emojiStyle,
                        onClick = onClickConversation,
                        onLongClick = onLongClickConversation,
                        onClickJoinOrOpenGroupCall = onClickJoinOrOpenGroupCall,
                    )
                }
                item(
                    key = "bottom-spacer",
                    contentType = "bottom-spacer",
                ) {
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
}

@Preview
@Composable
private fun ArchiveActivityContent_Preview() {
    ThreemaThemePreview {
        Scaffold { contentPadding ->
            ArchiveActivityContent(
                contentPadding = contentPadding,
                conversationListItemUiModels = listOf(
                    ConversationListItemUiModel(
                        model =
                        ConversationUiModel.ContactConversation(
                            conversationUID = UUID.randomUUID().toString(),
                            latestMessage = MessageModel().apply {
                                type = MessageType.TEXT
                                body = "How are you?"
                                createdAt = Date()
                            },
                            receiverModel = ContactModel.create("11111111", Random.nextBytes(32)),
                            receiverDisplayName = "Contact Name",
                            conversationName = "Conversation Name",
                            conversationNameStyle = ConversationNameStyle(strikethrough = false, dimAlpha = false),
                            draft = null,
                            latestMessageStateIcon = IconInfo(
                                icon = R.drawable.ic_visibility_filled,
                                contentDescription = null,
                            ),
                            unreadState = null,
                            isPinned = false,
                            isPrivate = false,
                            muteStatusIcon = null,
                            showWorkBadge = true,
                            isTyping = false,
                        ),
                        isChecked = false,
                    ),
                    ConversationListItemUiModel(
                        model =
                        ConversationUiModel.GroupConversation(
                            conversationUID = UUID.randomUUID().toString(),
                            latestMessage = MessageModel().apply {
                                type = MessageType.TEXT
                                body = "How are you?"
                                createdAt = Date()
                            },
                            receiverModel = GroupModel().apply {
                                setName("Group Name")
                            },
                            receiverDisplayName = "Contact Name",
                            conversationName = "Group Conversation Name",
                            conversationNameStyle = ConversationNameStyle(strikethrough = false, dimAlpha = false),
                            draft = null,
                            latestMessageStateIcon = IconInfo(
                                icon = R.drawable.ic_visibility_filled,
                                contentDescription = null,
                            ),
                            unreadState = null,
                            isPinned = false,
                            isPrivate = false,
                            muteStatusIcon = null,
                            latestMessageSenderName = null,
                            groupCall = null,
                        ),
                        isChecked = false,
                    ),
                    ConversationListItemUiModel(
                        model =
                        ConversationUiModel.DistributionListConversation(
                            conversationUID = UUID.randomUUID().toString(),
                            latestMessage = MessageModel().apply {
                                type = MessageType.TEXT
                                body = "How are you?"
                                createdAt = Date()
                            },
                            receiverModel = DistributionListModel().apply {
                                setName("Distribution List Name")
                            },
                            receiverDisplayName = "Contact Name",
                            conversationName = "Distribution List Conversation Name",
                            conversationNameStyle = ConversationNameStyle(strikethrough = false, dimAlpha = false),
                            draft = null,
                            latestMessageStateIcon = IconInfo(
                                icon = R.drawable.ic_visibility_filled,
                                contentDescription = null,
                            ),
                            unreadState = null,
                            isPinned = false,
                            isPrivate = false,
                            muteStatusIcon = null,
                        ),
                        isChecked = false,
                    ),
                ),
                onClickConversation = {},
                onLongClickConversation = {},
                onClickJoinOrOpenGroupCall = {},
                identityNameProvider = { null },
                ownIdentity = "00000000",
                emojiStyle = PreferenceService.EmojiStyle_ANDROID,
            )
        }
    }
}

@Preview
@Composable
private fun ArchiveActivityContent_Preview_Empty() {
    ThreemaThemePreview {
        Scaffold { contentPadding ->
            ArchiveActivityContent(
                contentPadding = contentPadding,
                conversationListItemUiModels = emptyList(),
                onClickConversation = {},
                onLongClickConversation = {},
                onClickJoinOrOpenGroupCall = {},
                identityNameProvider = { null },
                ownIdentity = "00000000",
                emojiStyle = PreferenceService.EmojiStyle_ANDROID,
            )
        }
    }
}
