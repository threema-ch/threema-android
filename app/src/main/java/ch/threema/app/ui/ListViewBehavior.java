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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class ListViewBehavior extends CoordinatorLayout.Behavior<View> {
	private static final Logger logger = LoggerFactory.getLogger(ListViewBehavior.class);

	public ListViewBehavior(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {

		return dependency instanceof Snackbar.SnackbarLayout;
	}

	@Override
	public boolean onDependentViewChanged(CoordinatorLayout parent, View child, View dependency) {
		logger.debug("onDependentViewChanged");

		ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
		layoutParams.height = parent.getHeight() - dependency.getHeight();
		child.setLayoutParams(layoutParams);

		return true;
	}

	@Override
	public void onDependentViewRemoved(CoordinatorLayout parent, View child, View dependency) {
		logger.debug("onDependentViewRemoved");

		ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
		layoutParams.height = MATCH_PARENT;
		child.setLayoutParams(layoutParams);
	}
}
