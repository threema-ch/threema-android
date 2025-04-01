/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2024 Threema GmbH
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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.util.forEach
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.FileService
import ch.threema.app.ui.CheckableFrameLayout
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.IconUtil
import ch.threema.app.utils.MimeUtil
import ch.threema.app.utils.StringConversionUtil
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.target.CustomViewTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.imageview.ShapeableImageView

class MediaGalleryAdapter(
    private val context: Context,
    clickListener: OnClickItemListener,
    messageReceiver: MessageReceiver<*>,
    columnCount: Int
) :
    RecyclerView.Adapter<MediaGalleryAdapter.MediaGalleryHolder>() {
    private val clickListener: OnClickItemListener
    private val columnCount: Int
    private val messageReceiver: MessageReceiver<*>
    private val checkedItems = SparseBooleanArray()
    private var messageModels: MutableList<AbstractMessageModel>? = null
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    @ColorInt
    private val foregroundColor: Int
    private val fileService: FileService?

    private val viewOutlineProvider: ViewOutlineProvider

    init {
        this.clickListener = clickListener
        this.columnCount = columnCount
        this.messageReceiver = messageReceiver
        this.foregroundColor = ConfigUtils.getColorFromAttribute(context, R.attr.colorOnBackground)
        this.fileService = ThreemaApplication.getServiceManager()?.fileService

        val cornerRadius: Int =
            context.resources.getDimensionPixelSize(R.dimen.media_gallery_container_radius)
        this.viewOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius.toFloat())
            }
        }
    }

    class MediaGalleryHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var imageView: ShapeableImageView? = null
        var videoContainerView: View? = null
        var animatedFormatLabelContainer: View? = null
        var animatedFormatLabelIconView: ImageView? = null
        var videoDuration: TextView? = null
        var vmContainerView: View? = null
        var vmDuration: TextView? = null
        var topTextView: TextView? = null
        var textContainerView: View? = null
        var messageId = 0

        init {
            imageView = itemView.findViewById(R.id.thumbnail_view)
            animatedFormatLabelContainer =
                itemView.findViewById(R.id.animated_format_label_container)
            animatedFormatLabelIconView = itemView.findViewById(R.id.animated_format_label_icon)
            videoContainerView = itemView.findViewById(R.id.video_marker_container)
            videoDuration = itemView.findViewById(R.id.video_duration_text)
            vmContainerView = itemView.findViewById(R.id.voicemessage_marker_container)
            vmDuration = itemView.findViewById(R.id.voicemessage_duration_text)
            topTextView = itemView.findViewById(R.id.text_filename)
            textContainerView = itemView.findViewById(R.id.filename_container)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaGalleryHolder {
        val itemView: View = inflater.inflate(R.layout.item_media_gallery, parent, false)
        val holder = MediaGalleryHolder(itemView)
        holder.vmContainerView?.outlineProvider = viewOutlineProvider
        holder.videoContainerView?.outlineProvider = viewOutlineProvider
        holder.animatedFormatLabelContainer?.outlineProvider = viewOutlineProvider
        holder.textContainerView?.outlineProvider = viewOutlineProvider
        holder.vmContainerView?.clipToOutline = true
        holder.videoContainerView?.clipToOutline = true
        holder.animatedFormatLabelContainer?.clipToOutline = true
        holder.textContainerView?.clipToOutline = true

        return holder
    }

    override fun onBindViewHolder(holder: MediaGalleryHolder, position: Int) {
        messageModels?.let {
            val messageModel: AbstractMessageModel = it[position]

            if (holder.messageId != messageModel.id) {
                val placeholderIcon: Int =
                    if (messageModel.messageContentsType == MessageContentsType.VOICE_MESSAGE) {
                        R.drawable.ic_keyboard_voice_outline
                    } else if (messageModel.type == MessageType.FILE) {
                        IconUtil.getMimeIcon(messageModel.fileData.mimeType)
                    } else {
                        IconUtil.getMimeIcon("application/x-error")
                    }

                // do not load contents again if it's unchanged
                Glide.with(context)
                    .load(messageModel)
                    .transition(withCrossFade())
                    .optionalCenterCrop()
                    .error(placeholderIcon)
                    .into(object :
                        CustomViewTarget<ShapeableImageView?, Drawable?>(holder.imageView!!) {
                        override fun onResourceCleared(placeholder: Drawable?) {}
                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            decorateItem(holder, messageModel)
                            holder.imageView?.setImageDrawable(errorDrawable)
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            transition: Transition<in Drawable?>?
                        ) {
                            holder.textContainerView?.visibility = View.GONE
                            holder.vmContainerView?.visibility = View.GONE
                            holder.imageView?.clearColorFilter()
                            holder.imageView?.scaleType = ImageView.ScaleType.CENTER_CROP
                            holder.imageView?.setImageDrawable(resource)
                            if (messageModel.messageContentsType == MessageContentsType.GIF) {
                                holder.animatedFormatLabelContainer?.visibility = View.VISIBLE
                                holder.animatedFormatLabelIconView?.setImageResource(R.drawable.ic_gif_24dp)
                                holder.animatedFormatLabelIconView?.contentDescription =
                                    context.getString(R.string.attach_gif)
                            } else if (messageModel.messageContentsType == MessageContentsType.IMAGE && MimeUtil.isAnimatedImageFormat(
                                    messageModel.fileData.mimeType
                                )
                            ) {
                                holder.animatedFormatLabelContainer?.visibility = View.VISIBLE
                                holder.animatedFormatLabelIconView?.setImageResource(R.drawable.ic_webp)
                                holder.animatedFormatLabelIconView?.contentDescription = "WebP"
                            } else {
                                holder.animatedFormatLabelContainer?.visibility = View.GONE
                            }

                            if (messageModel.messageContentsType == MessageContentsType.VIDEO) {
                                val duration: Long = when (messageModel.type) {
                                    MessageType.VIDEO -> {
                                        messageModel.videoData.duration.toLong()
                                    }

                                    MessageType.FILE -> {
                                        messageModel.fileData.durationSeconds
                                    }

                                    else -> {
                                        0
                                    }
                                }

                                if (duration > 0) {
                                    holder.videoDuration?.text =
                                        StringConversionUtil.secondsToString(duration, false)
                                    holder.videoDuration?.visibility = View.VISIBLE
                                } else {
                                    holder.videoDuration?.visibility = View.GONE
                                }
                                holder.videoContainerView?.visibility = View.VISIBLE
                            } else {
                                holder.videoContainerView?.visibility = View.GONE
                            }
                        }
                    })
            }
            holder.messageId = messageModel.id
            (holder.itemView as CheckableFrameLayout).isChecked = checkedItems.get(position)

            holder.itemView.setOnClickListener { v: View? ->
                clickListener.onClick(
                    messageModel,
                    holder.itemView,
                    holder.absoluteAdapterPosition
                )
            }
            holder.itemView.setOnLongClickListener {
                clickListener.onLongClick(
                    messageModel,
                    holder.itemView,
                    holder.absoluteAdapterPosition
                )
            }
        }
    }

    private fun decorateItem(holder: MediaGalleryHolder, messageModel: AbstractMessageModel) {
        holder.imageView?.scaleType = ImageView.ScaleType.CENTER
        holder.imageView?.setColorFilter(foregroundColor, PorterDuff.Mode.SRC_IN)
        holder.videoContainerView?.visibility = View.GONE
        holder.animatedFormatLabelContainer?.visibility = View.GONE

        if (messageModel.messageContentsType == MessageContentsType.VOICE_MESSAGE) {
            val duration: Long = if (messageModel.type == MessageType.FILE) {
                messageModel.fileData.durationSeconds
            } else if (messageModel.type == MessageType.VOICEMESSAGE) {
                messageModel.audioData.duration.toLong()
            } else {
                0
            }
            holder.vmDuration?.text = StringConversionUtil.secondsToString(duration, false)
            holder.vmContainerView?.visibility = View.VISIBLE
            holder.textContainerView?.visibility = View.GONE
        } else if (messageModel.type == MessageType.FILE) {
            holder.topTextView?.text = messageModel.fileData.fileName
            holder.textContainerView?.visibility = View.VISIBLE
            holder.vmContainerView?.visibility = View.GONE
        } else {
            holder.textContainerView?.visibility = View.GONE
            holder.vmContainerView?.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int {
        return messageModels?.size ?: 0
    }

    fun getItemAtPosition(position: Int): AbstractMessageModel? {
        return messageModels?.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(items: MutableList<AbstractMessageModel>?) {
        messageModels = items
        notifyDataSetChanged()
    }

    fun toggleChecked(pos: Int) {
        if (checkedItems[pos, false]) {
            checkedItems.delete(pos)
        } else {
            checkedItems.put(pos, true)
        }
        notifyItemChanged(pos)
    }

    fun clearCheckedItems() {
        var itemsToClear: Array<Int> = arrayOf()

        checkedItems.forEach { position, isChecked ->
            if (isChecked) {
                itemsToClear += position
            }
        }
        checkedItems.clear()
        itemsToClear.forEach { position -> notifyItemChanged(position) }
    }

    fun selectAll() {
        messageModels?.let {
            if (checkedItems.size() == it.size) {
                clearCheckedItems()
            } else {
                for (i in it.indices) {
                    checkedItems.put(i, true)
                    notifyItemChanged(i)
                }
            }
        }
    }

    fun getCheckedItemsCount(): Int {
        return checkedItems.size()
    }

    fun getCheckedItems(): List<AbstractMessageModel> {
        val items: MutableList<AbstractMessageModel> = ArrayList(checkedItems.size())
        checkedItems.forEach { key, _ ->
            messageModels?.let {
                if (key >= 0 && key < it.size) {
                    items.add(it[key])
                }
            }
        }
        return items
    }

    /**
     * get specified checked item. returns null if out of range or no data available
     */
    fun getCheckedItemAt(i: Int): AbstractMessageModel? {
        if (i >= 0 && i < checkedItems.size()) {
            messageModels?.let {
                return it[checkedItems.keyAt(i)];
            }
        }
        return null
    }

    @SuppressLint("NotifyDataSetChanged")
    fun removeItems(deletedMessages: List<AbstractMessageModel>) {
        checkedItems.clear()

        if (deletedMessages.size == 1) {
            messageModels?.let {
                val deletedMessage = deletedMessages.get(0)
                val index = it.indexOf(deletedMessage)
                it.remove(deletedMessage)
                notifyItemRemoved(index)
            }
        } else {
            messageModels?.removeAll(deletedMessages)
            notifyDataSetChanged()
        }
    }

    interface OnClickItemListener {
        fun onClick(messageModel: AbstractMessageModel?, view: View?, position: Int)
        fun onLongClick(
            messageModel: AbstractMessageModel?,
            itemView: View?,
            position: Int
        ): Boolean
    }
}
