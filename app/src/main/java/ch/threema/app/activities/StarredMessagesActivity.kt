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

package ch.threema.app.activities

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import ch.threema.app.R
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.SelectorDialog
import ch.threema.app.fragments.ComposeMessageFragment.EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR
import ch.threema.app.globalsearch.GlobalSearchAdapter
import ch.threema.app.globalsearch.GlobalSearchViewModel
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.MessageService
import ch.threema.app.services.MessageServiceImpl.FILTER_CHATS
import ch.threema.app.services.MessageServiceImpl.FILTER_GROUPS
import ch.threema.app.services.MessageServiceImpl.FILTER_INCLUDE_ARCHIVED
import ch.threema.app.services.MessageServiceImpl.FILTER_STARRED_ONLY
import ch.threema.app.services.PreferenceService
import ch.threema.app.ui.EmptyRecyclerView
import ch.threema.app.ui.EmptyView
import ch.threema.app.ui.SelectorDialogItem
import ch.threema.app.ui.ThreemaSearchView
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.data.DisplayTag.DISPLAY_TAG_NONE
import com.bumptech.glide.Glide
import com.google.android.material.search.SearchBar
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StarredMessagesActivity :
    ThreemaToolbarActivity(),
    SearchView.OnQueryTextListener,
    SelectorDialog.SelectorDialogClickListener,
    GenericAlertDialog.DialogClickListener {
    private val starredMessagesSearchQueryTimeout = 500.milliseconds
    private var chatsAdapter: GlobalSearchAdapter? = null
    private var globalSearchViewModel: GlobalSearchViewModel? = null
    private var searchView: ThreemaSearchView? = null
    private var searchBar: SearchBar? = null
    private var contactService: ContactService? = null
    private var messageService: MessageService? = null
    private var groupService: GroupService? = null
    private var sortMenuItem: MenuItem? = null
    private var removeStarsMenuItem: MenuItem? = null
    private var actionMode: ActionMode? = null
    private var sortOrder = PreferenceService.StarredMessagesSortOrder_DATE_DESCENDING
    private var queryText: String? = null
    private val queryHandler = Handler(Looper.getMainLooper())
    private val queryTask = Runnable {
        globalSearchViewModel?.onQueryChanged(
            queryText,
            FILTER_FLAGS,
            true,
            sortOrder == PreferenceService.StarredMessagesSortOrder_DATE_ASCENDING,
        )
        chatsAdapter?.onQueryChanged(queryText)
    }
    private val showMessageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _: ActivityResult ->
            // starred status may have changed when returning from ComposeMessageFragment
            globalSearchViewModel?.onDataChanged()
        }

    override fun onQueryTextSubmit(query: String): Boolean {
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        queryText = newText
        queryHandler.removeCallbacksAndMessages(null)
        if (queryText?.isNotEmpty() == true) {
            queryHandler.postDelayed(queryTask, starredMessagesSearchQueryTimeout.inWholeMilliseconds)
        } else {
            globalSearchViewModel?.onQueryChanged(
                null,
                FILTER_FLAGS,
                true,
                sortOrder == PreferenceService.StarredMessagesSortOrder_DATE_ASCENDING,
            )
            chatsAdapter?.onQueryChanged(null)
        }
        return true
    }

    override fun getLayoutResource(): Int {
        return R.layout.activity_starred_messages
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            contactService = serviceManager.contactService
            groupService = serviceManager.groupService
            messageService = serviceManager.messageService
        } catch (e: Exception) {
            logger.error("Exception", e)
            finish()
        }
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        sortOrder = preferenceService?.starredMessagesSortOrder
            ?: PreferenceService.StarredMessagesSortOrder_DATE_DESCENDING

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
                ConfigUtils.adjustSearchBarTextViewMargin(this, bar)
            }
        }
        chatsAdapter = GlobalSearchAdapter(
            this,
            Glide.with(this),
            R.layout.item_starred_messages,
            50,
        )
        chatsAdapter?.setOnClickItemListener(object : GlobalSearchAdapter.OnClickItemListener {
            override fun onClick(
                messageModel: AbstractMessageModel?,
                itemView: View,
                position: Int,
            ) {
                if (actionMode != null) {
                    chatsAdapter?.toggleChecked(position)
                    if ((chatsAdapter?.checkedItemsCount ?: 0) > 0) {
                        actionMode?.invalidate()
                    } else {
                        actionMode?.finish()
                    }
                } else {
                    showMessage(messageModel)
                }
            }

            override fun onLongClick(
                messageModel: AbstractMessageModel?,
                itemView: View,
                position: Int,
            ): Boolean {
                actionMode?.finish()
                chatsAdapter?.toggleChecked(position)
                if ((chatsAdapter?.checkedItemsCount ?: 0) > 0) {
                    actionMode = startSupportActionMode(actionModeCallback)
                }
                return true
            }
        })
        val recyclerView = findViewById<EmptyRecyclerView>(R.id.recycler_chats)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.itemAnimator = DefaultItemAnimator()
        val emptyView = EmptyView(this, ConfigUtils.getActionBarSize(this))
        emptyView.setup(
            R.string.no_starred_messages,
            R.drawable.ic_star_golden_24dp,
            null,
        )
        (recyclerView.parent.parent as ViewGroup).addView(emptyView)
        recyclerView.emptyView = emptyView
        emptyView.setLoading(true)
        recyclerView.adapter = chatsAdapter

        globalSearchViewModel =
            ViewModelProvider(this)[GlobalSearchViewModel::class.java].also { globalSearchViewModel ->
                globalSearchViewModel.messageModels.observe(this) { messages ->
                    emptyView.setLoading(false)
                    chatsAdapter?.setMessageModels(messages)
                    removeStarsMenuItem?.isVisible =
                        messages.isNotEmpty() && (searchView?.isIconified ?: false)
                }
            }

        onQueryTextChange(null)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.action_starred_messages_search, menu)
        val searchMenuItem = menu.findItem(R.id.menu_action_search)
        searchView = searchMenuItem.actionView as ThreemaSearchView?
        searchView?.let {
            if (ConfigUtils.isLandscape(this)) {
                it.maxWidth = Int.MAX_VALUE
            }

            ConfigUtils.adjustSearchViewPadding(it)
            it.queryHint = getString(R.string.hint_filter_list)
            it.setOnQueryTextListener(this)
            it.setOnSearchClickListener {
                searchBar?.hint = ""
                sortMenuItem?.isVisible = false
                removeStarsMenuItem?.isVisible = false
            }
            // Show the hint of the search bar again when the search view is closed
            it.setOnCloseListener {
                searchBar?.setHint(R.string.starred_messages)
                sortMenuItem?.isVisible = true
                removeStarsMenuItem?.isVisible = (((chatsAdapter?.itemCount ?: 0) > 0))
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
            )
                .show(supportFragmentManager, "rem")
            false
        }
        removeStarsMenuItem?.isVisible = (((chatsAdapter?.itemCount ?: 0) > 0))
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

    private fun showMessage(messageModel: AbstractMessageModel?) {
        if (messageModel == null) {
            return
        }
        hideKeyboard()
        val intent = IntentDataUtil.getJumpToMessageIntent(this, messageModel)
        intent.putExtra(EXTRA_OVERRIDE_BACK_TO_HOME_BEHAVIOR, true)
        showMessageLauncher.launch(intent)
    }

    private fun removeStar(checkedItems: MutableList<AbstractMessageModel>?) {
        if (checkedItems != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                checkedItems.forEach {
                    it.displayTags = DISPLAY_TAG_NONE
                    messageService?.save(it)
                }

                ListenerManager.messageListeners.handle { listener ->
                    listener.onModified(
                        checkedItems,
                    )
                }
                checkedItems.clear()

                withContext(Dispatchers.Main) {
                    actionMode?.finish()
                    globalSearchViewModel?.onDataChanged()
                }
            }
        }
    }

    private fun removeAllStars() {
        lifecycleScope.launch(Dispatchers.IO) {
            messageService?.unstarAllMessages()
            withContext(Dispatchers.Main) {
                globalSearchViewModel?.onDataChanged()
            }
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.action_starred_messages, menu)

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val checked: Int = chatsAdapter?.checkedItemsCount ?: 0
            if (checked > 0) {
                mode?.title = checked.toString()
                return true
            }
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return if (R.id.menu_remove_star == item?.itemId) {
                removeStar(chatsAdapter?.checkedItems)
                true
            } else {
                false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            chatsAdapter?.clearCheckedItems()
            actionMode = null
        }
    }

    override fun onClick(tag: String, which: Int, data: Any?) {
        if (DIALOG_TAG_SORT_BY == tag) {
            sortOrder = which
            preferenceService?.starredMessagesSortOrder = sortOrder
            onQueryTextChange(queryText)
        }
    }

    override fun onYes(tag: String?, data: Any?) {
        removeAllStars()
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
        private val logger = LoggingUtil.getThreemaLogger("StarredMessagesActivity")
        private const val DIALOG_TAG_SORT_BY = "sortBy"
        private const val FILTER_FLAGS =
            FILTER_STARRED_ONLY or FILTER_GROUPS or FILTER_CHATS or FILTER_INCLUDE_ARCHIVED
    }
}
