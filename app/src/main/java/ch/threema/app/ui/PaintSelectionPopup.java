/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.motionviews.widget.MotionEntity;
import ch.threema.app.utils.AnimationUtil;

public class PaintSelectionPopup extends PopupWindow {

	private final View removeView;
	private final View flipSeparator;
	private final View flipView;
	private final View bringToFrontSeparator;
	private final View bringToFrontView;
	private final View colorSeparator;
	private final View colorView;
	private final View parentView;
	private PaintSelectPopupListener paintSelectPopupListener;

	public PaintSelectionPopup(Context context, View parentView) {
		super(context);

		this.parentView = parentView;

		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		FrameLayout topLayout = (FrameLayout) layoutInflater.inflate(R.layout.popup_paint_selection, null, true);

		this.removeView = topLayout.findViewById(R.id.remove_paint);
		this.flipSeparator = topLayout.findViewById(R.id.flip_separator);
		this.flipView = topLayout.findViewById(R.id.flip_paint);
		this.bringToFrontSeparator = topLayout.findViewById(R.id.bring_to_front_separator);
		this.bringToFrontView = topLayout.findViewById(R.id.bring_to_front_paint);
		this.colorSeparator = topLayout.findViewById(R.id.color_separator);
		this.colorView = topLayout.findViewById(R.id.color_paint);

		setBackgroundDrawable(null);
		setContentView(topLayout);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
		setWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
		setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
		setAnimationStyle(0);
		setOutsideTouchable(false);
		setFocusable(true);
		setElevation(10);
	}

	public void show(int x, int y, @NonNull MotionEntity entity) {
		initRemoveView();

		if (entity.canMove()) {
			initFlipView();
			initBringToFrontView();
		} else {
			hideFlipView();
			hideBringToFrontView();
		}

		if (entity.canChangeColor()) {
			initColorView();
		} else {
			hideColorView();
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

				int animationDelay = 10;
				final int animationDelayStep = 100;

				if (removeView.getVisibility() == View.VISIBLE) {
					AnimationUtil.bubbleAnimate(removeView, animationDelay += animationDelayStep);
				}
				if (flipView.getVisibility() == View.VISIBLE) {
					AnimationUtil.bubbleAnimate(flipView, animationDelay += animationDelayStep);
				}
				if (bringToFrontView.getVisibility() == View.VISIBLE) {
					AnimationUtil.bubbleAnimate(bringToFrontView, animationDelay += animationDelayStep);
				}
				if (colorView.getVisibility() == View.VISIBLE) {
					AnimationUtil.bubbleAnimate(colorView, animationDelay + animationDelayStep);
				}
			}
		});
	}

	private void initRemoveView() {
		this.removeView.setVisibility(View.VISIBLE);
		this.removeView.setOnClickListener(v -> {
			paintSelectPopupListener.onRemoveClicked();
			dismiss();
		});
	}

	private void initFlipView() {
		this.flipView.setVisibility(View.VISIBLE);
		this.flipSeparator.setVisibility(View.VISIBLE);
		this.flipView.setOnClickListener(v -> {
			paintSelectPopupListener.onFlipClicked();
			dismiss();
		});
	}

	private void hideFlipView() {
		this.flipSeparator.setVisibility(View.GONE);
		this.flipView.setVisibility(View.GONE);
	}

	private void initBringToFrontView() {
		this.bringToFrontView.setVisibility(View.VISIBLE);
		this.bringToFrontSeparator.setVisibility(View.VISIBLE);
		this.bringToFrontView.setOnClickListener(v -> {
			paintSelectPopupListener.onBringToFrontClicked();
			dismiss();
		});
	}

	private void hideBringToFrontView() {
		this.bringToFrontSeparator.setVisibility(View.GONE);
		this.bringToFrontView.setVisibility(View.GONE);
	}

	private void initColorView() {
		this.colorView.setVisibility(View.VISIBLE);
		this.colorSeparator.setVisibility(View.VISIBLE);
		this.colorView.setOnClickListener(v -> {
			paintSelectPopupListener.onColorClicked();
			dismiss();
		});
	}

	private void hideColorView() {
		this.colorSeparator.setVisibility(View.GONE);
		this.colorView.setVisibility(View.GONE);
	}

	@Override
	public void dismiss() {
		if (this.paintSelectPopupListener != null) {
			this.paintSelectPopupListener.onClose();
		}

		AnimationUtil.popupAnimateOut(getContentView(), PaintSelectionPopup.super::dismiss);
	}

	public void setListener(PaintSelectPopupListener listener) {
		this.paintSelectPopupListener = listener;
	}

	public interface PaintSelectPopupListener {
		void onRemoveClicked();
		void onFlipClicked();
		void onBringToFrontClicked();
		void onColorClicked();
		void onOpen();
		void onClose();
	}
}
