/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2023 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.card.MaterialCardView;

import org.slf4j.Logger;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.services.QRCodeServiceImpl;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.base.utils.LoggingUtil;


public class QRCodePopup extends DimmingPopupWindow implements DefaultLifecycleObserver {
	private static final Logger logger = LoggingUtil.getThreemaLogger("QRCodePopup");

	private ImageView imageView;
	private View topLayout;
	private View parentView;
	private MaterialCardView containerView;

	private final int[] location = new int[2];

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

		this.containerView = topLayout.findViewById(R.id.qr_popup_container);
		this.imageView = topLayout.findViewById(R.id.image_view);

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
		setElevation(0);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
	}

	/**
	 * Show a popup with a QR code
	 * @param sourceView starting point for animation
	 * @param text text to display as QR code
	 * @param borderColor color to draw around the QR code (depending on type)
	 */
	public void show(@NonNull final View sourceView, String text, @QRCodeServiceImpl.QRCodeColor int borderColor) {
		Bitmap bitmap;

		if (text != null) {
			bitmap = ThreemaApplication.getServiceManager().getQRCodeService().getRawQR(text, true, borderColor);
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
		this.containerView.setStrokeColor(borderColor);
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
		AnimationUtil.popupAnimateOut(getContentView(), QRCodePopup.super::dismiss);
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
