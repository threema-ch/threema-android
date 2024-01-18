/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2024 Threema GmbH
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
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;

import ch.threema.app.R;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.utils.ConfigUtils;

public class TooltipPopup extends PopupWindow implements DefaultLifecycleObserver {

	public static final int ALIGN_ABOVE_ANCHOR_ARROW_LEFT = 1;
	public static final int ALIGN_BELOW_ANCHOR_ARROW_RIGHT = 2;
	public static final int ALIGN_BELOW_ANCHOR_ARROW_LEFT = 3;
	public static final int ALIGN_ABOVE_ANCHOR_ARROW_RIGHT = 4;

	private final Context context;
	private View popupLayout;
	private EmojiTextView textView;
	private final String preferenceString;
	private Handler timeoutHandler;
	private final Runnable dismissRunnable = () -> dismiss(false);

	private @DrawableRes int icon = 0;

	public TooltipPopup(Context context, int preferenceKey, LifecycleOwner lifecycleOwner) {
		super(context);

		if (lifecycleOwner != null) {
			lifecycleOwner.getLifecycle().addObserver(this);
		}

		if (preferenceKey == 0) {
			this.preferenceString = null;
		} else {
			this.preferenceString = context.getString(preferenceKey);
		}
		this.context = context;

		init(context, null);
	}

	public TooltipPopup(Context context, int preferenceKey, LifecycleOwner lifecycleOwner, Intent launchIntent, @DrawableRes int icon) {
		super(context);

		if (lifecycleOwner != null) {
			lifecycleOwner.getLifecycle().addObserver(this);
		}

		if (preferenceKey == 0) {
			this.preferenceString = null;
		} else {
			this.preferenceString = context.getString(preferenceKey);
		}
		this.context = context;
		this.icon = icon;

		init(context, launchIntent);
	}

	private void init(Context context, Intent launchIntent) {
		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		this.popupLayout = layoutInflater.inflate(R.layout.popup_tooltip, null, false);
		this.textView = popupLayout.findViewById(R.id.label);

		setContentView(popupLayout);
		setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
		setAnimationStyle(R.style.TooltipAnimation);
		setFocusable(false);
		setTouchable(true);
		setOutsideTouchable(false);
		setBackgroundDrawable(new BitmapDrawable());

		popupLayout.setOnClickListener(v -> {
			if (launchIntent != null) {
				context.startActivity(launchIntent);
				if (context instanceof Activity) {
					((Activity) context).overridePendingTransition(0, 0);
				}
			} else {
				dismissForever();
			}
		});

		View closeButton = popupLayout.findViewById(R.id.close_button);
		if (closeButton != null) {
			if (preferenceString == null) {
				closeButton.setVisibility(View.GONE);
			} else {
				closeButton.setOnClickListener(v -> dismissForever());
			}
		}
	}

	public static boolean isDismissed(Context context, String preferenceString) {
		if (preferenceString == null) {
			return false;
		}

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		if (sharedPreferences != null) {
			return sharedPreferences.getBoolean(preferenceString, false);
		}
		return false;
	}

	public void dismissForever() {
		if (preferenceString != null) {
			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
			if (sharedPreferences != null) {
				sharedPreferences.edit().putBoolean(preferenceString, true).apply();
			}
		}

		dismiss(false);
	}

	public void dismiss(boolean immediate) {
		if (immediate) {
			setAnimationStyle(0);
		}

		if (timeoutHandler != null) {
			timeoutHandler.removeCallbacks(dismissRunnable);
			timeoutHandler = null;
		}

		this.dismiss();
	}

	/**
	 * Show a tooltip at the specified location pointing to a specified anchor view
	 * @param activity Activity context
	 * @param anchor Anchor / parent view to of this tooltip
	 * @param text Text to show in tooltip
	 * @param align Where to align the tooltip and where the arrow should be shown
	 * @param originLocation The location on screen where the tip of the arrow should point to
	 * @param timeoutMs How long the tooltip should be shown until it fades out
	 */
	public void show(Activity activity, final View anchor, String text, int align, int[] originLocation, int timeoutMs) {
		if (isDismissed(context, preferenceString)) {
			return;
		}

		this.textView.setText(text);

		int screenHeight = activity.getWindowManager().getDefaultDisplay().getHeight();
		int screenWidth = activity.getWindowManager().getDefaultDisplay().getWidth();
		int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.tooltip_max_width);
		int arrowInset = context.getResources().getDimensionPixelSize(R.dimen.tooltip_popup_arrow_inset);
		int marginOnOtherEdge = context.getResources().getDimensionPixelSize(R.dimen.tooltip_margin_on_other_edge);
		int arrowOffset = (context.getResources().getDimensionPixelSize(R.dimen.identity_popup_arrow_width) / 2) + arrowInset;
		int popupX, popupY, popupWidth, anchorGravity, contentGravity;

		if (align == ALIGN_ABOVE_ANCHOR_ARROW_LEFT) {
			this.popupLayout.findViewById(R.id.arrow_bottom_left).setVisibility(View.VISIBLE);
			popupX = Math.max(0, originLocation[0] - arrowOffset); // left edge of popup
			popupY = screenHeight - originLocation[1] + ConfigUtils.getNavigationBarHeight(activity);
			popupWidth = Math.min(screenWidth - popupX - marginOnOtherEdge, maxWidth);
			anchorGravity = Gravity.LEFT | Gravity.BOTTOM;
			contentGravity = Gravity.LEFT;
		} else if (align == ALIGN_ABOVE_ANCHOR_ARROW_RIGHT) {
			this.popupLayout.findViewById(R.id.arrow_bottom_right).setVisibility(View.VISIBLE);
			popupX = Math.min(screenWidth, originLocation[0] + arrowOffset);
			popupY = screenHeight - originLocation[1] + ConfigUtils.getNavigationBarHeight(activity);
			popupWidth = Math.min(popupX - marginOnOtherEdge, maxWidth);
			popupX -= popupWidth;
			anchorGravity = Gravity.LEFT | Gravity.BOTTOM;
			contentGravity = Gravity.RIGHT;
		} else if (align == ALIGN_BELOW_ANCHOR_ARROW_LEFT) {
			this.popupLayout.findViewById(R.id.arrow_top_left).setVisibility(View.VISIBLE);
			popupX = Math.max(0, originLocation[0] - arrowOffset); // left edge of popup
			popupY = originLocation[1];
			popupWidth = Math.min(screenWidth - popupX - marginOnOtherEdge, maxWidth);
			anchorGravity = Gravity.LEFT | Gravity.TOP;
			contentGravity = Gravity.LEFT;
		} else { // arrow right
			this.popupLayout.findViewById(R.id.arrow_top_right).setVisibility(View.VISIBLE);
			popupX = Math.min(screenWidth, originLocation[0] + arrowOffset); // right edge of popup
			popupY = originLocation[1];
			popupWidth = Math.min(popupX - marginOnOtherEdge, maxWidth);
			popupX -= popupWidth;
			anchorGravity = Gravity.LEFT | Gravity.TOP;
			contentGravity = Gravity.RIGHT;
		}

		this.setWidth(popupWidth);
		MaterialCardView contentLayout = this.popupLayout.findViewById(R.id.content);
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) contentLayout.getLayoutParams();
		params.gravity = contentGravity;
		contentLayout.setLayoutParams(params);

		if (activity.isFinishing() || activity.isDestroyed()) {
			return;
		}
		try {
			showAtLocation(anchor, anchorGravity, popupX, popupY);
		} catch (WindowManager.BadTokenException e) {
			return;
		}

		ImageView iconView = this.popupLayout.findViewById(R.id.icon);
		if (icon != 0) {
			iconView.setImageResource(icon);
			iconView.setVisibility(View.VISIBLE);
		} else {
			iconView.setVisibility(View.GONE);
		}

		if (timeoutMs > 0) {
			if (timeoutHandler == null) {
				timeoutHandler = new Handler();
			}
			timeoutHandler.removeCallbacks(dismissRunnable);
			timeoutHandler.postDelayed(dismissRunnable, timeoutMs);
		}
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
		dismiss(true);
	}
}
