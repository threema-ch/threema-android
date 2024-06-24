/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.adapters.SendMediaPreviewAdapter.SendMediaHolder
import ch.threema.app.services.PreferenceService.ImageScale_SEND_AS_FILE
import ch.threema.app.services.PreferenceService.VideoSize_SEND_AS_FILE
import ch.threema.app.ui.CheckableFrameLayout
import ch.threema.app.ui.MediaItem
import ch.threema.app.ui.MediaItem.*
import ch.threema.app.utils.*
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

class SendMediaPreviewAdapter(
        private val context: Context,
        private val mm: MediaAdapterManager
        ) : RecyclerView.Adapter<SendMediaHolder>(), MediaAdapter {

    init {
        mm.setMediaPreviewAdapter(this)
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(VIEW_TYPE_NORMAL, VIEW_TYPE_ADD)
    annotation class ViewType

    open class SendMediaHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.thumbnail_view)
    }

    class SendMediaItemHolder(itemView: View): SendMediaHolder(itemView) {
        val contentFrameLayout: CheckableFrameLayout = itemView.findViewById(R.id.content_frame)
        val fileIndicatorView: ImageView = itemView.findViewById(R.id.file_indicator_view)
        val mutedIndicatorView: ImageView = itemView.findViewById(R.id.video_send_no_audio)
        val deleteView: ImageView = itemView.findViewById(R.id.delete_view)
        val brokenView: ImageView = itemView.findViewById(R.id.broken_view)
        val qualifierView: LinearLayout = itemView.findViewById(R.id.qualifier_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, @ViewType viewType: Int): SendMediaHolder {
        val layout = if (viewType == VIEW_TYPE_ADD) R.layout.item_send_media_add else R.layout.item_send_media
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)

        return if (viewType == VIEW_TYPE_ADD) SendMediaHolder(view) else SendMediaItemHolder(view)
    }

    override fun onBindViewHolder(holder: SendMediaHolder, position: Int) {
        initializeSendMediaViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: SendMediaHolder, position: Int, payloads: List<Any>) {
        if (holder is SendMediaItemHolder) {
            val item = mm.get(position)

            if (isVideoMuteStateUpdate(payloads)) {
                // If it is a video sound update, we need to update the visibility of the muted icon
                updateMutedStateLayout(holder, item)
            }

            if (isPositionUpdate(payloads)) {
                // If the position is updated, we need to update the checked-state
                updateCheckedLayout(holder, position)
            }

            if (isSendAsFileUpdate(payloads)) {
                // If the send as file option has changed, we need to update the file indicator icon
                updateSendAsFileLayout(holder, item)
            }

            if (payloads.isEmpty()) {
                // A total refresh is needed, as the payloads may get dropped when the view is
                // updated while not visible
                initializeSendMediaItemViewHolder(holder, position)
            }
        } else {
            initializeSendMediaViewHolder(holder, position)
        }
    }

    @ViewType
    override fun getItemViewType(position: Int): Int {
        return if (position == mm.size()) VIEW_TYPE_ADD else VIEW_TYPE_NORMAL
    }

    override fun getItemCount(): Int {
        return mm.size() + 1
    }

    /**
     * Initialize the send media view holder
     */
    private fun initializeSendMediaViewHolder(holder: SendMediaHolder, position: Int) {
        if (holder is SendMediaItemHolder) {
            initializeSendMediaItemViewHolder(holder, position)
        } else {
            holder.itemView.setOnClickListener { mm.onAddClicked() }
        }
    }

    /**
     * Initialize the send media item view holder
     */
    private fun initializeSendMediaItemViewHolder(holder: SendMediaItemHolder, position: Int) {
        val item = mm.get(position)

        updateCheckedLayout(holder, position)

        updateMutedStateLayout(holder, item)

        updateSendAsFileLayout(holder, item)

        updateItemContentLayout(holder, item)
    }

    /**
     * Update the layout regarding the current position
     */
    private fun updateCheckedLayout(holder: SendMediaItemHolder, position: Int) {
        // Update the delete button depending on checked status
        val isChecked = position == mm.getCurrentPosition()
        holder.contentFrameLayout.isChecked = isChecked
        holder.deleteView.visibility = if (isChecked) View.VISIBLE else View.INVISIBLE

        // Set listeners
        holder.itemView.setOnClickListener { mm.changePosition(holder.bindingAdapterPosition) }
        holder.deleteView.setOnClickListener { mm.remove(holder.bindingAdapterPosition) }
    }

    /**
     * Update the layout regarding the muted state
     */
    private fun updateMutedStateLayout(holder: SendMediaItemHolder, item: MediaItem) {
        holder.mutedIndicatorView.visibility =
            if ((item.type == TYPE_VIDEO || item.type == TYPE_VIDEO_CAM) && item.isMuted) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    /**
     * Show the file indicator for files or images/videos that should be sent as files
     */
    private fun updateSendAsFileLayout(holder: SendMediaItemHolder, item: MediaItem) {
        val sendAsFile = item.type == TYPE_FILE || item.imageScale == ImageScale_SEND_AS_FILE || item.videoSize == VideoSize_SEND_AS_FILE
        holder.fileIndicatorView.visibility = if (sendAsFile) View.VISIBLE else View.GONE
    }

    /**
     * Show the given item in the preview
     */
    private fun updateItemContentLayout(holder: SendMediaItemHolder, item: MediaItem) {
        if (showAsFile(item)) {
            // Show file
            holder.imageView.setImageDrawable(AppCompatResources.getDrawable(context, IconUtil.getMimeIcon(item.mimeType)))
            holder.brokenView.visibility = View.INVISIBLE
        } else {
            // Show image/video/gif
            showMedia(holder, item)
        }
    }

    /**
     * Show the media item in the layout
     */
    private fun showMedia(holder: SendMediaItemHolder, item: MediaItem) {
        loadImage(item, holder)

        rotateAndFlipImageView(holder.imageView, item)
    }

    /**
     * Return true if the given media item should be displayed as file
     */
    private fun showAsFile(mediaItem: MediaItem): Boolean {
        return when(mediaItem.type) {
            TYPE_FILE -> true
            TYPE_VOICEMESSAGE -> true
            TYPE_TEXT -> true
            else -> false
        }
    }

    /**
     * Load the image asynchronously into the image view
     */
    private fun loadImage(item: MediaItem, holder: SendMediaItemHolder) {
        Glide.with(context).load(item.uri)
            .skipMemoryCache(true)
            .addListener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>,
                    isFirstResource: Boolean
                ): Boolean {
                    holder.imageView.setImageDrawable(null)
                    holder.brokenView.visibility = View.VISIBLE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable?>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    setQualifierView(item, holder)
                    holder.brokenView.visibility = View.INVISIBLE
                    return false
                }
            })
            .into(holder.imageView)
    }

    /**
     * Initialize the qualifier view
     */
    private fun setQualifierView(item: MediaItem, holder: SendMediaItemHolder) {
        val imageView: AppCompatImageView = holder.qualifierView.findViewById(R.id.video_icon)

        if (item.type == TYPE_VIDEO_CAM || item.type == TYPE_VIDEO) {
            holder.qualifierView.visibility = View.VISIBLE
            imageView.setImageResource(R.drawable.ic_videocam_black_24dp)
            val durationView = holder.qualifierView.findViewById<TextView>(R.id.video_duration_text)
            if (item.durationMs > 0) {
                durationView.text = StringConversionUtil.getDurationString(item.durationMs)
                durationView.visibility = View.VISIBLE
            } else {
                durationView.visibility = View.GONE
            }
        } else if (item.type == TYPE_IMAGE_ANIMATED) {
            holder.qualifierView.findViewById<View>(R.id.video_duration_text).visibility = View.GONE
            if (MimeUtil.isWebPFile(item.mimeType)) {
                holder.qualifierView.visibility = View.VISIBLE
                imageView.setImageResource(R.drawable.ic_webp)
            } else if (MimeUtil.isGifFile(item.mimeType)) {
                holder.qualifierView.visibility = View.VISIBLE
                imageView.setImageResource(R.drawable.ic_gif_24dp)
            } else {
                holder.qualifierView.visibility = View.GONE
            }
        } else {
            holder.qualifierView.visibility = View.GONE
        }
    }

    /**
     * Rotate and flip the image view based on the settings of the media item
     */
    private fun rotateAndFlipImageView(imageView: ImageView, item: MediaItem) {
        imageView.rotation = item.rotation.toFloat()
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

    private fun isPositionUpdate(payloads: List<Any>) = payloads.contains(CHANGE_POSITION)

    private fun isVideoMuteStateUpdate(payloads: List<Any>) = payloads.contains(CHANGE_MUTE)

    private fun isSendAsFileUpdate(payloads: List<Any>) = payloads.contains(CHANGE_SEND_AS_FILE)

    override fun filenameUpdated(position: Int) {
        // Nothing to do as the filename does not appear in the preview
    }

    override fun videoMuteStateUpdated(position: Int) {
        notifyItemChanged(position, CHANGE_MUTE)
    }

    override fun positionUpdated(oldPosition: Int, newPosition: Int) {
        notifyItemChanged(oldPosition, CHANGE_POSITION)
        notifyItemChanged(newPosition, CHANGE_POSITION)
    }

    override fun sendAsFileStateUpdated(position: Int) {
        notifyItemChanged(position, CHANGE_SEND_AS_FILE)
    }

    companion object {
        const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_ADD = 1
        private const val CHANGE_POSITION = 1
        private const val CHANGE_MUTE = 2
        private const val CHANGE_SEND_AS_FILE = 3
    }
}
