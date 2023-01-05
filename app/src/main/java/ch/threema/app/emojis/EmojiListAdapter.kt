/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.app.emojis

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.emojis.EmojiListAdapter.EmojiItemViewHolder
import androidx.annotation.ColorInt
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import android.widget.AbsListView
import ch.threema.app.R
import ch.threema.app.utils.ConfigUtils

class EmojiListAdapter(
	context: Context,
	private val keyClickListener: KeyClickListener,
	private val emojiService: EmojiService
) : RecyclerView.Adapter<EmojiItemViewHolder>() {
	private var emojiItemSize = 0
	private var emojiItemPaddingSize = 0

	@ColorInt
	private val diverseHintColor: Int = ConfigUtils.getColorFromAttribute(context, R.attr.emoji_picker_hint)
	private var emojis: List<EmojiInfo> = emptyList()

	init {
		if (EmojiManager.getInstance(context).spritemapInSampleSize == 1) {
			emojiItemSize = context.resources.getDimensionPixelSize(R.dimen.emoji_picker_item_size)
			emojiItemPaddingSize = (emojiItemSize - context.resources.getDimensionPixelSize(R.dimen.emoji_picker_emoji_size)) / 2
		} else {
			emojiItemSize = 44
			emojiItemPaddingSize = (emojiItemSize - 32) / 2
		}
	}

	fun getItemHeight(): Int {
		return emojiItemSize
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiItemViewHolder {
		val context = parent.context
		val view = EmojiItemView(context)
		val background = ResourcesCompat.getDrawable(
			context.resources,
			R.drawable.listitem_background_selector_noripple,
			context.theme
		)
		view.background = background
		view.setPadding(
			emojiItemPaddingSize,
			emojiItemPaddingSize,
			emojiItemPaddingSize,
			emojiItemPaddingSize
		)
		view.layoutParams = AbsListView.LayoutParams(emojiItemSize, emojiItemSize)
		return EmojiItemViewHolder(view)
	}

	override fun onBindViewHolder(holder: EmojiItemViewHolder, position: Int) {
		val emojiInfo = emojis[position]
		val emojiSequence = emojiService.getPreferredDiversity(emojiInfo.emojiSequence)
		holder.emojiView.setEmoji(
			emojiSequence,
			emojiInfo.diversityFlag == EmojiSpritemap.DIVERSITY_PARENT,
			diverseHintColor
		)
		holder.emojiView.contentDescription = emojiSequence
		holder.emojiView.setOnClickListener {
			keyClickListener.onEmojiKeyClicked(emojiService.getPreferredDiversity(emojiInfo.emojiSequence))
		}
		holder.emojiView.setOnLongClickListener { v: View? ->
			keyClickListener.onEmojiKeyLongClicked(v, emojiInfo.emojiSequence)
			true
		}
	}

	override fun getItemCount(): Int {
		return emojis.size
	}

	@SuppressLint("NotifyDataSetChanged")
	fun setEmojis(emojis: List<EmojiInfo>) {
		this.emojis = emojis.toList()
		notifyDataSetChanged()
	}

	interface KeyClickListener {
		fun onEmojiKeyClicked(emojiCodeString: String?)
		fun onEmojiKeyLongClicked(view: View?, emojiCodeString: String?)
	}

	class EmojiItemViewHolder(internal val emojiView: EmojiItemView) : RecyclerView.ViewHolder(emojiView)
}
