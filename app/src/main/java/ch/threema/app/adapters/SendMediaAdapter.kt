package ch.threema.app.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.adapter.FragmentViewHolder
import androidx.viewpager2.widget.ViewPager2
import ch.threema.app.fragments.BigMediaFragment
import ch.threema.app.utils.MediaAdapter
import ch.threema.app.utils.MediaAdapterManager
import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("SendMediaAdapter")

class SendMediaAdapter(
    fm: FragmentManager,
    lifecycle: Lifecycle,
    private val mm: MediaAdapterManager,
    private val viewPager: ViewPager2?,
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

    override fun createFragment(position: Int): Fragment {
        return BigMediaFragment.newInstance(mm.get(position), bottomElemHeight).also {
            fragments[position] = it
            it.setViewPager(viewPager)
        }
    }

    override fun onBindViewHolder(
        holder: FragmentViewHolder,
        position: Int,
        payloads: MutableList<Any>,
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

    override fun filenameUpdated(position: Int) {
        if (position !in 0 until itemCount) {
            logger.error(
                "Could not update filename at position {} of {} items",
                position,
                itemCount,
            )
            return
        }
        fragments[position]?.updateFilename()
    }

    override fun videoMuteStateUpdated(position: Int) {
        if (position !in 0 until itemCount) {
            logger.error("Could not update video at position {} of {} items", position, itemCount)
            return
        }
        fragments[position]?.updateVideoPlayerSound()
    }

    override fun positionUpdated(oldPosition: Int, newPosition: Int) {
        // Nothing to do as the view pager position is updated via the layout
    }

    override fun sendAsFileStateUpdated(position: Int) {
        fragments[position]?.updateSendAsFileState()
    }
}
