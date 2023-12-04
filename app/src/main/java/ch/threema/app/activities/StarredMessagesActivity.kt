/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2023 Threema GmbH
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import ch.threema.app.R
import ch.threema.app.collections.Functional
import ch.threema.app.collections.IPredicateNonNull
import ch.threema.app.dialogs.GenericAlertDialog
import ch.threema.app.dialogs.SelectorDialog
import ch.threema.app.globalsearch.GlobalSearchAdapter
import ch.threema.app.globalsearch.GlobalSearchViewModel
import ch.threema.app.managers.ListenerManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.DeadlineListService
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
import ch.threema.app.utils.EditTextUtil
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.data.DisplayTag.DISPLAY_TAG_NONE
import com.bumptech.glide.Glide
import com.google.android.material.search.SearchBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StarredMessagesActivity : ThreemaToolbarActivity(), SearchView.OnQueryTextListener, SelectorDialog.SelectorDialogClickListener, GenericAlertDialog.DialogClickListener {
    private val STARRED_MESSSAGES_SEARCH_QUERY_TIMEOUT_MS: Long = 500
    private var chatsAdapter: GlobalSearchAdapter? = null
    private var chatsViewModel: GlobalSearchViewModel? = null
    private var searchView: ThreemaSearchView? = null
    private var searchBar: SearchBar? = null
    private var hiddenChatsListService: DeadlineListService? = null
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
        chatsViewModel?.onQueryChanged(queryText, filterFlags, true, sortOrder == PreferenceService.StarredMessagesSortOrder_DATE_ASCENDING)
        chatsAdapter?.onQueryChanged(queryText)
    }
    private val showMessageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _: ActivityResult ->
        // starred status may have changed when returning from ComposeMessageFragment
        chatsViewModel?.onDataChanged()
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        queryText = newText
        queryHandler.removeCallbacksAndMessages(null)
        if (queryText?.isNotEmpty() == true) {
            queryHandler.postDelayed(queryTask, STARRED_MESSSAGES_SEARCH_QUERY_TIMEOUT_MS)
        } else {
            chatsViewModel?.onQueryChanged(null, filterFlags, true, sortOrder == PreferenceService.StarredMessagesSortOrder_DATE_ASCENDING)
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
            hiddenChatsListService = serviceManager.hiddenChatsListService
        } catch (e: Exception) {
            logger.error("Exception", e)
            finish()
        }
    }

    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        sortOrder = preferenceService?.starredMessagesSortOrder ?: PreferenceService.StarredMessagesSortOrder_DATE_DESCENDING

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
            50
        )
        chatsAdapter?.setOnClickItemListener(object : GlobalSearchAdapter.OnClickItemListener {
            override fun onClick(messageModel: AbstractMessageModel?, itemView: View, position: Int) {
                if (actionMode != null) {
                    chatsAdapter?.toggleChecked(position)
                    if ((chatsAdapter?.checkedItemsCount ?: 0)  > 0) {
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
                position: Int
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
        emptyView.setup(R.string.no_starred_messages, R.drawable.ic_star_golden_24dp)
        (recyclerView.parent.parent as ViewGroup).addView(emptyView)
        recyclerView.emptyView = emptyView
        emptyView.setLoading(true)
        recyclerView.adapter = chatsAdapter

        chatsViewModel = ViewModelProvider(this)[GlobalSearchViewModel::class.java]
        chatsViewModel?.messageModels?.observe(this) {
            emptyView.setLoading(false)
            if (it.isNotEmpty()) {
                chatsAdapter?.setMessageModels(Functional.filter(it, IPredicateNonNull(fun(messageModel: AbstractMessageModel): Boolean {
                    return if (messageModel is GroupMessageModel) {
                        messageModel.groupId > 0
                    } else {
                        messageModel.identity != null
                    }
                })))
            } else {
                chatsAdapter?.setMessageModels(it)
            }
            removeStarsMenuItem?.isVisible = it.isNotEmpty() && searchView?.isIconified ?: false
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
            GenericAlertDialog.newInstance(R.string.remove_all_stars, R.string.really_remove_all_stars, R.string.yes, R.string.no).show(supportFragmentManager, "rem")
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
                        SelectorDialogItem(getString(R.string.oldest_first), R.drawable.ic_arrow_upward)
                ),
                getString(R.string.cancel))
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
        if (searchView != null) {
            EditTextUtil.hideSoftKeyboard(searchView)
        }
        showMessageLauncher.launch(IntentDataUtil.getJumpToMessageIntent(this, messageModel))
    }

    private fun removeStar(checkedItems: MutableList<AbstractMessageModel>?) {
        if (checkedItems != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                checkedItems.forEach {
                    it.displayTags = DISPLAY_TAG_NONE
                    messageService?.save(it)

                }

                ListenerManager.messageListeners.handle { listener -> listener.onModified(checkedItems) }
                checkedItems.clear()

                withContext(Dispatchers.Main) {
                    actionMode?.finish()
                    chatsViewModel?.onDataChanged()
                }
            }
        }
    }

    private fun removeAllStars() {
        lifecycleScope.launch(Dispatchers.IO) {
            messageService?.unstarAllMessages()
            withContext(Dispatchers.Main) {
                chatsViewModel?.onDataChanged()
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

    companion object {
        private val logger = LoggingUtil.getThreemaLogger("StarredMessagesActivity")
        private const val DIALOG_TAG_SORT_BY = "sortBy"
        private const val filterFlags = FILTER_STARRED_ONLY or FILTER_GROUPS or FILTER_CHATS or FILTER_INCLUDE_ARCHIVED
    }
}


