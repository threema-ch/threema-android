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

package ch.threema.app.ui;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.AbsListView;
import android.widget.GridView;

import androidx.appcompat.view.ContextThemeWrapper;
import ch.threema.app.R;
import ch.threema.app.utils.RuntimeUtil;

/**
 * This class fixes two bugs in the Android framework
 * - android:fastScrollStyle attribute ignored for GridViews
 * - android:fastScrollAlwaysVisible="false" never ever showing fastscroll
 */

public class FastScrollGridView extends GridView implements AbsListView.OnScrollListener {
	private ScrollListener scrollListener;
	private int lastFirstVisibleItem = -1;
	private final Handler fastScrollRemoveHandler = new Handler();
	private final Runnable fastScrollRemoveTask = () -> RuntimeUtil.runOnUiThread(() -> setFastScrollAlwaysVisible(false));

	public FastScrollGridView(Context context, AttributeSet attrs) {
		super(new ContextThemeWrapper(context, R.style.Threema_MediaGallery_FastScroll), attrs);
		setOnScrollListener(this);
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (scrollState == SCROLL_STATE_IDLE) {
			fastScrollRemoveHandler.removeCallbacks(fastScrollRemoveTask);
			fastScrollRemoveHandler.postDelayed(fastScrollRemoveTask, 1000);
		} else if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
			fastScrollRemoveHandler.removeCallbacks(fastScrollRemoveTask);
			setFastScrollAlwaysVisible(true);
		}
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (firstVisibleItem != this.lastFirstVisibleItem) {
			if (this.scrollListener != null) {
				this.scrollListener.onScroll(firstVisibleItem);
			}
			this.lastFirstVisibleItem = firstVisibleItem;
		}
	}

	public void setScrollListener(ScrollListener scrollListener) {
		this.scrollListener = scrollListener;
	}

	public interface ScrollListener {
		void onScroll(int firstVisibleItem);
	}
}
