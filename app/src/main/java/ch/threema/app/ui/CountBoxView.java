/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2021 Threema GmbH
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
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;

import ch.threema.app.R;

public class CountBoxView extends androidx.appcompat.widget.AppCompatTextView {

	public CountBoxView(Context context) {
		super(context);
		init(context, null);
	}

	public CountBoxView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public CountBoxView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	private void init(Context context, final AttributeSet attrs) {

		int paddingPx = context.getResources().getDimensionPixelSize(R.dimen.count_box_padding);
		float textSize = 0;
		int backgroundRes = R.drawable.count_box_background;

		if (attrs != null) {
			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CountBoxView);

			if (a != null) {
				final int N = a.getIndexCount();
				for (int i = 0; i < N; ++i) {
					int attr = a.getIndex(i);
					switch (attr) {
						case R.styleable.CountBoxView_textSizeOverride:
							textSize = a.getDimensionPixelSize(R.styleable.CountBoxView_textSizeOverride, 0);
							break;
						case R.styleable.CountBoxView_backgroundOverride:
						 	backgroundRes = a.getResourceId(R.styleable.CountBoxView_backgroundOverride, R.drawable.count_box_background);
							break;
						case R.styleable.CountBoxView_paddingOverride:
							paddingPx = a.getDimensionPixelSize(R.styleable.CountBoxView_paddingOverride, paddingPx);
							break;
						default:
							break;
					}
				}
				a.recycle();
			}
		}

		this.setPadding(paddingPx, 0, paddingPx, 0);
		this.setSingleLine(true);
		this.setEllipsize(TextUtils.TruncateAt.MARQUEE);
		if(!this.isInEditMode()) {
			if (textSize > 0) {
				this.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
			} else {
				this.setTextAppearance(context, android.R.style.TextAppearance_Small);
			}
		}
		this.setTypeface(Typeface.DEFAULT_BOLD);
		this.setTextColor(getResources().getColor(android.R.color.white));
		this.setBackgroundResource(backgroundRes);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if (getMeasuredWidth() < getMeasuredHeight()) {
			setMeasuredDimension(getMeasuredHeight(), getMeasuredHeight());
		}
	}
}
