/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2023 Threema GmbH
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
import ch.threema.app.emojis.EmojiMarkupUtil;
import ch.threema.app.emojis.MarkupParser;
import ch.threema.app.utils.ConfigUtils;

public class MarkupTextWatcher implements TextWatcher {
	private final EditText editText;
	@ColorInt private final int markerColor;
	private boolean afterMarkup = false, beforeMarkup = false;
	private final Pattern markupCharPattern;
	private final Context context;

	MarkupTextWatcher(Context context, EditText editor) {
		this.context = context;

		editText = editor;
		editText.addTextChangedListener(this);

		markerColor = ConfigUtils.getColorFromAttribute(context, R.attr.markup_marker_color);
		markupCharPattern = Pattern.compile(MarkupParser.MARKUP_CHAR_PATTERN);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		beforeMarkup = false;
		if (count >= after && count > 0) {
			// Text will be deleted or replaced: check also the adjacent characters for markup characters
			int from = Math.max(0, start - 1);
			int until = Math.min(s.length(), start + count + 1);
			beforeMarkup = markupCharPattern.matcher(TextUtils.substring(s, from, until)).matches();
		}
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		afterMarkup = false;
		if (count >= before && count > 0) {
			// Text has been added or replaced: check also the adjacent characters for markup characters
			int from = Math.max(0, start - 1);
			int until = Math.min(s.length(), start + count + 1);
			afterMarkup = markupCharPattern.matcher(TextUtils.substring(s, from, until)).matches();
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

			// Also update the mention markup for strikethrough changes
			EmojiMarkupUtil.getInstance().addMentionMarkup(context, s);
		}
	}
}
