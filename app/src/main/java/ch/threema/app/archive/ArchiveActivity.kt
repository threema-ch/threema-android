package ch.threema.app.archive

import android.content.Context
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
import ch.threema.android.buildActivityIntent
import ch.threema.android.context
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.activities.ComposeMessageActivity
import ch.threema.app.activities.DistributionListAddActivity
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.compose.common.LocalDayOfYear
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.rememberRefreshingLocalDayOfYear
import ch.threema.app.compose.conversation.ConversationListItem
import ch.threema.app.compose.conversation.models.ConversationListItemUiModel
import ch.threema.app.compose.conversation.models.ConversationNameStyle
import ch.threema.app.compose.conversation.models.ConversationUiModel
import ch.threema.app.compose.conversation.models.IconInfo
import ch.threema.app.compose.preview.PreviewData
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.ThreemaThemePreview
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.contactdetails.ContactDetailActivity
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.GenericAlertDialog.DialogClickListener
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.PreferenceService.EmojiStyle
import ch.threema.app.services.GroupService
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.ThreemaSearchView
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.usecases.conversations.AvatarIteration
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.voip.activities.GroupCallActivity
import ch.threema.common.consume
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.domain.models.ContactReceiverIdentifier
import ch.threema.domain.models.DistributionListReceiverIdentifier
import ch.threema.domain.models.GroupReceiverIdentifier
import ch.threema.domain.models.ReceiverIdentifier
import ch.threema.domain.types.Identity
import com.google.android.material.appbar.MaterialToolbar
import java.util.UUID
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class ArchiveActivity : ThreemaToolbarActivity(), DialogClickListener, SearchView.OnQueryTextListener {
    private val preferenceService: PreferenceService by inject()
    private val viewModel: ArchiveViewModel by viewModel()
    private val groupService: GroupService by inject()
    private val identityProvider: IdentityProvider by inject()

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
                                putExtra(AppConstants.INTENT_DATA_GROUP_DATABASE_ID, archiveScreenEvent.groupDatabaseId.toLong())
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

            val contactNameFormat: ContactNameFormat by viewModel.contactNameFormat.collectAsStateWithLifecycle()

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
                ) { insetsPadding ->

                    ArchiveActivityContent(
                        insetsPadding = insetsPadding,
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
                        onClickAvatar = ::onAvatarClicked,
                        onLongClickConversation = { conversationUiModel ->
                            val hasAnyCheckedConversations: Boolean = viewModel.toggleSelected(
                                conversationUID = conversationUiModel.conversationUID,
                            )
                            if (actionMode == null && hasAnyCheckedConversations) {
                                actionMode = startSupportActionMode(ArchiveActionCallback())
                            }
                        },
                        onClickJoinOrOpenGroupCall = { groupReceiverIdentifier ->
                            startActivity(
                                GroupCallActivity.createJoinCallIntent(
                                    this,
                                    groupReceiverIdentifier.groupDatabaseId.toInt(),
                                ),
                            )
                        },
                        avatarBitmapProvider = viewModel::provideAvatarBitmap,
                        ownIdentity = remember {
                            identityProvider.getIdentity()!!
                        },
                        emojiStyle = preferenceService.getEmojiStyle(),
                        contactNameFormat = contactNameFormat,
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

    override fun onYes(tag: String?, data: Any?) {
        if (tag == DIALOG_TAG_REALLY_DELETE_CHATS) {
            viewModel.confirmDeleteCurrentlySelected(
                supportFragmentManager = supportFragmentManager,
                snackbarFeedbackView = findViewById(R.id.parent_layout),
            )
        }
    }

    private fun onAvatarClicked(conversationUiModel: ConversationUiModel) {
        val intent = when (val receiverIdentifier = conversationUiModel.receiverIdentifier) {
            is ContactReceiverIdentifier -> {
                ContactDetailActivity.createIntent(this, receiverIdentifier.identity)
            }

            is GroupReceiverIdentifier -> {
                groupService.getGroupDetailIntent(receiverIdentifier.groupDatabaseId, this)
            }

            is DistributionListReceiverIdentifier -> {
                DistributionListAddActivity.createIntent(this, receiverIdentifier.id)
            }
        }
        startActivity(intent)
    }

    companion object {
        private const val DIALOG_TAG_REALLY_DELETE_CHATS = "delc"

        fun createIntent(context: Context, filterQuery: String? = null) = buildActivityIntent<ArchiveActivity>(context) {
            putExtra(AppConstants.INTENT_DATA_ARCHIVE_FILTER, filterQuery)
        }
    }
}

@Composable
private fun ArchiveActivityContent(
    insetsPadding: PaddingValues,
    conversationListItemUiModels: List<ConversationListItemUiModel>,
    onClickConversation: (ConversationUiModel) -> Unit,
    onLongClickConversation: (ConversationUiModel) -> Unit,
    onClickAvatar: (ConversationUiModel) -> Unit,
    onClickJoinOrOpenGroupCall: (GroupReceiverIdentifier) -> Unit,
    avatarBitmapProvider: suspend (ReceiverIdentifier) -> ImmutableBitmap?,
    ownIdentity: Identity,
    @EmojiStyle emojiStyle: Int,
    contactNameFormat: ContactNameFormat,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (conversationListItemUiModels.isNotEmpty()) {
            val localDayOfYear: LocalDayOfYear by rememberRefreshingLocalDayOfYear()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = dimensionResource(R.dimen.tablet_additional_padding_horizontal),
                    ),
                contentPadding = insetsPadding,
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
                        avatarIteration = conversationListItemUiModel.model.avatarIteration,
                        localDayOfYear = localDayOfYear,
                        avatarBitmapProvider = avatarBitmapProvider,
                        ownIdentity = ownIdentity,
                        emojiStyle = emojiStyle,
                        contactNameFormat = contactNameFormat,
                        onClick = onClickConversation,
                        onLongClick = onLongClickConversation,
                        onClickAvatar = onClickAvatar,
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
                    .padding(insetsPadding),
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
                insetsPadding = contentPadding,
                conversationListItemUiModels = listOf(
                    ConversationListItemUiModel(
                        model = ConversationUiModel.ContactConversation(
                            conversationUID = UUID.randomUUID().toString(),
                            latestMessageData = PreviewData.LatestMessageData.incomingTextMessage(
                                body = "How are you?",
                            ),
                            receiverIdentifier = ContactReceiverIdentifier(
                                identity = PreviewData.IDENTITY_OTHER_1.value,
                            ),
                            receiverDisplayName = "Contact Name",
                            conversationName = "Conversation Name",
                            conversationNameStyle = ConversationNameStyle(strikethrough = false, dimAlpha = false),
                            draftData = null,
                            unreadState = null,
                            isPinned = false,
                            isPrivate = false,
                            icon = IconInfo(
                                res = R.drawable.ic_reply_filled,
                                contentDescription = null,
                            ),
                            muteStatusIcon = null,
                            showWorkBadge = true,
                            isTyping = false,
                            avatarIteration = AvatarIteration.initial,
                            availabilityStatus = AvailabilityStatus.Unavailable(),
                        ),
                        isChecked = false,
                        isHighlighted = false,
                    ),
                    ConversationListItemUiModel(
                        model = ConversationUiModel.GroupConversation(
                            conversationUID = UUID.randomUUID().toString(),
                            latestMessageData = PreviewData.LatestMessageData.incomingTextMessage(
                                body = "How are you?",
                            ),
                            receiverIdentifier = GroupReceiverIdentifier(
                                groupDatabaseId = 1L,
                                groupCreatorIdentity = PreviewData.IDENTITY_OTHER_1.value,
                                groupApiId = 1L,
                            ),
                            receiverDisplayName = "Contact Name",
                            conversationName = "Group Conversation Name",
                            conversationNameStyle = ConversationNameStyle(strikethrough = false, dimAlpha = false),
                            draftData = null,
                            unreadState = null,
                            isPinned = false,
                            isPrivate = false,
                            icon = IconInfo(
                                res = R.drawable.ic_group_filled,
                                contentDescription = null,
                            ),
                            muteStatusIcon = null,
                            latestMessageSenderName = null,
                            groupCall = null,
                            avatarIteration = AvatarIteration.initial,
                        ),
                        isChecked = false,
                        isHighlighted = false,
                    ),
                    ConversationListItemUiModel(
                        model = ConversationUiModel.DistributionListConversation(
                            conversationUID = UUID.randomUUID().toString(),
                            latestMessageData = PreviewData.LatestMessageData.incomingTextMessage(
                                body = "How are you?",
                            ),
                            receiverIdentifier = DistributionListReceiverIdentifier(
                                id = 1L,
                            ),
                            receiverDisplayName = "Contact Name",
                            conversationName = "Distribution List Conversation Name",
                            conversationNameStyle = ConversationNameStyle(strikethrough = false, dimAlpha = false),
                            draftData = null,
                            unreadState = null,
                            isPinned = false,
                            isPrivate = false,
                            icon = IconInfo(
                                res = R.drawable.ic_distribution_list_filled,
                                contentDescription = null,
                            ),
                            muteStatusIcon = null,
                            avatarIteration = AvatarIteration.initial,
                        ),
                        isChecked = false,
                        isHighlighted = false,
                    ),
                ),
                onClickConversation = {},
                onClickAvatar = {},
                onLongClickConversation = {},
                onClickJoinOrOpenGroupCall = {},
                avatarBitmapProvider = { null },
                ownIdentity = PreviewData.IDENTITY_ME,
                emojiStyle = PreferenceService.EMOJI_STYLE_ANDROID,
                contactNameFormat = ContactNameFormat.DEFAULT,
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
                insetsPadding = contentPadding,
                conversationListItemUiModels = emptyList(),
                onClickConversation = {},
                onLongClickConversation = {},
                onClickAvatar = {},
                onClickJoinOrOpenGroupCall = {},
                avatarBitmapProvider = { null },
                ownIdentity = PreviewData.IDENTITY_ME,
                emojiStyle = PreferenceService.EMOJI_STYLE_ANDROID,
                contactNameFormat = ContactNameFormat.DEFAULT,
            )
        }
    }
}
