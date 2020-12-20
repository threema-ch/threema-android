/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2020 Threema GmbH
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
import android.util.AttributeSet;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;

public class EmojiButton extends androidx.appcompat.widget.AppCompatImageButton implements EmojiPicker.EmojiPickerListener {
	private boolean fullscreenIme;
	private Context context;

	public EmojiButton(Context context) {
		this(context, null);
	}

	public EmojiButton(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmojiButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		this.context = context;
		showEmojiIcon();
	}

	public void showEmojiIcon() {
		setImageResource(R.drawable.ic_tag_faces_outline);
	}

	public void showKeyboardIcon() {
		if (ConfigUtils.isLandscape(context) &&
				!ConfigUtils.isTabletLayout() &&
				fullscreenIme) {
			setImageResource(R.drawable.ic_keyboard_arrow_down_outline);
		} else {
			setImageResource(R.drawable.ic_keyboard_outline);
		}
	}

	public void attach(EmojiPicker emojiPicker, boolean fullscreenIme) {
		this.fullscreenIme = fullscreenIme;
		emojiPicker.addEmojiPickerListener(this);
	}

	public void detach(EmojiPicker emojiPicker) {
		if (emojiPicker != null) {
			emojiPicker.removeEmojiPickerListener(this);
		}
	}

	@Override
	public void onEmojiPickerOpen() {
		showKeyboardIcon();
	}

	@Override
	public void onEmojiPickerClose() {
		showEmojiIcon();
	}
}
