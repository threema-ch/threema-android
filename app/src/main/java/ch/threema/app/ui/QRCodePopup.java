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
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.PopupWindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.utils.AnimationUtil;


public class QRCodePopup extends DimmingPopupWindow implements DefaultLifecycleObserver {
	private static final Logger logger = LoggerFactory.getLogger(QRCodePopup.class);

	private ImageView imageView;
	private View topLayout;
	private View parentView;

	private final int[] location = new int[2];
	private View iconBorder, iconImage;

	public QRCodePopup(Context context, View parentView, LifecycleOwner lifecycleOwner) {
		super(context);

		if (lifecycleOwner != null) {
			lifecycleOwner.getLifecycle().addObserver(this);
		}

		init(context, parentView, parentView.getWidth(), parentView.getHeight());
	}

	private void init(Context context, View parentView, int screenWidth, int screenHeight) {
		this.parentView = parentView;

		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		topLayout =  layoutInflater.inflate(R.layout.popup_qrcode, null, true);

		this.imageView = topLayout.findViewById(R.id.image_view);
		this.iconBorder = topLayout.findViewById(R.id.icon_border);
		this.iconImage = topLayout.findViewById(R.id.icon_image);

		// border around popup contents
		int borderSize = context.getResources().getDimensionPixelSize(R.dimen.qrcode_min_margin) * 2;

		setContentView(topLayout);

		if (screenHeight > screenWidth) {
			// portrait
			setWidth(screenWidth - borderSize);
			setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
		} else {
			// landscape
			setWidth(screenHeight - borderSize);
			setHeight(screenHeight - borderSize);
		}
		setBackgroundDrawable(new BitmapDrawable());
		setAnimationStyle(0);
		setElevation(10);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
	}

	/**
	 * Show a popup with a QR code
	 * @param sourceView starting point for animation
	 * @param text text to display as QR code
	 */
	public void show(@NonNull final View sourceView, String text) {
		Bitmap bitmap;

		if (text != null) {
			bitmap = ThreemaApplication.getServiceManager().getQRCodeService().getRawQR(text, true);
			this.iconBorder.setVisibility(View.GONE);
			this.iconImage.setVisibility(View.GONE);
		} else {
			bitmap = ThreemaApplication.getServiceManager().getQRCodeService().getUserQRCode();
		}

		if (bitmap == null) {
			logger.debug("Unable to get qr code bitmap");
			return;
		}

		final BitmapDrawable bitmapDrawable = new BitmapDrawable(getContext().getResources(), bitmap);
		bitmapDrawable.setFilterBitmap(false);

		this.imageView.setImageDrawable(bitmapDrawable);
		showAtLocation(parentView, Gravity.CENTER, 0, 0);
		dimBackground();

		getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);

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
				QRCodePopup.super.dismiss();
			}
		});
	}

	/**
	 * Notifies that {@code ON_PAUSE} event occurred.
	 * <p>
	 * This method will be called before the {@link LifecycleOwner}'s {@code onPause} method
	 * is called.
	 *
	 * @param owner the component, whose state was changed
	 */
	@Override
	public void onPause(@NonNull LifecycleOwner owner) {
		QRCodePopup.super.dismiss();
	}
}
