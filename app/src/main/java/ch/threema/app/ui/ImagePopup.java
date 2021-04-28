/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import ch.threema.app.R;
import ch.threema.app.utils.AnimationUtil;

public class ImagePopup extends DimmingPopupWindow {
	private static final Logger logger = LoggerFactory.getLogger(ImagePopup.class);

	private ImageView imageView;
	private TextView filenameTextView, dateTextView;
	private View topLayout;
	private View parentView;

	final int[] location = new int[2];


	public ImagePopup(Context context, View parentView, int screenWidth, int screenHeight) {
		super(context);
		init(context, parentView, screenWidth, screenHeight, 0, 0);
	}

	public ImagePopup(Context context, View parentView, int screenWidth, int screenHeight, int innerBorder) {
		super(context);
		init(context, parentView, screenWidth, screenHeight, innerBorder, 0);
	}

	public ImagePopup(Context context, @NonNull View parentView, @LayoutRes int layout) {
		super(context);
		init(context, parentView, parentView.getWidth(), parentView.getHeight(), 0, layout);
	}

	private void init(Context context, View parentView, int screenWidth, int screenHeight, int innerBorder, @LayoutRes int layout) {
		this.parentView = parentView;

		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (layout == 0) {
			topLayout = layoutInflater.inflate(R.layout.popup_image, null, true);
		} else {
			topLayout = layoutInflater.inflate(layout, null, true);
		}

		this.imageView = topLayout.findViewById(R.id.image_view);
		this.filenameTextView = topLayout.findViewById(R.id.filename_view);
		this.dateTextView = topLayout.findViewById(R.id.date_view);

		int borderSize = context.getResources().getDimensionPixelSize(R.dimen.image_popup_screen_border_width);
		setContentView(topLayout);

		if (innerBorder != 0) {
			ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) imageView.getLayoutParams();
			marginParams.setMargins(innerBorder, innerBorder, innerBorder, innerBorder);
			imageView.setLayoutParams(marginParams);

			marginParams = (ViewGroup.MarginLayoutParams) filenameTextView.getLayoutParams();
			marginParams.setMargins(innerBorder, innerBorder -
					context.getResources().getDimensionPixelSize(R.dimen.image_popup_text_size) -
					context.getResources().getDimensionPixelSize(R.dimen.image_popup_text_margin_bottom), 0, 0);
			filenameTextView.setLayoutParams(marginParams);
		}

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
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !(this.topLayout instanceof MaterialCardView)) {
			setElevation(10);
		}
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
	}

	public void show(final View view, Bitmap bitmap, String title) {
		this.imageView.setImageBitmap(bitmap);
		show(view, title, true);
	}

	public void show(final View view, BitmapDrawable bitmapDrawable, String title, boolean animated) {
		this.imageView.setImageDrawable(bitmapDrawable);
		show(view, title, animated);
	}

	private void show(final View view, String title, final boolean animated) {
		if (this.filenameTextView != null) {
			this.filenameTextView.setText(title != null ? title : "");
		}
		if (this.dateTextView != null) {
			this.dateTextView.setText("");
		}

		showAtLocation(parentView, Gravity.CENTER, 0, 0);
		dimBackground();
		if (animated) {
			getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);

					AnimationUtil.getViewCenter(view, getContentView(), location);
					AnimationUtil.popupAnimateIn(getContentView());
				}
			});
		}

		topLayout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
	}

	@Override
	public void dismiss() {
		AnimationUtil.popupAnimateOut(getContentView(), new Runnable() {
			@Override
			public void run() {
				ImagePopup.super.dismiss();
			}
		});
	}

	private Bitmap rotateBitmap(Bitmap source, float angle) {
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);
		return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
	}
}
