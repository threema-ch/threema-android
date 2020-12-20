/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2020 Threema GmbH
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

package ch.threema.app.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.widget.EditText;

import java.util.regex.Pattern;

import androidx.annotation.ColorInt;
import ch.threema.app.R;
import ch.threema.app.emojis.MarkupParser;
import ch.threema.app.utils.ConfigUtils;

public class MarkupTextWatcher implements TextWatcher {
	private final EditText editText;
	@ColorInt private final int markerColor;
	private boolean afterMarkup = false, beforeMarkup = false;
	private final Pattern markupCharPattern;

	MarkupTextWatcher(Context context, EditText editor) {
		editText = editor;
		editText.addTextChangedListener(this);

		markerColor = ConfigUtils.getColorFromAttribute(context, R.attr.markup_marker_color);
		markupCharPattern = Pattern.compile(MarkupParser.MARKUP_CHAR_PATTERN);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		beforeMarkup = false;
		if (count >= after && count > 0) {
			// text will be deleted or replaced
			if (markupCharPattern.matcher(TextUtils.substring(s, start, start + count)).matches()) {
				beforeMarkup = true;
			} else {
				if (after == 0 && count == getCharCount(s, start) && start > 0) {
					// simply deleting a single character (count == getCharCount()), do not replace anything (after == 0) - check if previous character is relevant for markup
					if (markupCharPattern.matcher(TextUtils.substring(s, start - 1, start)).matches()) {
						beforeMarkup = true;
					}
				}
			}
		}
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		afterMarkup = false;
		if (count >= before && count > 0) {
			// text has been added or replaced
			if (markupCharPattern.matcher(TextUtils.substring(s, start, start + count)).matches()) {
				afterMarkup = true;
			} else {
				if (before == 0 && count == getCharCount(s, start) && start > 0) {
					// simply adding a single character (count == getCharCount()), do not replace anything (before == 0) - check if previous character is relevant for markup
					if (markupCharPattern.matcher(TextUtils.substring(s, start - 1, start)).matches()) {
						afterMarkup = true;
					}
				}
			}
		}
	}

	@Override
	public void afterTextChanged(Editable s) {
		if (beforeMarkup || afterMarkup) {
			Editable editableText = editText.getEditableText();

			// remove old spans
			Object[] spans = editableText.getSpans(0, s.length(), Object.class);
			for (Object span : spans) {
				if (span instanceof StyleSpan ||
						span instanceof StrikethroughSpan ||
						span instanceof ForegroundColorSpan) {
					editableText.removeSpan(span);
				}
			}

			MarkupParser.getInstance().markify(s, markerColor);
		}
	}

	/**
	 * Get number of characters at this position if it's a valid codepoint, otherwise 1 (some emojis might consist of more than one character)
	 * @param s input CharSequence
	 * @param start index of codepoint to check
	 * @return number of characters at this codepoint
	 */
	private int getCharCount(CharSequence s, int start) {
		final int codePoint = Character.codePointAt(s, start);
		if (Character.isValidCodePoint(codePoint)) {
			return Character.charCount(codePoint);
		}
		return 1;
	}
}
