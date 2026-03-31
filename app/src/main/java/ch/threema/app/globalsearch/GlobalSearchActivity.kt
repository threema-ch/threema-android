package ch.threema.app.globalsearch

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import ch.threema.android.buildActivityIntent
import ch.threema.app.R
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.fragments.composemessage.ComposeMessageFragment.EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService.MessageFilterFlags
import ch.threema.app.services.MessageServiceImpl.FILTER_CHATS
import ch.threema.app.services.MessageServiceImpl.FILTER_GROUPS
import ch.threema.app.services.MessageServiceImpl.FILTER_INCLUDE_ARCHIVED
import ch.threema.app.services.UserService
import ch.threema.app.services.ballot.BallotService
import ch.threema.app.ui.EmptyRecyclerView
import ch.threema.app.ui.EmptyView
import ch.threema.app.ui.InsetSides
import ch.threema.app.ui.SpacingValues
import ch.threema.app.ui.ThreemaSearchView
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.GroupUtil
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.group.GroupMessageModel
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.search.SearchBar
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

private val logger = getThreemaLogger("GlobalSearchActivity")

class GlobalSearchActivity : ThreemaToolbarActivity(), SearchView.OnQueryTextListener {
    init {
        logScreenVisibility(logger)
    }

    private val contactService: ContactService by inject()
    private val userService: UserService by inject()
    private val groupService: GroupService by inject()
    private val conversationCategoryService: ConversationCategoryService by inject()
    private val ballotService: BallotService by inject()
    private val preferenceService: PreferenceService by inject()
    private val globalSearchViewModel: GlobalSearchViewModel by viewModel()

    private var chatsAdapter: GlobalSearchAdapter? = null
    private var searchView: ThreemaSearchView? = null
    private var searchBar: SearchBar? = null

    @MessageFilterFlags
    private var filterFlags = FILTER_CHATS or FILTER_GROUPS or FILTER_INCLUDE_ARCHIVED
    private var queryText: String? = null
    private val queryHandler = Handler(Looper.getMainLooper())
    private val queryTask = Runnable {
        globalSearchViewModel.onQueryChanged(
            query = queryText,
            filterFlags = filterFlags,
        )
        chatsAdapter?.onQueryChanged(queryText)
    }
    private val showMessageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _: ActivityResult ->
            // search results may have changed when returning from ComposeMessageFragment
            globalSearchViewModel.onDataChanged()
        }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        queryText = newText
        if (chatsAdapter != null) {
            queryHandler.removeCallbacksAndMessages(null)
            if ((queryText?.length ?: 0) >= QUERY_MIN_LENGTH) {
                queryHandler.postDelayed(queryTask, GLOBAL_SEARCH_QUERY_TIMEOUT_MS)
            } else {
                globalSearchViewModel.onQueryChanged(
                    query = null,
                    filterFlags = filterFlags,
                )
                chatsAdapter?.onQueryChanged(null)
            }
        }
        return true
    }

    override fun getLayoutResource(): Int = R.layout.activity_global_search

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()

        findViewById<EmptyRecyclerView>(R.id.recycler_chats).applyDeviceInsetsAsPadding(
            insetSides = InsetSides.lbr(),
        )
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        if (supportActionBar != null) {
            searchBar = toolbar as SearchBar
            searchBar?.let { bar ->
                bar.setNavigationOnClickListener {
                    searchView?.let {
                        if (it.isIconified) {
                            finish()
                        } else {
                            it.isIconified = true
                        }
                    }
                }
                bar.setOnClickListener { searchView?.isIconified = false }
            }
        }

        chatsAdapter = GlobalSearchAdapter(
            this,
            Glide.with(this),
            SNIPPET_THRESHOLD,
            groupService,
            contactService,
            userService,
            ballotService,
            conversationCategoryService,
            preferenceService,
        )
        chatsAdapter?.setOnClickItemListener { messageModel: AbstractMessageModel, _, _ ->
            logger.info("Message clicked")
            showMessage(messageModel)
        }

        setupChip(R.id.chats, FILTER_CHATS)
        setupChip(R.id.groups, FILTER_GROUPS)
        setupChip(R.id.archived, FILTER_INCLUDE_ARCHIVED)

        val recyclerView = this.findViewById<EmptyRecyclerView>(R.id.recycler_chats)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = DefaultItemAnimator()
        val emptyView = EmptyView(this, ConfigUtils.getActionBarSize(this))
        emptyView.setup(
            R.string.global_search_empty_view_text,
            R.drawable.ic_search_outline,
        )
        (recyclerView.parent.parent as ViewGroup).addView(emptyView)
        recyclerView.emptyView = emptyView
        emptyView.setLoading(true)
        recyclerView.adapter = chatsAdapter

        emptyView.applyDeviceInsetsAsPadding(
            insetSides = InsetSides.lbr(),
            ownPadding = SpacingValues.symmetric(
                vertical = R.dimen.grid_unit_x1,
                horizontal = R.dimen.grid_unit_x3,
            ),
        )

        lifecycleScope.launch {
            globalSearchViewModel.messageModels.collect { messageModels ->
                val messageModelsWithoutPrivateChats = messageModels.filterNot { messageModel ->
                    val uniqueIdString: String =
                        if (messageModel is GroupMessageModel) {
                            GroupUtil.getUniqueIdString(messageModel.groupId.toLong())
                        } else {
                            ContactUtil.getUniqueIdString(messageModel.identity)
                        }
                    conversationCategoryService.isPrivateChat(uniqueIdString)
                }

                chatsAdapter?.setMessageModels(messageModelsWithoutPrivateChats)

                if (messageModelsWithoutPrivateChats.isEmpty()) {
                    if ((queryText?.length ?: 0) >= QUERY_MIN_LENGTH) {
                        emptyView.setup(R.string.search_no_matches)
                    } else {
                        emptyView.setup(R.string.global_search_empty_view_text)
                    }
                }
            }
        }

        lifecycleScope.launch {
            globalSearchViewModel.isLoading.collect { isLoading ->
                emptyView.setLoading(isLoading)
            }
        }

        onQueryTextChange(null)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_global_search, menu)
        val searchMenuItem = menu.findItem(R.id.menu_action_search)
        searchView = searchMenuItem.actionView as ThreemaSearchView?
        searchView?.let {
            if (ConfigUtils.isLandscape(this)) {
                it.maxWidth = Int.MAX_VALUE
            }

            ConfigUtils.adjustSearchViewPadding(it)
            it.queryHint = getString(R.string.global_search_empty_view_text)
            it.setOnQueryTextListener(this)
            it.setOnSearchClickListener {
                searchBar?.hint = ""
            }
            // Show the hint of the search bar again when the search view is closed
            it.setOnCloseListener {
                searchBar?.setHint(R.string.global_search)
                false
            }
        }
        if (searchView == null) {
            searchMenuItem.isVisible = false
        }
        return true
    }

    private fun setupChip(@IdRes id: Int, flag: Int) {
        // https://github.com/material-components/material-components-android/issues/1419
        val chip = findViewById<Chip>(id)
        chip.isChecked = true
        chip.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            filterFlags = if (isChecked) {
                filterFlags or flag
            } else {
                filterFlags and flag.inv()
            }
            globalSearchViewModel.onQueryChanged(
                query = queryText,
                filterFlags = filterFlags,
            )
        }
    }

    private fun showMessage(messageModel: AbstractMessageModel?) {
        if (messageModel == null) {
            return
        }
        hideKeyboard()
        val intent = IntentDataUtil.getJumpToMessageIntent(this, messageModel)
        intent.putExtra(EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR, true)
        showMessageLauncher.launch(intent)
    }

    private fun hideKeyboard() {
        searchView?.let {
            WindowCompat.getInsetsController(window, it).hide(WindowInsetsCompat.Type.ime())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        hideKeyboard()
        super.onConfigurationChanged(newConfig)
    }

    companion object {
        private const val QUERY_MIN_LENGTH = 2
        private const val GLOBAL_SEARCH_QUERY_TIMEOUT_MS = 500L
        private const val SNIPPET_THRESHOLD = 17

        fun createIntent(context: Context) = buildActivityIntent<GlobalSearchActivity>(context)
    }
}
