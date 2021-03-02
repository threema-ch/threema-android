/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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
import android.view.MotionEvent;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import ch.threema.app.R;

public class LockingSwipeRefreshLayout extends SwipeRefreshLayout {
	private int tolerancePx;

	/**
	 * Prevents SwipeRefreshLayout from activating when fastscroll handle is touched
	 */

	public LockingSwipeRefreshLayout(Context context) {
		super(context);

		init(context);
	}

	public LockingSwipeRefreshLayout(Context context, AttributeSet attrs) {
		super(context, attrs);

		init(context);
	}

	private void init(Context context) {
		tolerancePx = context.getResources().getDimensionPixelSize(R.dimen.contacts_scrollbar_tolerance);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			if (event.getX() > this.getWidth() - tolerancePx) {
				return false;
			}
		}
		return super.onInterceptTouchEvent(event);
	}
}
