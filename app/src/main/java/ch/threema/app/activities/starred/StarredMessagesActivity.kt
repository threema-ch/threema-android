package ch.threema.app.activities.starred

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.activities.starred.models.StarredMessageListItemUiModel
import ch.threema.app.activities.starred.models.StarredMessagesViewState
import ch.threema.app.compose.common.SpacerVertical
import ch.threema.app.compose.common.ThemedText
import ch.threema.app.compose.common.immutables.ImmutableBitmap
import ch.threema.app.compose.common.immutables.toImmutableBitmap
import ch.threema.app.compose.common.rememberRefreshingLocalDayOfYear
import ch.threema.app.compose.theme.ThreemaTheme
import ch.threema.app.compose.theme.dimens.GridUnit
import ch.threema.app.compose.theme.dimens.responsive
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.SelectorDialog
import ch.threema.app.fragments.composemessage.ComposeMessageFragment.EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR
import ch.threema.app.framework.EventHandler
import ch.threema.app.framework.WithViewState
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.stores.IdentityProvider
import ch.threema.app.ui.SelectorDialogItem
import ch.threema.app.ui.ThreemaSearchView
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.consume
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.domain.types.Identity
import ch.threema.storage.models.AbstractMessageModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions
import com.google.android.material.search.SearchBar
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

private val logger = getThreemaLogger("StarredMessagesActivity")

class StarredMessagesActivity :
    ThreemaToolbarActivity(),
    SearchView.OnQueryTextListener,
    SelectorDialog.SelectorDialogClickListener,
    GenericAlertDialog.DialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val viewModel: StarredMessagesViewModel by viewModel()
    private val identityProvider: IdentityProvider by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private var searchView: ThreemaSearchView? = null
    private var searchBar: SearchBar? = null
    private var sortMenuItem: MenuItem? = null
    private var removeStarsMenuItem: MenuItem? = null
    private var actionMode: ActionMode? = null

    private val showMessageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _: ActivityResult ->
        // starred status may have changed when returning from ComposeMessageFragment
        viewModel.loadStarredMessages()
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean = consume {
        viewModel.onQueryChanged(newText)
    }

    override fun getLayoutResource() = R.layout.activity_starred_messages

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        if (supportActionBar != null) {
            initSearchBar()
        }

        val ownIdentity = identityProvider.getIdentity() ?: return false

        findViewById<ComposeView>(R.id.starred_messages_content).setContent {
            EventHandler(viewModel, ::handleScreenEvent)

            WithViewState(viewModel) { viewState ->

                LaunchedEffect(viewState?.listItems?.size) {
                    removeStarsMenuItem?.isVisible = viewState?.listItems?.isNotEmpty() == true && (searchView?.isIconified ?: false)
                }

                if (viewState != null) {
                    StarredMessageScreenContent(
                        viewState = viewState,
                        ownIdentity = ownIdentity,
                    )
                }
            }
        }

        if (viewModel.selectedMessageItemsCount > 0) {
            actionMode = startSupportActionMode(actionModeCallback)
        }

        return true
    }

    private fun initSearchBar() {
        searchBar = toolbar as SearchBar
        searchBar?.let { searchBar ->
            searchBar.setNavigationOnClickListener {
                finish()
            }
            searchBar.setOnClickListener {
                searchView?.apply {
                    isIconified = false
                    requestFocus()
                }
            }
        }
    }

    @Composable
    private fun StarredMessageScreenContent(
        viewState: StarredMessagesViewState,
        ownIdentity: Identity,
    ) {
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

                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (viewState.listItems.isNotEmpty()) {
                        MessageList(
                            modifier = Modifier.fillMaxSize(),
                            insetsPadding = insetsPadding,
                            listItems = viewState.listItems,
                            emojiStyle = viewState.emojiStyle,
                            contactNameFormat = viewState.contactNameFormat,
                            ownIdentity = ownIdentity,
                            query = viewState.query,
                        )
                    } else if (!viewState.isLoading) {
                        EmptyList(
                            modifier = Modifier.fillMaxSize(),
                            insetsPadding = insetsPadding,
                        )
                    }

                    AnimatedVisibility(
                        visible = viewState.isLoading,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        val layoutDirection = LocalLayoutDirection.current
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = insetsPadding.calculateStartPadding(layoutDirection),
                                    end = insetsPadding.calculateEndPadding(layoutDirection),
                                ),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MessageList(
        modifier: Modifier,
        insetsPadding: PaddingValues,
        listItems: List<StarredMessageListItemUiModel>,
        @PreferenceService.EmojiStyle emojiStyle: Int,
        contactNameFormat: ContactNameFormat,
        ownIdentity: Identity,
        query: String?,
    ) {
        val localDayOfYear by rememberRefreshingLocalDayOfYear()
        val layoutDirection = LocalLayoutDirection.current

        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(
                start = insetsPadding.calculateStartPadding(layoutDirection) + dimensionResource(R.dimen.tablet_additional_padding_horizontal),
                top = insetsPadding.calculateTopPadding() + GridUnit.x1,
                end = insetsPadding.calculateEndPadding(layoutDirection) + dimensionResource(R.dimen.tablet_additional_padding_horizontal),
                bottom = insetsPadding.calculateBottomPadding() + GridUnit.x5,
            ),
        ) {
            items(
                items = listItems,
                key = { starredMessageListItemUiModel ->
                    starredMessageListItemUiModel.starredMessageUiModel.uid
                },
                contentType = { "starred_message" },
            ) { starredMessageListItemUiModel ->
                StarredMessageListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = GridUnit.x1,
                        )
                        .animateItem(),
                    localDayOfYear = localDayOfYear,
                    avatarBitmapProvider = viewModel::provideAvatarBitmap,
                    messageMediaPreviewProvider = ::provideMediaPreviewBitmap,
                    ownIdentity = ownIdentity,
                    emojiStyle = emojiStyle,
                    contactNameFormat = contactNameFormat,
                    starredMessageUiModel = starredMessageListItemUiModel.starredMessageUiModel,
                    isSelected = starredMessageListItemUiModel.isSelected,
                    highlightedMessageContent = query,
                    onClickMessageBubble = ::onClickMessageBubble,
                    onLongClickMessageBubble = ::onLongClickMessageBubble,
                    onClickNavigateToMessage = ::showMessage,
                )
            }
        }
    }

    /**
     *  `Glide.with(Activity)` respects the activities lifecycle state.
     */
    private suspend fun provideMediaPreviewBitmap(abstractMessageModel: AbstractMessageModel): ImmutableBitmap? =
        withContext(dispatcherProvider.io) {
            runCatching {
                Glide
                    .with(this@StarredMessagesActivity)
                    .asBitmap()
                    .load(abstractMessageModel)
                    .transition(BitmapTransitionOptions.withCrossFade())
                    .submit()
                    .get()
                    ?.toImmutableBitmap()
            }.getOrElse { throwable ->
                logger.warn("File message preview bitmap could not be loaded", throwable)
                null
            }
        }

    private fun onClickMessageBubble(messageModel: AbstractMessageModel) {
        if (actionMode != null) {
            viewModel.toggleListItemSelected(
                messageId = messageModel.id,
                initiatedByLongClick = false,
            )
        } else {
            showMessage(messageModel)
        }
    }

    private fun onLongClickMessageBubble(messageModel: AbstractMessageModel) {
        actionMode?.finish()
        viewModel.toggleListItemSelected(
            messageId = messageModel.id,
            initiatedByLongClick = true,
        )
    }

    @Composable
    private fun EmptyList(
        modifier: Modifier = Modifier,
        insetsPadding: PaddingValues,
    ) {
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(
                    start = insetsPadding.calculateStartPadding(layoutDirection) + GridUnit.x2.responsive,
                    end = insetsPadding.calculateEndPadding(layoutDirection) + GridUnit.x2.responsive,
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpacerVertical(insetsPadding.calculateTopPadding())
            Icon(
                modifier = Modifier.size(156.dp),
                painter = painterResource(R.drawable.ic_star_filled),
                tint = colorResource(R.color.empty_view_icon_tint),
                contentDescription = null,
            )
            SpacerVertical(GridUnit.x2)
            ThemedText(
                text = stringResource(R.string.no_starred_messages),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current.copy(
                    alpha = 0.8f,
                ),
                textAlign = TextAlign.Center,
            )
            SpacerVertical(insetsPadding.calculateBottomPadding())
        }
    }

    private fun handleScreenEvent(event: StarredMessagesScreenEvent) {
        when (event) {
            StarredMessagesScreenEvent.SelectedStarsRemoved -> onScreenEventSelectedStarsRemoved()
            is StarredMessagesScreenEvent.ListItemSelectedToggled -> onScreenEventListItemSelectedToggled(event)
        }
    }

    private fun onScreenEventSelectedStarsRemoved() {
        actionMode?.finish()
    }

    private fun onScreenEventListItemSelectedToggled(event: StarredMessagesScreenEvent.ListItemSelectedToggled) {
        logger.info("Starred message selection toggled")
        if (event.initiatedByLongClick) {
            if (event.updatedSelectedListItemsCount > 0) {
                searchView?.clearFocus()
                hideKeyboard()
                actionMode = startSupportActionMode(actionModeCallback)
            }
        } else {
            if (event.updatedSelectedListItemsCount > 0) {
                actionMode?.invalidate()
            } else {
                actionMode?.finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.action_starred_messages_search, menu)
        val searchMenuItem = menu.findItem(R.id.menu_action_search)
        searchView = searchMenuItem.actionView as ThreemaSearchView?
        searchView?.let { searchView ->
            if (ConfigUtils.isLandscape(this)) {
                searchView.maxWidth = Int.MAX_VALUE
            }

            ConfigUtils.adjustSearchViewPadding(searchView)
            searchView.queryHint = getString(R.string.hint_filter_list)
            searchView.setOnQueryTextListener(this)
            searchView.setOnSearchClickListener {
                searchBar?.hint = ""
                sortMenuItem?.isVisible = false
                removeStarsMenuItem?.isVisible = false
            }
            // Show the hint of the search bar again when the search view is closed
            searchView.setOnCloseListener {
                searchBar?.setHint(R.string.starred_messages)
                sortMenuItem?.isVisible = true
                false
            }
        }
        if (searchView == null) {
            searchMenuItem.isVisible = false
        }
        sortMenuItem = menu.findItem(R.id.menu_action_sort)
        sortMenuItem?.setOnMenuItemClickListener {
            showSortingSelector()
            false
        }

        removeStarsMenuItem = menu.findItem(R.id.menu_remove_stars)
        removeStarsMenuItem?.setOnMenuItemClickListener {
            GenericAlertDialog.newInstance(
                R.string.remove_all_stars,
                R.string.really_remove_all_stars,
                R.string.yes,
                R.string.no,
            ).show(supportFragmentManager, "rem")
            false
        }
        return true
    }

    private fun showSortingSelector() {
        val selectorDialog = SelectorDialog.newInstance(
            getString(R.string.sort_by),
            arrayListOf(
                SelectorDialogItem(getString(R.string.newest_first), R.drawable.ic_arrow_downward),
                SelectorDialogItem(getString(R.string.oldest_first), R.drawable.ic_arrow_upward),
            ),
            getString(R.string.cancel),
        )
        try {
            selectorDialog.show(supportFragmentManager, DIALOG_TAG_SORT_BY)
        } catch (e: IllegalStateException) {
            logger.error("Exception", e)
        }
    }

    private fun showMessage(messageModel: AbstractMessageModel) {
        logger.info("Navigating to starred message")
        hideKeyboard()
        val intent = IntentDataUtil.getJumpToMessageIntent(this, messageModel)
        intent.putExtra(EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR, true)
        showMessageLauncher.launch(intent)
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.menuInflater?.inflate(R.menu.action_starred_messages, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu?): Boolean {
            val selectedMessageItemsCount: Int = viewModel.selectedMessageItemsCount
            if (selectedMessageItemsCount > 0) {
                mode.title = selectedMessageItemsCount.toString()
                return true
            }
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem?): Boolean {
            return if (R.id.menu_remove_star == item?.itemId) {
                viewModel.removeStarsFromSelectedMessages()
                true
            } else {
                false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            viewModel.unselectAllMessageItems()
            actionMode = null
        }
    }

    override fun onClick(tag: String, which: Int, data: Any?) {
        if (DIALOG_TAG_SORT_BY == tag) {
            logger.info("Sorting order for starred messages changed")
            viewModel.onSortOrderChanged(which)
        }
    }

    override fun onYes(tag: String?, data: Any?) {
        viewModel.removeAllStars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        hideKeyboard()
        super.onConfigurationChanged(newConfig)
    }

    private fun hideKeyboard() {
        searchView?.let {
            WindowCompat.getInsetsController(window, it).hide(WindowInsetsCompat.Type.ime())
        }
    }

    companion object {
        private const val DIALOG_TAG_SORT_BY = "sortBy"

        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<StarredMessagesActivity>(context)
    }
}
