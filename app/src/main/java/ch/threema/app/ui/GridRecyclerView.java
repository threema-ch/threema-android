/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2021 Threema GmbH
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
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.GridLayoutAnimationController;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class GridRecyclerView extends RecyclerView {

	public GridRecyclerView(Context context) { super(context); }

	public GridRecyclerView(Context context, AttributeSet attrs) { super(context, attrs); }

	public GridRecyclerView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }

	@Override
	protected void attachLayoutAnimationParameters(View child, ViewGroup.LayoutParams params,
	                                               int index, int count) {
		final LayoutManager layoutManager = getLayoutManager();
		if (getAdapter() != null && layoutManager instanceof GridLayoutManager){

			GridLayoutAnimationController.AnimationParameters animationParams =
				(GridLayoutAnimationController.AnimationParameters) params.layoutAnimationParameters;

			if (animationParams == null) {
				// If there are no animation parameters, create new once and attach them to
				// the LayoutParams.
				animationParams = new GridLayoutAnimationController.AnimationParameters();
				params.layoutAnimationParameters = animationParams;
			}

			// Next we are updating the parameters

			// Set the number of items in the RecyclerView and the index of this item
			animationParams.count = count;
			animationParams.index = index;

			// Calculate the number of columns and rows in the grid
			final int columns = ((GridLayoutManager) layoutManager).getSpanCount();
			animationParams.columnsCount = columns;
			animationParams.rowsCount = count / columns;

			// Calculate the column/row position in the grid
			final int invertedIndex = count - 1 - index;
			animationParams.column = columns - 1 - (invertedIndex % columns);
			animationParams.row = animationParams.rowsCount - 1 - invertedIndex / columns;

		} else {
			// Proceed as normal if using another type of LayoutManager
			super.attachLayoutAnimationParameters(child, params, index, count);
		}
	}
}
