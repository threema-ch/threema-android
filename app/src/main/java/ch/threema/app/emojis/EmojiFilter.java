/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import android.text.InputFilter;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.EditText;

/**
 * InputFilter to replace Emojis in EditText
 */
public class EmojiFilter implements InputFilter {
	private EditText editText;

	public EmojiFilter(EditText editText) {
		this.editText = editText;
	}

	@Override
	public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
		Spannable spannable = (Spannable) EmojiMarkupUtil.getInstance().addTextSpans(editText.getContext(), source, editText, true);
		if (source instanceof Spanned && spannable != null) {
			TextUtils.copySpansFrom((Spanned) source, start, end, null, spannable, 0);
		}

		return spannable;
	}
}
