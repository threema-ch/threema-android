/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import ch.threema.app.R;
import ch.threema.app.utils.AnimationUtil;

public class PaintSelectionPopup extends PopupWindow implements View.OnClickListener {

	public static final int TAG_REMOVE = 1;
	public static final int TAG_FLIP = 2;
	public static final int TAG_TO_FRONT = 3;
	private FrameLayout removeView, flipView, tofrontView;
	private View parentView;
	private PaintSelectPopupListener paintSelectPopupListener;

	private final int[] location = new int[2];

	public PaintSelectionPopup(Context context, View parentView) {
		super(context);

		this.parentView = parentView;

		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout topLayout = (LinearLayout) layoutInflater.inflate(R.layout.popup_paint_selection, null, true);

		this.removeView = topLayout.findViewById(R.id.remove_layout);
		this.flipView = topLayout.findViewById(R.id.flip_layout);
		this.tofrontView = topLayout.findViewById(R.id.tofront_layout);

		setContentView(topLayout);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
		setWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
		setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
		setAnimationStyle(0);
		setOutsideTouchable(false);
		setFocusable(true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			setElevation(10);
		}
	}

	public void show(int x, int y, boolean allowReordering) {
		this.removeView.setOnClickListener(this);
		this.removeView.setTag(TAG_REMOVE);

		if (allowReordering) {
			this.flipView.setVisibility(View.VISIBLE);
			this.flipView.setOnClickListener(this);
			this.flipView.setTag(TAG_FLIP);

			this.tofrontView.setVisibility(View.VISIBLE);
			this.tofrontView.setOnClickListener(this);
			this.tofrontView.setTag(TAG_TO_FRONT);
		} else {
			this.flipView.setVisibility(View.GONE);
			this.tofrontView.setVisibility(View.GONE);
		}

		if (this.paintSelectPopupListener != null) {
			this.paintSelectPopupListener.onOpen();
		}

		showAtLocation(parentView, Gravity.LEFT|Gravity.TOP, x, y);

		getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				getContentView().getViewTreeObserver().removeOnGlobalLayoutListener(this);

				AnimationUtil.popupAnimateIn(getContentView());
			}
		});
	}

	@Override
	public void dismiss() {
		if (this.paintSelectPopupListener != null) {
			this.paintSelectPopupListener.onClose();
		}

		AnimationUtil.popupAnimateOut(getContentView(), new Runnable() {
			@Override
			public void run() {
				PaintSelectionPopup.super.dismiss();
			}
		});

	}

	public void setListener(PaintSelectPopupListener listener) {
		this.paintSelectPopupListener = listener;
	}

	@Override
	public void onClick(View v) {
		if (paintSelectPopupListener != null) {
			paintSelectPopupListener.onItemSelected((int) v.getTag());
			dismiss();
		}
	}

	public interface PaintSelectPopupListener {
		void onItemSelected(int tag);
		void onOpen();
		void onClose();
	}
}
