/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2020 Threema GmbH
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
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;

import androidx.core.content.res.ResourcesCompat;
import ch.threema.app.R;
import ch.threema.app.emojis.EmojiTextView;

public class TextViewRobotoMedium extends EmojiTextView {
	private Context context;

	public TextViewRobotoMedium(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.context = context;
		createFont();
	}

	public TextViewRobotoMedium(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		createFont();
	}

	public TextViewRobotoMedium(Context context) {
		super(context);
		this.context = context;
		createFont();
	}

	public void createFont() {
		Typeface font;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			font = ResourcesCompat.getFont(context, R.font.roboto_medium);
		} else {
			font = Typeface.create("sans-serif-medium", Typeface.NORMAL);
		}
		setTypeface(font);
	}

	@Override
	public void setTypeface(Typeface tf) {
		super.setTypeface(tf);
	}

}

