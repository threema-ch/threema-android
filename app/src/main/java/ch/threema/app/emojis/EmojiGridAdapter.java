/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.app.emojis;

import android.content.Context;
import androidx.annotation.ColorInt;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;

import java.util.ArrayList;

import androidx.core.content.res.ResourcesCompat;
import ch.threema.app.R;

import static ch.threema.app.emojis.EmojiSpritemap.emojiCategories;

public class EmojiGridAdapter extends BaseAdapter {
	private final int pageNumber;
	private final int emojiItemSize;
	private final int emojiItemPaddingSize;
	@ColorInt private final int diverseHintColor;
	private final EmojiService emojiService;
	private final ArrayList<EmojiInfo> emojis;
	Context context;

	private final KeyClickListener keyClickListener;

	public EmojiGridAdapter(Context context,
	                        int pageNumber,
	                        EmojiService emojiService,
	                        KeyClickListener listener) {
		this.context = context;
		this.pageNumber = pageNumber;
		this.keyClickListener = listener;
		this.emojiService = emojiService;
		this.diverseHintColor = context.getResources().getColor(R.color.emoji_picker_hint);
		if (EmojiManager.getInstance(context).getSpritemapInSampleSize() == 1) {
			this.emojiItemSize = context.getResources().getDimensionPixelSize(R.dimen.emoji_picker_item_size);
			this.emojiItemPaddingSize = (emojiItemSize - context.getResources().getDimensionPixelSize(R.dimen.emoji_picker_emoji_size)) / 2;
		} else {
			this.emojiItemSize = 44;
			this.emojiItemPaddingSize = (emojiItemSize - 32) / 2;
		}
		this.emojis = new ArrayList<>();
		if (pageNumber != 0) {
			for (EmojiInfo entry : emojiCategories.get(pageNumber - 1).emojiInfos) {
				// filter diversity child emojis
				if (entry.diversityFlag == EmojiSpritemap.DIVERSITY_CHILD) {
					continue;
				}

				// filter emojis with display flag set to 0
				if (entry.displayFlag == EmojiSpritemap.DISPLAY_NO) {
					continue;
				}

				this.emojis.add(entry);
			}
		} else {
			emojiService.syncRecentEmojis();
			getRecents();
		}
	}

	@Override
	public void notifyDataSetChanged() {
		// TODO hack
		if (pageNumber == 0) {
			this.emojis.clear();
			getRecents();
		}

		super.notifyDataSetChanged();
	}

	private void getRecents() {
		this.emojis.addAll(emojiService.getRecentEmojis());
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		final EmojiInfo item = getItem(position);
		final String emojiSequence = getKey(item.emojiSequence);

		final EmojiItemView view;
		if (convertView instanceof EmojiItemView) {
			view = (EmojiItemView)convertView;
		} else {
			final EmojiItemView emojiItemView = new EmojiItemView(context);
			Drawable background = ResourcesCompat.getDrawable(context.getResources(), R.drawable.listitem_background_selector_noripple, null);
			emojiItemView.setBackground(background);
			emojiItemView.setPadding(emojiItemPaddingSize, emojiItemPaddingSize, emojiItemPaddingSize, emojiItemPaddingSize);
			emojiItemView.setLayoutParams(new AbsListView.LayoutParams(emojiItemSize, emojiItemSize));
			view = emojiItemView;
		}

		view.setEmoji(emojiSequence, pageNumber != 0 && item.diversityFlag == EmojiSpritemap.DIVERSITY_PARENT, diverseHintColor);
		view.setContentDescription(emojiSequence);
		view.setOnClickListener(v -> keyClickListener.onEmojiKeyClicked(getKey(item.emojiSequence)));
		view.setOnLongClickListener(v -> {
			if (pageNumber != 0) {
				keyClickListener.onEmojiKeyLongClicked(v, item.emojiSequence);
			} else {
				keyClickListener.onRecentLongClicked(v, item.emojiSequence);
			}
			return true;
		});

		return view;
	}

	private String getKey(String parentKey) {
		return pageNumber != 0
			? emojiService.getPreferredDiversity(parentKey)
			: parentKey;
	}

	@Override
	public int getCount() {
		return this.emojis.size();
	}

	@Override
	public EmojiInfo getItem(int position) {
		return this.emojis.get(position);
	}

	@Override
	public boolean hasStableIds() {
		return true;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public interface KeyClickListener {
		void onEmojiKeyClicked(String emojiCodeString);

		void onEmojiKeyLongClicked(View view, String emojiCodeString);

		void onRecentLongClicked(View v, String emojiCodeString);
	}
}
