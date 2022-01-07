/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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
import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;

import ch.threema.app.emojis.EmojiMarkupUtil;

public class MentionFilter implements InputFilter {
	Context context;

	public MentionFilter(Context context) {
		super();
		this.context = context;
	}

	@Override
	public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
		char[] buffer = new char[end - start];
		TextUtils.getChars(source, start, end, buffer, 0);

		String insertText = new String(buffer);

		Spannable spannable = (Spannable) EmojiMarkupUtil.getInstance().addMentionMarkup(context, insertText);
		if (source instanceof Spanned && spannable != null) {
			TextUtils.copySpansFrom((Spanned) source, start, end, null, spannable, 0);
		}

		return spannable;
	}
}
