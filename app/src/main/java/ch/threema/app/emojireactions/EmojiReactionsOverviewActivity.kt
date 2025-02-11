/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.app.emojireactions

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowMetrics
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.emojis.EmojiTextView
import ch.threema.app.emojis.EmojiUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier
import ch.threema.storage.models.AbstractMessageModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import org.slf4j.Logger

private val logger: Logger = LoggingUtil.getThreemaLogger("EmojiReactionOverviewActivity")

class EmojiReactionsOverviewActivity : ThreemaToolbarActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var parentLayout: CoordinatorLayout
    private lateinit var infoBox: View
    private var initialItem: String? = null
    private var messageModel: AbstractMessageModel? = null
    private val items = mutableListOf<EmojiReactionItems>()
    private var tabLayoutMediator: TabLayoutMediator? = null
    private var emojiReactionsOverviewAdapter: EmojiReactionsOverviewAdapter? = null
    private val statusBarColorExpanded: Int by lazy {
        ContextCompat.getColor(this, R.color.attach_status_bar_color_expanded)
    }
    private val statusBarColorCollapsed: Int by lazy {
        ContextCompat.getColor(this, R.color.attach_status_bar_color_collapsed)
    }

    data class EmojiReactionItems(val emojiSequence: String, val count: Int, val isMyReaction: Boolean)

    override fun getLayoutResource(): Int {
        return R.layout.activity_emojireactions_overview
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initActivity(savedInstanceState: Bundle?): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }

        val serviceManager = ThreemaApplication.requireServiceManager()
        val messageService = serviceManager.messageService
        val emojiReactionsRepository = serviceManager.modelRepositories.emojiReaction

        messageModel = IntentDataUtil.getAbstractMessageModel(intent, messageService)
        messageModel?.let { message ->

            val reactionMessageIdentifier: ReactionMessageIdentifier = ReactionMessageIdentifier.fromMessageModel(message) ?: run {
                logger.error("Closing emoji overview for unsupported message model type of {}", message::class.java.simpleName)
                return false
            }

            initialItem = intent.getStringExtra(EXTRA_INITIAL_EMOJI)

            val emojiReactionsViewModel: EmojiReactionsViewModel by viewModels {
                EmojiReactionsViewModel.provideFactory(
                    emojiReactionsRepository = emojiReactionsRepository,
                    messageService = messageService,
                    reactionMessageIdentifier = reactionMessageIdentifier
                )
            }

            if (!super.initActivity(savedInstanceState)) {
                finish()
                return false
            }

            infoBox = findViewById(R.id.infobox)
            window.statusBarColor = statusBarColorCollapsed

            setupParentLayout()
            setupViewPager()

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    emojiReactionsViewModel.emojiReactionsUiState
                        .collect { uiState -> onUiStateChanged(uiState, emojiReactionsViewModel, message) }
                }
            }

        } ?: run {
            logger.error("No message model found")
            finish()
            return false
        }
        return true
    }

    private fun onUiStateChanged(
        uiState: EmojiReactionsViewModel.EmojiReactionsUiState,
        emojiReactionsViewModel: EmojiReactionsViewModel,
        message: AbstractMessageModel
    ) {
        val oldItems = items.toList()

        items.clear()
        items.addAll(processReactions(uiState.emojiReactions))

        // transition to zero items means we have no reactions left to show, so quit
        if (oldItems.isNotEmpty() && items.isEmpty()) {
            finish()
            return
        }

        if (requiresViewPagerRecreation(oldItems, items)) {
            recreateViewPager(emojiReactionsViewModel, message)
            setupDefaultViewPagerItem()
        } else {
            emojiReactionsOverviewAdapter?.notifyDataSetChanged()
        }
    }

    /**
     * Show an infobox if there are new-style emoji reactions but we can't send them (V1)
     */
    private fun setupInfoBox() {
        if (!ConfigUtils.canSendEmojiReactions() && items.isNotEmpty()) {

            val count = items.filter { item ->
                item.emojiSequence != EmojiUtil.THUMBS_UP_SEQUENCE && item.emojiSequence != EmojiUtil.THUMBS_DOWN_SEQUENCE
            }

            infoBox.visibility = if (count.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    /**
     * Group and sort reactions and return a list of EmojiReactionItems
     */
    private fun processReactions(reactions: List<EmojiReactionData>): List<EmojiReactionItems> {
        return reactions
            .groupBy { it.emojiSequence }
            .map { (emojiSequence, reactionList) ->
                val count = reactionList.size
                val isMyReaction = reactionList.any { it.senderIdentity == myIdentity }
                EmojiReactionItems(emojiSequence, count, isMyReaction)
            }
            .sortedByDescending { it.count }
    }

    /**
     * Setup view pager item to show initially
     */
    private fun setupDefaultViewPagerItem() {
        val initialItemIndex = if (initialItem != null && items.isNotEmpty()) {
            items.indexOfFirst { it.emojiSequence == initialItem }.takeIf { it != -1 }
        } else {
            if (items.isNotEmpty()) 0 else null
        }

        initialItemIndex?.let {
            viewPager.post { viewPager.setCurrentItem(it, false) }
        }
        initialItem = null
    }

    /**
     * Setup view pager and adapter
     */
    private fun setupViewPagerAdapter(emojiReactionsViewModel: EmojiReactionsViewModel, messageModel: AbstractMessageModel) {
        emojiReactionsOverviewAdapter = EmojiReactionsOverviewAdapter(
            this,
            emojiReactionsViewModel,
            messageModel
        )
        viewPager.adapter = emojiReactionsOverviewAdapter
    }

    /**
     * Setup parent layout and set click listener to dismiss bottom sheet
     */
    private fun setupParentLayout() {
        val bottomSheetLayout = findViewById<ConstraintLayout>(R.id.bottom_sheet)
        val bottomSheetBehavior = setupBottomSheet(bottomSheetLayout)

        parentLayout = findViewById(R.id.parent_layout)
        parentLayout.setOnClickListener { _: View? ->
            bottomSheetBehavior.setState(
                BottomSheetBehavior.STATE_HIDDEN
            )
        }

        determineAvailableDrawingHeight { availableHeight ->
            bottomSheetBehavior.maxHeight = availableHeight
            bottomSheetBehavior.peekHeight = (availableHeight * 0.4).toInt()
            bottomSheetBehavior.state = STATE_COLLAPSED
        }
    }

    private fun setupViewPager() {
        viewPager = findViewById(R.id.view_pager)
        // workarounds for dragging issues with ViewPager2
        // see also https://github.com/material-components/material-components-android/issues/2689
        viewPager.children.find { it is RecyclerView }?.let {
            (it as RecyclerView).isNestedScrollingEnabled = false
        }
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)

                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    parentLayout.requestLayout()
                }
            }
        })
    }

    /**
     * Setup tab layout and attach it to view pager via TabLayoutMediator
     * Also sets accessibility texts for custom views
     */
    private fun setupTabLayout() {
        val textColor = ConfigUtils.getColorFromAttribute(this, R.attr.textColorPrimary)
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)

        tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            if (position > items.lastIndex) {
                return@TabLayoutMediator
            }

            val emojiSequence = items[position].emojiSequence
            val emojiCount = items[position].count.toString()
            val isMyReaction = items[position].isMyReaction

            tab.customView = layoutInflater.inflate(R.layout.tab_emoji_reactions_overview, null)
            tab.customView?.let {
                it.findViewById<EmojiTextView>(R.id.emoji).setSingleEmojiSequence(emojiSequence)
                val countTextView = it.findViewById<TextView>(R.id.count)

                countTextView?.apply {
                    text = emojiCount
                    typeface = if (isMyReaction) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                // we use a shadow to achieve a "bolder than bold" effect
                if (isMyReaction) {
                    countTextView.setShadowLayer(1f, 2f, 0f, textColor)
                } else {
                    countTextView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                }
            }
            tab.contentDescription =
                getString(R.string.tab_emoji_reactions_overview_content_description, emojiCount, emojiSequence)
        }
        tabLayoutMediator?.attach()
    }

    /**
     * Setup bottom sheet and set callback for state changes
     */
    private fun setupBottomSheet(bottomSheetLayout: ConstraintLayout): BottomSheetBehavior<ConstraintLayout> {
        val dragHandle = findViewById<View>(R.id.drag_handle)

        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.isShouldRemoveExpandedCorners = true
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            var previousSlideOffset = -1f

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    finish()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset > previousSlideOffset && slideOffset == 1.0f) {
                    // fully expanded
                    dragHandle.visibility = View.INVISIBLE
                    window.statusBarColor = getBottomSheetBackgroundColor(bottomSheetLayout)
                }
                if (previousSlideOffset > slideOffset && previousSlideOffset == 1.0f) {
                    // dragging
                    dragHandle.visibility = View.VISIBLE
                    window.statusBarColor = statusBarColorCollapsed
                }
                previousSlideOffset = slideOffset
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bottomSheetLayout.post {
                window.navigationBarColor = getBottomSheetBackgroundColor(bottomSheetLayout)
            }
        }

        return bottomSheetBehavior
    }

    private fun getBottomSheetBackgroundColor(bottomSheetLayout: ConstraintLayout): Int {
        val background = bottomSheetLayout.background
        if (background is MaterialShapeDrawable) {
            return background.resolvedTintColor
        }
        return statusBarColorExpanded
    }

    private fun requiresViewPagerRecreation(oldData: List<EmojiReactionItems>, newData: List<EmojiReactionItems>): Boolean {
        if (oldData.size != newData.size) {
            return true
        }

        for (i in oldData.indices) {
            if (oldData[i] != newData[i]) {
                return true
            }
        }
        return false
    }

    private fun recreateViewPager(emojiReactionsViewModel: EmojiReactionsViewModel, messageModel: AbstractMessageModel) {
        // Detach existing TabLayoutMediator (if any)
        tabLayoutMediator?.detach()

        // Create new adapter
        setupViewPagerAdapter(emojiReactionsViewModel, messageModel)

        // Create and attach new TabLayoutMediator
        setupTabLayout()
        setupInfoBox()
    }

    override fun finish() {
        try {
            super.finish()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        } catch (ignored: Exception) {
            // ignore
        }
    }

    private fun determineAvailableDrawingHeight(onAvailable: (Int) -> Unit) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            onAvailable(determineAvailableDrawingHeightApi30Impl())
        } else {
            val statusBarHeight = ConfigUtils.getStatusBarHeight(this)
            val navigationBarHeight = ConfigUtils.getNavigationBarHeight(this)
            val screenHeightWithoutTopInsets = resources.displayMetrics.heightPixels - statusBarHeight + navigationBarHeight
            onAvailable(screenHeightWithoutTopInsets.coerceAtLeast(0))
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun determineAvailableDrawingHeightApi30Impl(): Int {
        val metrics: WindowMetrics = windowManager.currentWindowMetrics
        val windowInsets: WindowInsets = metrics.getWindowInsets()
        val insetsTop = windowInsets.getInsets(
            WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.captionBar()
        )
        val insetsHeightTop = insetsTop.top + insetsTop.bottom

        return (metrics.bounds.height() - insetsHeightTop).coerceAtLeast(0)
    }

    companion object {
        const val EXTRA_INITIAL_EMOJI = "extra_initial_emoji" // emoji to show initially
    }
}

