/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.R;

public class DirectoryHeaderItemDecoration extends RecyclerView.ItemDecoration {

	private final int             headerOffset;
	private final boolean         sticky;
	private final HeaderCallback sectionCallback;

	private View headerView;
	private TextView header;

	public DirectoryHeaderItemDecoration(int headerHeight, boolean sticky, @NonNull HeaderCallback sectionCallback) {
		headerOffset = headerHeight;
		this.sticky = sticky;
		this.sectionCallback = sectionCallback;
	}

	@Override
	public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		super.getItemOffsets(outRect, view, parent, state);

		int pos = parent.getChildAdapterPosition(view);
		if (sectionCallback.isHeader(pos)) {
			outRect.top = headerOffset;
		}
	}

	@Override
	public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
		super.onDrawOver(c, parent, state);

		if (headerView == null) {
			headerView = inflateHeaderView(parent);
			header = (TextView) headerView.findViewById(R.id.list_item_section_text);
			fixLayoutSize(headerView, parent);
		}

		CharSequence previousHeader = "";
		for (int i = 0; i < parent.getChildCount(); i++) {
			View child = parent.getChildAt(i);
			final int position = parent.getChildAdapterPosition(child);

			CharSequence title = sectionCallback.getHeaderText(position);
			header.setText(title);
			if (!previousHeader.equals(title) || sectionCallback.isHeader(position)) {
				drawHeader(c, child, headerView);
				previousHeader = title;
			}
		}
	}

	private void drawHeader(Canvas c, View child, View headerView) {
		c.save();
		if (sticky) {
			c.translate(0, Math.max(0, child.getTop() - headerView.getHeight()));
		} else {
			c.translate(0, child.getTop() - headerView.getHeight());
		}
		headerView.draw(c);
		c.restore();
	}

	private View inflateHeaderView(RecyclerView parent) {
		return LayoutInflater.from(parent.getContext())
			.inflate(R.layout.item_directory_header, parent, false);
	}

	/**
	 * Measures the header view to make sure its size is greater than 0 and will be drawn
	 * https://yoda.entelect.co.za/view/9627/how-to-android-recyclerview-item-decorations
	 */
	private void fixLayoutSize(View view, ViewGroup parent) {
		int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(),
			View.MeasureSpec.EXACTLY);
		int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(),
			View.MeasureSpec.UNSPECIFIED);

		int childWidth = ViewGroup.getChildMeasureSpec(widthSpec,
			parent.getPaddingLeft() + parent.getPaddingRight(),
			view.getLayoutParams().width);
		int childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
			parent.getPaddingTop() + parent.getPaddingBottom(),
			view.getLayoutParams().height);

		view.measure(childWidth, childHeight);

		view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
	}

	public interface HeaderCallback {
		boolean isHeader(int position);
		CharSequence getHeaderText(int position);
	}
}
