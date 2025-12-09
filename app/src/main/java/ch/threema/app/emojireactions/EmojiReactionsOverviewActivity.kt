/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.ThreemaToolbarActivity
import ch.threema.app.emojis.EmojiTextView
import ch.threema.app.services.UserService
import ch.threema.app.utils.IntentDataUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.EmojiReactionData
import ch.threema.data.repositories.EmojiReactionsRepository.ReactionMessageIdentifier
import ch.threema.storage.models.AbstractMessageModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

private val logger = getThreemaLogger("EmojiReactionsOverviewActivity")

class EmojiReactionsOverviewActivity : ThreemaToolbarActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val userService: UserService by inject()
    private val myIdentity by lazy { userService.identity }

    private lateinit var viewPager: ViewPager2
    private lateinit var parentLayout: CoordinatorLayout
    private var initialItem: String? = null
    private var messageModel: AbstractMessageModel? = null
    private val items = mutableListOf<EmojiReactionItems>()
    private var tabLayoutMediator: TabLayoutMediator? = null
    private var emojiReactionsOverviewAdapter: EmojiReactionsOverviewAdapter? = null

    data class EmojiReactionItems(
        val emojiSequence: String,
        val count: Int,
        val isMyReaction: Boolean,
    )

    override fun getLayoutResource(): Int = R.layout.activity_emojireactions_overview

    @SuppressLint("ClickableViewAccessibility")
    override fun initActivity(savedInstanceState: Bundle?): Boolean {
        if (!super.initActivity(savedInstanceState)) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }

        val serviceManager = ThreemaApplication.requireServiceManager()
        val messageService = serviceManager.messageService

        messageModel = IntentDataUtil.getAbstractMessageModel(intent, messageService)
        messageModel?.let { message ->

            val reactionMessageIdentifier: ReactionMessageIdentifier =
                ReactionMessageIdentifier.fromMessageModel(message) ?: run {
                    logger.error(
                        "Closing emoji overview for unsupported message model type of {}",
                        message::class.java.simpleName,
                    )
                    return false
                }

            initialItem = intent.getStringExtra(EXTRA_INITIAL_EMOJI)

            val emojiReactionsViewModel: EmojiReactionsViewModel by viewModel {
                parametersOf(reactionMessageIdentifier)
            }

            setupParentLayout()
            setupViewPager()

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    emojiReactionsViewModel.viewState
                        .filterNotNull()
                        .collect { viewState ->
                            onViewStateChanged(
                                viewState.emojiReactions,
                                emojiReactionsViewModel,
                                message,
                            )
                        }
                }
            }
        } ?: run {
            logger.error("No message model found")
            finish()
            return false
        }
        return true
    }

    override fun handleDeviceInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.status_bar_background),
        ) { view: View, windowInsets: WindowInsetsCompat ->
            val insets = windowInsets.getInsets(systemBars() or displayCutout())
            val layoutParams = view.layoutParams
            layoutParams.height = insets.top
            view.layoutParams = layoutParams
            windowInsets
        }
    }

    private fun onViewStateChanged(
        emojiReactions: List<EmojiReactionData>,
        emojiReactionsViewModel: EmojiReactionsViewModel,
        message: AbstractMessageModel,
    ) {
        val oldItems = items.toList()

        items.clear()
        items.addAll(processReactions(emojiReactions))

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
    private fun setupViewPagerAdapter(
        emojiReactionsViewModel: EmojiReactionsViewModel,
        messageModel: AbstractMessageModel,
    ) {
        emojiReactionsOverviewAdapter = EmojiReactionsOverviewAdapter(
            this,
            emojiReactionsViewModel,
            messageModel,
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
                BottomSheetBehavior.STATE_HIDDEN,
            )
        }

        determineMaxHeight { availableHeight ->
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
        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    super.onPageScrollStateChanged(state)

                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        parentLayout.requestLayout()
                    }
                }
            },
        )
    }

    /**
     * Setup tab layout and attach it to view pager via TabLayoutMediator
     * Also sets accessibility texts for custom views
     */
    private fun setupTabLayout() {
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

                    // we use a shadow to achieve a "bolder than bold" effect
                    if (isMyReaction) {
                        setShadowLayer(1f, 2f, 2f, this.currentTextColor)
                    } else {
                        setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                    }
                }
            }
            tab.contentDescription =
                getString(
                    R.string.tab_emoji_reactions_overview_content_description,
                    emojiCount,
                    emojiSequence,
                )
        }
        tabLayoutMediator?.attach()
    }

    /**
     * Setup bottom sheet and set callback for state changes
     */
    private fun setupBottomSheet(bottomSheetLayout: ConstraintLayout): BottomSheetBehavior<ConstraintLayout> {
        val statusBarBackground = findViewById<View>(R.id.status_bar_background)

        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.isShouldRemoveExpandedCorners = true
        bottomSheetBehavior.addBottomSheetCallback(
            object : BottomSheetCallback() {

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    statusBarBackground.isVisible = newState == BottomSheetBehavior.STATE_EXPANDED
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        finish()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            },
        )

        return bottomSheetBehavior
    }

    private fun requiresViewPagerRecreation(
        oldData: List<EmojiReactionItems>,
        newData: List<EmojiReactionItems>,
    ): Boolean {
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

    private fun recreateViewPager(
        emojiReactionsViewModel: EmojiReactionsViewModel,
        messageModel: AbstractMessageModel,
    ) {
        // Detach existing TabLayoutMediator (if any)
        tabLayoutMediator?.detach()

        // Create new adapter
        setupViewPagerAdapter(emojiReactionsViewModel, messageModel)

        // Create and attach new TabLayoutMediator
        setupTabLayout()
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

    private fun determineMaxHeight(onAvailable: (Int) -> Unit) {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.parent_layout)) { view: View, windowInsets: WindowInsetsCompat ->
            val insets = windowInsets.getInsets(systemBars() or displayCutout())
            val windowHeight: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.height()
            } else {
                // It seems that this height value already excludes the vertical device insets
                resources.displayMetrics.heightPixels
            }
            var maxHeight = windowHeight
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                maxHeight -= insets.top
            }
            view.setOnApplyWindowInsetsListener(null)
            onAvailable(maxHeight.coerceAtLeast(0))
            windowInsets
        }
    }

    companion object {
        const val EXTRA_INITIAL_EMOJI = "extra_initial_emoji" // emoji to show initially
    }
}
