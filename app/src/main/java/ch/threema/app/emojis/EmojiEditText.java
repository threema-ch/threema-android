/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.ui.ThreemaEditText;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.client.Utils;

public class EmojiEditText extends ThreemaEditText {

	protected Context appContext;
	protected CharSequence hint;
	private String currentText;
	private int maxByteSize;

	public EmojiEditText(Context context) {
		super(context);

		init2(context);
	}

	public EmojiEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		init2(context);
	}

	public EmojiEditText(Context context, AttributeSet attrs) {
		super(context, attrs);

		init2(context);
	}

	private void init2(Context context) {
		this.appContext = context.getApplicationContext();
		this.hint = getHint();
		this.currentText = "";
		this.maxByteSize = 0;

		if (ConfigUtils.isDefaultEmojiStyle()) {
			setFilters(appendEmojiFilter(this.getFilters()));
		}
	}

	/**
	 * Add our EmojiFilter as the first item to the array of existing InputFilters
	 * @param originalFilters
	 * @return Array of filters
	 */
	private InputFilter[] appendEmojiFilter(@Nullable InputFilter[] originalFilters) {
		InputFilter[] result;

		if (originalFilters != null) {
			result = new InputFilter[originalFilters.length + 1];
			System.arraycopy(originalFilters, 0, result, 1, originalFilters.length);
		} else {
			result = new InputFilter[1];
		}
		result[0] = new EmojiFilter(this);

		return result;
	}

	/**
	 * Add single emoji at the current cursor position
	 * @param emojiCodeString
	 */
	public void addEmoji(String emojiCodeString) {
		final int start = getSelectionStart();
		final int end = getSelectionEnd();

		// fix reverse selections
		getText().replace(Math.min(start, end), Math.max(start, end), emojiCodeString);
		setSelection(start + emojiCodeString.length());
	}

	/**
	 * Callback called by invalidateSelf of EmojiDrawable
	 * @param drawable
	 */
	@Override
	public void invalidateDrawable(@NonNull Drawable drawable) {
		if (drawable instanceof EmojiDrawable) {
			/* setHint() invalidates the view while invalidate() does not */
			setHint(this.hint);
		} else {
			super.invalidateDrawable(drawable);
		}
	}

	/**
	 * Limit input size to maxByteSize by not allowing any input that exceeds the value thus keeping multi-byte characters intact
	 * @param maxByteSize Maximum input size in byte
	 */
	public void setMaxByteSize(int maxByteSize) {
		removeTextChangedListener(textLengthWatcher);
		if (maxByteSize > 0) {
			addTextChangedListener(textLengthWatcher);
		}
		this.maxByteSize = maxByteSize;
	}


	private final TextWatcher textLengthWatcher = new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) { }

		@Override
		public void afterTextChanged(Editable s) {
			if (s != null) {
				String text = s.toString();
				String cropped = Utils.truncateUTF8String(text, maxByteSize);

				if (!TestUtil.compare(text, cropped == null ? "" : cropped)) {
					setText(currentText);
					setSelection(currentText.length());
				} else {
					currentText = text;
				}
			}
		}
	};
}
