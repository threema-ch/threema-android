/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2022 Threema GmbH
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
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

public class HintedImageView extends androidx.appcompat.widget.AppCompatImageView implements View.OnClickListener {

	private OnClickListener onClickListener;

	public HintedImageView(Context context) {
		super(context);

		setOnClickListener(this);
	}

	public HintedImageView(Context context, AttributeSet attrs) {
		super(context, attrs);

		setOnClickListener(this);
	}

	public HintedImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public void setOnClickListener(OnClickListener l) {
		if (l == this) {
			super.setOnClickListener(l);
			this.onClickListener = l;
		}
	}

	@Override
	public void onClick(View v) {
		if (this.onClickListener != null) {
			handleClick();
		}
	}

	private void handleClick() {
		if (getContentDescription() != null && getContext() != null) {
			String contentDesc = getContentDescription().toString();
			if (!TextUtils.isEmpty(contentDesc)) {
				int[] pos = new int[2];
				getLocationInWindow(pos);
				SingleToast.getInstance().text(contentDesc, Toast.LENGTH_SHORT, Gravity.TOP | Gravity.LEFT, pos[0] - ((contentDesc.length() / 2) * 12), pos[1] - 128);
			}
		}
	}
}
