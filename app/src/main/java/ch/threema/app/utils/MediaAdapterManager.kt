/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.utils

import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.adapters.SendMediaAdapter
import ch.threema.app.adapters.SendMediaPreviewAdapter
import ch.threema.app.ui.MediaItem
import java.util.*

const val NOTIFY_LISTENER: Int = 1
const val NOTIFY_ADAPTER: Int = 2
const val NOTIFY_PREVIEW_ADAPTER = 4
const val NOTIFY_BOTH_ADAPTERS = NOTIFY_ADAPTER or NOTIFY_PREVIEW_ADAPTER
const val NOTIFY_ALL = -1

class MediaAdapterManager(private val mediaAdapterListener: MediaAdapterListener) {
    private var mediaAdapter: SendMediaAdapter? = null
    private var mediaPreviewAdapter: SendMediaPreviewAdapter? = null
    private val items: MutableList<MediaItem> = mutableListOf()
    private var currentPosition = 0
    private var futurePosition = -1
    private val onNextItemRunnable = mutableListOf<Runnable>()

    fun setMediaAdapter(adapter: SendMediaAdapter) {
        mediaAdapter = adapter
    }

    fun setMediaPreviewAdapter(adapter: SendMediaPreviewAdapter) {
        mediaPreviewAdapter = adapter
    }

    fun add(item: MediaItem, notify: Int = NOTIFY_BOTH_ADAPTERS) {
        add(listOf(item), notify)
    }

    /**
     * append items to list
     */
    fun add(itemList: List<MediaItem?>, notify: Int = NOTIFY_BOTH_ADAPTERS) {
        add(itemList, items.size, notify)
    }

    fun add(itemList: List<MediaItem?>, indexWhereToAdd: Int, notify: Int = NOTIFY_BOTH_ADAPTERS) {
        items.addAll(indexWhereToAdd, itemList.filterNotNull())
        notifyItemRangeChanged(indexWhereToAdd, itemList.size, notify)
        if (futurePosition >= 0 && futurePosition < size()) {
            changePosition(futurePosition, NOTIFY_ALL)
            futurePosition = -1
        }
        mediaAdapterListener.onItemCountChanged(size())
        while (size() > 0 && onNextItemRunnable.size > 0) {
            onNextItemRunnable.removeFirst().run()
        }
    }

    fun remove(index: Int, notify: Int = NOTIFY_ALL) {
        items.removeAt(index)
        if (isNotifyListener(notify)) {
            mediaAdapterListener.onItemCountChanged(size())
            if (size() == 0) {
                mediaAdapterListener.onAllItemsRemoved()
                return
            }
        }

        notifyItemRemoved(index, notify)
        if (currentPosition >= items.size) {
            currentPosition = items.size - 1
        }
        changePosition(currentPosition, notify)
    }

    fun update(position: Int, notify: Int) {
        notifyItemChanged(position, notify)
    }

    fun updateCurrent(notify: Int) {
        update(currentPosition, notify)
    }

    fun updateFilename(notify: Int = NOTIFY_ADAPTER) {
        if (isNotifyAdapter(notify)) {
            mediaAdapter?.filenameUpdated(currentPosition)
        }
        if (isNotifyPreviewAdapter(notify)) {
            mediaPreviewAdapter?.filenameUpdated(currentPosition)
        }
    }

    fun updateMuteState(notify: Int = NOTIFY_BOTH_ADAPTERS) {
        if (isNotifyAdapter(notify)) {
            mediaAdapter?.videoMuteStateUpdated(currentPosition)
        }
        if (isNotifyPreviewAdapter(notify)) {
            mediaPreviewAdapter?.videoMuteStateUpdated(currentPosition)
        }
    }

    fun updateSendAsFileState(notify: Int = NOTIFY_BOTH_ADAPTERS) {
        if (isNotifyAdapter(notify)) {
            mediaAdapter?.sendAsFileStateUpdated(currentPosition)
        }
        if (isNotifyPreviewAdapter(notify)) {
            mediaPreviewAdapter?.sendAsFileStateUpdated(currentPosition)
        }
    }

    fun move(fromPosition: Int, toPosition: Int, notify: Int): Boolean {
        if (toPosition < items.size) {
            Collections.swap(items, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition, notify)
            if (fromPosition == currentPosition) {
                // current position has been moved
                currentPosition = toPosition
            } else if (currentPosition in fromPosition..toPosition) {
                // an element from left of the current position has moved to the right
                currentPosition -= 1
            } else if (currentPosition in toPosition..fromPosition) {
                // an element from right of the current position has moved to the left
                currentPosition += 1
            }
            return true
        }
        return false
    }

    fun getCurrentPosition() = currentPosition

    fun getCurrentItem() = items[currentPosition]

    fun get(position: Int) = items[position]

    fun runWhenCurrentItemAvailable(runnable: Runnable) {
        if (size() > 0) {
            runnable.run()
        } else {
            onNextItemRunnable.add(runnable)
        }
    }

    fun getItems() = items

    fun size() = items.size

    fun changePosition(position: Int, notify: Int = NOTIFY_ALL) {
        if (position < 0 || position >= items.size) {
            throw IndexOutOfBoundsException("Cannot update position to $position while containing ${size()} elements")
        }
        val oldPosition = currentPosition
        currentPosition = position
        if (isNotifyListener(notify)) {
            mediaAdapterListener.onPositionChanged()
        }
        if (isNotifyPreviewAdapter(notify)) {
            mediaPreviewAdapter?.positionUpdated(oldPosition, position)
        }
        if (isNotifyAdapter(notify)) {
            mediaAdapter?.positionUpdated(oldPosition, position)
        }
    }

    fun changePositionWhenItemsLoaded(position: Int) {
        futurePosition = position
    }

    fun onAddClicked() {
        mediaAdapterListener.onAddClicked()
    }

    fun hasChangedItems(): Boolean = items.any { it.hasChanges() }

    private fun notifyAdapters(func: (a: RecyclerView.Adapter<*>) -> Unit, notify: Int) {
        if (isNotifyAdapter(notify)) {
            mediaAdapter?.let(func)
        }
        if (isNotifyPreviewAdapter(notify)) {
            mediaPreviewAdapter?.let(func)
        }
    }

    private fun notifyItemRangeChanged(start: Int, itemCount: Int, notify: Int) {
        notifyAdapters({a -> a.notifyItemRangeChanged(start, itemCount)}, notify)
    }

    private fun notifyItemChanged(position: Int, notify: Int) {
        notifyAdapters({a -> a.notifyItemChanged(position)}, notify)
    }

    private fun notifyItemMoved(fromPosition: Int, toPosition: Int, notify: Int = NOTIFY_BOTH_ADAPTERS) {
        notifyAdapters({a -> a.notifyItemMoved(fromPosition, toPosition)}, notify)
    }

    private fun notifyItemRemoved(position: Int, notify: Int = NOTIFY_BOTH_ADAPTERS) {
        notifyAdapters({a -> a.notifyItemRemoved(position)}, notify)
    }

    private fun isNotifyListener(notify: Int) = notify and NOTIFY_LISTENER == NOTIFY_LISTENER
    private fun isNotifyAdapter(notify: Int) = notify and NOTIFY_ADAPTER == NOTIFY_ADAPTER
    private fun isNotifyPreviewAdapter(notify: Int) = notify and NOTIFY_PREVIEW_ADAPTER == NOTIFY_PREVIEW_ADAPTER
}

/**
 * These functions are used to inform the media adapter listener about changes that are triggered
 * directly by the media adapters.
 */
interface MediaAdapterListener {
    fun onPositionChanged()
    fun onAddClicked()
    fun onAllItemsRemoved()
    fun onItemCountChanged(newSize: Int)
}

/**
 * These functions are used to inform the media adapters about changes that might require an UI
 * update.
 */
interface MediaAdapter {
    fun positionUpdated(oldPosition: Int, newPosition: Int)
    fun filenameUpdated(position: Int)
    fun videoMuteStateUpdated(position: Int)
    fun sendAsFileStateUpdated(position: Int)
}
