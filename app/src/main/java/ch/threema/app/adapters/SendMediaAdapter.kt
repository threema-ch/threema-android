/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.adapters.SendMediaAdapter.SendMediaHolder
import ch.threema.app.ui.CheckableFrameLayout
import ch.threema.app.ui.MediaItem
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.StringConversionUtil
import ch.threema.base.utils.LoggingUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.util.*

class SendMediaAdapter(
        private val context: Context,
        private val clickListener: ClickListener) : RecyclerView.Adapter<SendMediaHolder>() {
    private val items: MutableList<MediaItem?> = ArrayList()
    private var checkedItem: MediaItem? = null

    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    @IntDef(VIEW_TYPE_NORMAL, VIEW_TYPE_ADD)
    annotation class ViewType

    class SendMediaHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view)
        val deleteView: ImageView? = itemView.findViewById(R.id.delete_view)
        val brokenView: ImageView? = itemView.findViewById(R.id.broken_view)
        val qualifierView: LinearLayout? = itemView.findViewById(R.id.qualifier_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, @ViewType viewType: Int): SendMediaHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(if (viewType == VIEW_TYPE_ADD) R.layout.item_send_media_add else R.layout.item_send_media,
                        parent,
                        false)

        return SendMediaHolder(view)
    }

    override fun onBindViewHolder(holder: SendMediaHolder, position: Int) {
        if (getItemViewType(holder.bindingAdapterPosition) == VIEW_TYPE_NORMAL) {
            val item = items[holder.bindingAdapterPosition]

            holder.itemView.setOnClickListener { clickListener.onItemClicked(holder.bindingAdapterPosition, item, getItemViewType(holder.bindingAdapterPosition)) }
            holder.deleteView!!.setOnClickListener { clickListener.onDeleteKeyClicked(holder.bindingAdapterPosition) }
            holder.brokenView!!.visibility = View.GONE
            (holder.itemView as CheckableFrameLayout).isChecked = item == checkedItem

            Glide.with(context).load(item!!.uri)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .addListener(object : RequestListener<Drawable?> {
                        override fun onLoadFailed(e: GlideException?, model: Any, target: Target<Drawable?>, isFirstResource: Boolean): Boolean {
                            holder.brokenView.visibility = View.VISIBLE
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any, target: Target<Drawable?>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            if (item.type == MediaItem.TYPE_VIDEO_CAM || item.type == MediaItem.TYPE_VIDEO) {
                                holder.qualifierView!!.visibility = View.VISIBLE
                                val imageView: AppCompatImageView = holder.qualifierView.findViewById(R.id.video_icon)
                                imageView.setImageResource(R.drawable.ic_videocam_black_24dp)
                                val durationView = holder.qualifierView.findViewById<TextView>(R.id.video_duration_text)
                                if (item.durationMs > 0) {
                                    durationView.text = StringConversionUtil.getDurationString(item.durationMs)
                                    durationView.visibility = View.VISIBLE
                                } else {
                                    durationView.visibility = View.GONE
                                }
                            } else if (item.type == MediaItem.TYPE_GIF) {
                                holder.qualifierView!!.visibility = View.VISIBLE
                                val imageView: AppCompatImageView = holder.qualifierView.findViewById(R.id.video_icon)
                                imageView.setImageResource(R.drawable.ic_gif_24dp)
                                holder.qualifierView.findViewById<View>(R.id.video_duration_text).visibility = View.GONE
                            } else {
                                holder.qualifierView!!.visibility = View.GONE
                            }
                            return false
                        }
                    })
                    .into(holder.imageView)

            rotateAndFlipImageView(holder.imageView, item)
        } else {
            holder.itemView.setOnClickListener { clickListener.onAddKeyClicked() }
        }
    }

    @ViewType
    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) VIEW_TYPE_ADD else VIEW_TYPE_NORMAL
    }

    override fun getItemCount(): Int {
        return items.size + 1
    }

    fun add(item: MediaItem) {
        add(listOf(item))
    }

    /**
     * append items to list
     */
    fun add(itemList: List<MediaItem?>) {
        add(itemList, items.size)
    }

    fun add(itemList: List<MediaItem?>, indexWhereToAdd: Int) {
        items.addAll(indexWhereToAdd, itemList)
        notifyItemRangeInserted(indexWhereToAdd, itemList.size)
    }

    fun remove(index: Int) {
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    fun update(position: Int) {
        notifyItemChanged(position)
    }

    fun move(fromPosition: Int, toPosition: Int): Boolean {
        if (toPosition < items.size) {
            Collections.swap(items, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
            return true
        }
        return false
    }

    private fun rotateAndFlipImageView(imageView: ImageView, item: MediaItem?) {
        imageView.rotation = item!!.rotation.toFloat()
        if (item.flip == BitmapUtil.FLIP_NONE) {
            imageView.scaleY = 1f
            imageView.scaleX = 1f
        }
        if (item.flip and BitmapUtil.FLIP_HORIZONTAL == BitmapUtil.FLIP_HORIZONTAL) {
            imageView.scaleX = -1f
        }
        if (item.flip and BitmapUtil.FLIP_VERTICAL == BitmapUtil.FLIP_VERTICAL) {
            imageView.scaleY = -1f
        }
    }

    fun getItems(): List<MediaItem?> {
        return items
    }

    fun getItem(index: Int): MediaItem? {
        return items[index]
    }

    fun size(): Int {
        return items.size
    }

    fun setItemChecked(position: Int) {
        for (i in items.indices) {
            val item = items[i]
            if (item == checkedItem) {
                if (i != position) {
                    notifyItemChanged(i)
                } else {
                    return
                }
                break
            }
        }
        try {
            checkedItem = items[position]
            notifyItemChanged(position)
        } catch (e: IndexOutOfBoundsException) {
            logger.error("Unable to find item to select", e)
        }
    }

    interface ClickListener {
        fun onItemClicked(position: Int, item: MediaItem?, @ViewType itemViewType: Int)
        fun onDeleteKeyClicked(position: Int)
        fun onAddKeyClicked()
    }

    companion object {
        private val logger = LoggingUtil.getThreemaLogger("SendMediaGridAdapter")
        const val VIEW_TYPE_NORMAL = 0
        const val VIEW_TYPE_ADD = 1
    }
}
