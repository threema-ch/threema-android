/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.adapters

import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.adapter.FragmentViewHolder
import androidx.viewpager2.widget.ViewPager2
import ch.threema.app.fragments.BigMediaFragment
import ch.threema.app.utils.MediaAdapter
import ch.threema.app.utils.MediaAdapterManager
import ch.threema.base.utils.LoggingUtil

private val logger = LoggingUtil.getThreemaLogger("SendMediaAdapter")

class SendMediaAdapter(
    fm: FragmentManager,
    lifecycle: Lifecycle,
    private val mm: MediaAdapterManager,
    private val viewPager: ViewPager2?
) : FragmentStateAdapter(fm, lifecycle), MediaAdapter {

    private var fragments: MutableMap<Int, BigMediaFragment> = mutableMapOf()
    private var bottomElemHeight: Int = 0

    init {
        mm.setMediaAdapter(this)
    }

    fun setBottomElemHeight(bottomElemHeight: Int) {
        this.bottomElemHeight = bottomElemHeight
    }

    override fun getItemCount() = mm.size()

    @OptIn(UnstableApi::class) override fun createFragment(position: Int): Fragment {
        return BigMediaFragment.newInstance(mm.get(position), bottomElemHeight).also {
            fragments[position] = it
            it.setViewPager(viewPager)
        }
    }

    @OptIn(UnstableApi::class) override fun onBindViewHolder(
        holder: FragmentViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        super.onBindViewHolder(holder, position, payloads)

        fragments[position]?.setMediaItem(mm.get(position))
        fragments[position]?.showBigMediaItem()
    }

    override fun getItemId(position: Int): Long {
        return mm.get(position).uri.hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return mm.getItems().map { it.uri.hashCode().toLong() }.contains(itemId)
    }

    @OptIn(UnstableApi::class) override fun filenameUpdated(position: Int) {
        if (position < 0 || position >= itemCount) {
            logger.error("Could not update filename at position {} of {} items", position, itemCount)
            return
        }
        fragments[position]?.updateFilename()
    }

    @OptIn(UnstableApi::class) override fun videoMuteStateUpdated(position: Int) {
        if (position < 0 || position >= itemCount) {
            logger.error("Could not update video at position {} of {} items", position, itemCount)
            return
        }
        fragments[position]?.updateVideoPlayerSound()
    }

    override fun positionUpdated(oldPosition: Int, newPosition: Int) {
        // Nothing to do as the view pager position is updated via the layout
    }

    @OptIn(UnstableApi::class) override fun sendAsFileStateUpdated(position: Int) {
        fragments[position]?.updateSendAsFileState()
    }
}
