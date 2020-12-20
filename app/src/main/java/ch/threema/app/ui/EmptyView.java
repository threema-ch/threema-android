/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2020 Threema GmbH
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
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import ch.threema.app.R;

public class EmptyView extends LinearLayout {
	private TextView emptyText;

	public EmptyView(Context context) {
		this(context, null, 0);
	}

	public EmptyView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmptyView(Context context, int parentOffset) {
		this(context, null, parentOffset);
	}

	public EmptyView(Context context, AttributeSet attrs, int parentOffset) {
		super(context, attrs);

		setOrientation(LinearLayout.VERTICAL);
		setGravity(Gravity.CENTER);
		int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics());
		setPadding(paddingPx, parentOffset, paddingPx, 0);
		setLayoutParams(new ListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));

		LayoutInflater.from(context).inflate(R.layout.view_empty, this, true);
		setVisibility(View.GONE);

		this.emptyText = (TextView) getChildAt(0);
	}

	public void setup(int label) {
		this.emptyText.setText(label);
	}

	public void setup(String label) {
		this.emptyText.setText(label);
	}

	public void setColors(@ColorRes int background, @ColorRes int foreground) {
		this.setBackgroundColor(getResources().getColor(background));
		this.emptyText.setTextColor(getResources().getColor(foreground));
	}

	public void setColorsInt(@ColorInt int background, @ColorInt int foreground) {
		this.setBackgroundColor(background);
		this.emptyText.setTextColor(foreground);
	}
}
