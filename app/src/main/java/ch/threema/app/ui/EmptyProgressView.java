/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2023 Threema GmbH
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

import ch.threema.app.R;

public class EmptyProgressView extends LinearLayout {
	public EmptyProgressView(Context context) {
		this(context, null, 0);
	}

	public EmptyProgressView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public EmptyProgressView(Context context, int parentOffset) {
		this(context, null, parentOffset);
	}

	public EmptyProgressView(Context context, AttributeSet attrs, int parentOffset) {
		super(context, attrs);

		setOrientation(LinearLayout.VERTICAL);
		setGravity(Gravity.CENTER);
		int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics());
		setPadding(paddingPx, parentOffset, paddingPx, 0);
		setLayoutParams(new ListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));

		LayoutInflater.from(context).inflate(R.layout.view_empty_progress, this, true);
		setVisibility(View.GONE);
	}
}
