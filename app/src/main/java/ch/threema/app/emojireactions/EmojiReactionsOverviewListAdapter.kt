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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.ContactService
import ch.threema.app.services.MessageService
import ch.threema.app.ui.AvatarView
import ch.threema.app.utils.AdapterUtil
import ch.threema.app.utils.MessageUtil
import ch.threema.app.utils.NameUtil
import ch.threema.data.models.EmojiReactionData
import ch.threema.storage.models.AbstractMessageModel

class EmojiReactionsOverviewListAdapter(
    messageService: MessageService,
    private val contactService: ContactService,
    val messageModel: AbstractMessageModel?,
    val onItemClickListener: OnItemClickListener?,
) :
    ListAdapter<EmojiReactionData, EmojiReactionsOverviewListAdapter.EmojiReactionViewHolder>(
        EmojiReactionDiffCallback(),
    ) {

    val messageReceiver: MessageReceiver<*> = messageService.getMessageReceiver(messageModel)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiReactionViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emoji_reaction_list, parent, false)
        return EmojiReactionViewHolder(itemView, ::getItem)
    }

    override fun onBindViewHolder(holder: EmojiReactionViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.onBind(currentItem)
    }

    class EmojiReactionDiffCallback : DiffUtil.ItemCallback<EmojiReactionData>() {
        override fun areItemsTheSame(
            oldItem: EmojiReactionData,
            newItem: EmojiReactionData,
        ): Boolean {
            return oldItem.emojiSequence == newItem.emojiSequence &&
                oldItem.senderIdentity == newItem.senderIdentity &&
                oldItem.messageId == newItem.messageId
        }

        override fun areContentsTheSame(
            oldItem: EmojiReactionData,
            newItem: EmojiReactionData,
        ): Boolean {
            return oldItem == newItem
        }
    }

    inner class EmojiReactionViewHolder(
        itemView: View,
        private val getItem: (Int) -> EmojiReactionData?,
    ) : RecyclerView.ViewHolder(itemView) {
        private val contactAvatarView: AvatarView = itemView.findViewById(R.id.contact_avatar)
        private val contactNameTextView: TextView = itemView.findViewById(R.id.contact_name)
        private val removeIconView: ImageView = itemView.findViewById(R.id.remove_icon)

        fun onBind(data: EmojiReactionData) {
            val avatar = contactService.getAvatar(data.senderIdentity, false)

            val contactModel = contactService.getByIdentity(data.senderIdentity)

            contactNameTextView.text = NameUtil.getDisplayNameOrNickname(contactModel, true)
            AdapterUtil.styleContact(contactNameTextView, contactModel)

            contactAvatarView.setImageBitmap(avatar)
            contactAvatarView.setBadgeVisible(contactService.showBadge(contactModel))

            removeIconView.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let {
                        onItemClickListener?.onRemoveClick(it, position)
                    }
                }
            }
            removeIconView.isVisible = data.senderIdentity == contactService.me.identity &&
                messageReceiver.emojiReactionSupport != MessageReceiver.Reactions_NONE &&
                MessageUtil.canEmojiReact(messageModel)
        }
    }

    fun interface OnItemClickListener {
        fun onRemoveClick(data: EmojiReactionData, position: Int)
    }
}
