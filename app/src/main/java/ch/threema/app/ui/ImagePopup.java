/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2023 Threema GmbH
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
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.utils.AnimationUtil;

public class ImagePopup extends DimmingPopupWindow {
	private ImageView imageView;
	private View topLayout;
	private View parentView;
	final int[] location = new int[2];

	public ImagePopup(Context context, @NonNull View parentView) {
		super(context);
		init(context, parentView, parentView.getWidth(), parentView.getHeight());
	}

	private void init(Context context, View parentView, int screenWidth, int screenHeight) {
		this.parentView = parentView;

		topLayout = LayoutInflater.from(context).inflate(R.layout.popup_image_nomargin, null, true);

		this.imageView = topLayout.findViewById(R.id.thumbnail_view);

		int borderSize = context.getResources().getDimensionPixelSize(R.dimen.image_popup_screen_border_width);
		setContentView(topLayout);

		if (screenHeight > screenWidth) {
			// portrait
			setWidth(screenWidth - borderSize);
			setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
		} else {
			// landscape
			setWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
			setHeight(screenHeight - borderSize);
		}
		setBackgroundDrawable(new BitmapDrawable());
		setAnimationStyle(0);
		setElevation(0);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
	}

	public void show(@NonNull final View sourceView, @NonNull Bitmap bitmap) {
		this.imageView.setImageBitmap(bitmap);

		showAtLocation(parentView, Gravity.CENTER, 0, 0);
		dimBackground();
		getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				getContentView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
				AnimationUtil.getViewCenter(sourceView, getContentView(), location);

				AnimationSet animation = new AnimationSet(true);
				Animation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, Animation.ABSOLUTE, location[0], Animation.ABSOLUTE, location[1]);
				Animation fade = new AlphaAnimation(0.0f, 1.0f);

				animation.addAnimation(scale);
				animation.addAnimation(fade);
				animation.setInterpolator(new DecelerateInterpolator());
				animation.setDuration(150);

				getContentView().startAnimation(animation);
			}
		});

		topLayout.setOnClickListener(v -> dismiss());
	}

	@Override
	public void dismiss() {
		AnimationUtil.popupAnimateOut(getContentView(), ImagePopup.super::dismiss);
	}
}
