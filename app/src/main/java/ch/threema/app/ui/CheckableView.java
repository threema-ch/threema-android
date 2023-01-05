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
import android.view.View;
import android.widget.Checkable;

public class CheckableView extends View implements Checkable {
	private boolean checked = false;
	private static final int[] CHECKED_STATE_SET = { android.R.attr.state_checked };

	public CheckableView(Context context) {
		super(context);
	}

	public CheckableView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CheckableView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public int[] onCreateDrawableState(int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (isChecked())
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		return drawableState;
	}

	@Override
	public boolean isChecked() {
		return checked;
	}

	@Override
	public void setChecked(boolean _checked) {
		checked = _checked;
		refreshDrawableState();
	}

	@Override
	public void toggle() {
		setChecked(!checked);
	}
}
