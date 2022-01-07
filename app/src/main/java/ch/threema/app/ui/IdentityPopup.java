/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2022 Threema GmbH
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.google.android.material.chip.Chip;

import java.lang.ref.WeakReference;

import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.Group;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.AddContactActivity;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.QRCodeService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.ShareUtil;
import ch.threema.app.webclient.activities.SessionsActivity;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.SessionService;
import ch.threema.base.ThreemaException;

public class IdentityPopup extends DimmingPopupWindow {

	private Context context;
	private WeakReference<Activity> activityRef = new WeakReference<>(null);
	private ImageView qrCodeView;
	private QRCodeService qrCodeService;
	private SwitchCompat webEnableView;
	private SessionService sessionService;
	private int animationCenterX, animationCenterY;
	private ProfileButtonListener profileButtonListener;

	public IdentityPopup(Context context) {
		super(context);

		this.context = context;

		final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager == null) {
			dismiss();
			return;
		}

		final UserService userService = serviceManager.getUserService();
		if (userService == null) {
			dismiss();
			return;
		}

		qrCodeService = serviceManager.getQRCodeService();
		if (qrCodeService == null) {
			dismiss();
			return;
		}

		try {
			sessionService = serviceManager.getWebClientServiceManager().getSessionService();
		} catch (ThreemaException e) {
			dismiss();
			return;
		}

		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		FrameLayout popupLayout = (FrameLayout) layoutInflater.inflate(R.layout.popup_identity, null, false);

		TextView textView = popupLayout.findViewById(R.id.identity_label);
		this.qrCodeView = popupLayout.findViewById(R.id.qr_image);
		Group webControls = popupLayout.findViewById(R.id.web_controls);
		this.webEnableView = popupLayout.findViewById(R.id.web_enable);
		popupLayout.findViewById(R.id.web_label).setOnClickListener(v -> {
			Intent intent = new Intent(context, SessionsActivity.class);
			AnimationUtil.startActivity(activityRef.get(), v, intent);
			dismiss();
		});

		if (ConfigUtils.isOnPremBuild()) {
			popupLayout.findViewById(R.id.share_button).setVisibility(View.GONE);
		} else {
			popupLayout.findViewById(R.id.share_button).setOnClickListener(v -> ShareUtil.shareContact(activityRef.get(), null));
		}

		textView.setText(userService.getIdentity());
		textView.setContentDescription(context.getString(R.string.my_id) + " " + userService.getIdentity());

		setContentView(popupLayout);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
		setWidth(FrameLayout.LayoutParams.WRAP_CONTENT);
		setHeight(FrameLayout.LayoutParams.WRAP_CONTENT);
		setAnimationStyle(0);
		setFocusable(false);
		setTouchable(true);
		setOutsideTouchable(true);
		setBackgroundDrawable(new BitmapDrawable());

		popupLayout.setOnClickListener(v -> dismiss());

		Chip scanButton = popupLayout.findViewById(R.id.scan_button);
		scanButton.setOnClickListener(v -> scanQR());

		Chip profileButton = popupLayout.findViewById(R.id.profile_button);
		profileButton.setOnClickListener(v -> {
			dismiss();
			this.profileButtonListener.onClicked();
		});

		if (qrCodeView != null) {
			qrCodeView.setOnClickListener(v -> zoomQR(v));
		}

		if (webControls != null && webEnableView != null) {
			if (AppRestrictionUtil.isWebDisabled(context) || ConfigUtils.isBlackBerry()) {
				// Webclient is disabled, hide UI elements
				webEnableView.setEnabled(false);
				webControls.setVisibility(View.GONE);
			} else {
				webEnableView.setChecked(this.isWebClientEnabled());
				webEnableView.setOnCheckedChangeListener((buttonView, isChecked) -> startWebClient(isChecked));
			}
		}
	}

	private void scanQR() {
		if (ConfigUtils.supportsGroupLinks()) {
			QRScannerUtil.getInstance().initiateGeneralThreemaQrScanner(activityRef.get(), context.getString(R.string.qr_scanner_id_hint));
		} else {
			Intent intent = new Intent(context, AddContactActivity.class);
			intent.putExtra(AddContactActivity.EXTRA_ADD_BY_QR, true);
			if (activityRef.get() != null) {
				activityRef.get().startActivity(intent);
				activityRef.get().overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
			}
		}
	}

	private void zoomQR(View v) {
		new QRCodePopup(context, activityRef.get().getWindow().getDecorView(), null).show(v, null);
	}

	/**
	 *
	 * @param activity
	 * @param toolbarView Toolbar this popup will be aligned to
	 * @param location center location of navigation icon in toolbar
	 */
	public void show(Activity activity, final View toolbarView, int[] location, ProfileButtonListener profileButtonListener) {
		this.activityRef = new WeakReference<>(activity);
		this.profileButtonListener = profileButtonListener;

		int offsetY = activity.getResources().getDimensionPixelSize(R.dimen.navigation_icon_size) / 2;
		int offsetX = activity.getResources().getDimensionPixelSize(R.dimen.identity_popup_arrow_margin_left) + (activity.getResources().getDimensionPixelSize(R.dimen.identity_popup_arrow_width) / 2);

		animationCenterX = offsetX;
		animationCenterY = 0;

		Bitmap bitmap = qrCodeService.getUserQRCode();
		if (bitmap == null) {
			dismiss();
			return;
		}

		final BitmapDrawable bitmapDrawable = new BitmapDrawable(context.getResources(), bitmap);
		bitmapDrawable.setFilterBitmap(false);

		this.qrCodeView.setImageDrawable(bitmapDrawable);
		if (ConfigUtils.getAppTheme(context) == ConfigUtils.THEME_DARK) {
			ConfigUtils.invertColors(this.qrCodeView);
		}

		if (toolbarView != null) {
			int[] toolbarLocation = {0, 0};
			toolbarView.getLocationInWindow(toolbarLocation);

			if (activity.isFinishing() || activity.isDestroyed()) {
				return;
			}
			try {
				showAtLocation(toolbarView, Gravity.LEFT | Gravity.TOP, location[0] - offsetX, location[1] + offsetY);
			} catch (WindowManager.BadTokenException e) {
				//
			}

			dimBackground();

			getContentView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					getContentView().getViewTreeObserver().removeGlobalOnLayoutListener(this);
					AnimationUtil.circularReveal(getContentView(), animationCenterX, animationCenterY, false);
				}
			});
		}
		WebClientListenerManager.serviceListener.add(this.webClientServiceListener);
	}

	@Override
	public void dismiss() {
		if (isShowing()) {
			AnimationUtil.circularObscure(getContentView(), animationCenterX, animationCenterY, false, new Runnable() {
				@Override
				public void run() {
					IdentityPopup.super.dismiss();
				}
			});
		}
		WebClientListenerManager.serviceListener.remove(this.webClientServiceListener);
	}

	private final WebClientServiceListener webClientServiceListener = new WebClientServiceListener() {
		@Override
		public void onEnabled() {
			this.setEnabled(true);
		}

		@Override
		public void onDisabled() {
			this.setEnabled(false);
		}

		private void setEnabled(final boolean enabled) {
			RuntimeUtil.runOnUiThread(() -> {
				if (webEnableView != null) {
					webEnableView.setChecked(enabled);
				}
			});
		}
	};

	private boolean isWebClientEnabled() {
		return sessionService.isEnabled();
	}

	private void startWebClient(boolean start) {
		if(start && sessionService.getAllSessionModels().size() == 0) {
			context.startActivity(new Intent(context, SessionsActivity.class));
			dismiss();
		}
		else {
			sessionService.setEnabled(start);
		}
	}

	public interface ProfileButtonListener {
		void onClicked();
	}
}
