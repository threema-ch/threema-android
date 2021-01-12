/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.emojis.EmojiTextView;
import ch.threema.app.utils.ConfigUtils;

public class TooltipPopup extends PopupWindow implements DefaultLifecycleObserver {

	private static final Logger logger = LoggerFactory.getLogger(TooltipPopup.class);

	public static final int ALIGN_ABOVE_ANCHOR_ARROW_LEFT = 1;
	public static final int ALIGN_BELOW_ANCHOR_ARROW_RIGHT = 2;
	public static final int ALIGN_BELOW_ANCHOR_ARROW_LEFT = 3;
	public static final int ALIGN_ABOVE_ANCHOR_ARROW_RIGHT = 4;

	private Context context;
	private EmojiTextView textView;
	private final String preferenceString;
	private Handler timeoutHandler;
	private Runnable dismissRunnable = () -> dismiss(false);

	public TooltipPopup(Context context, int preferenceKey, @LayoutRes int layoutResource, LifecycleOwner lifecycleOwner) {
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

		init(context, layoutResource, null);
	}

	public TooltipPopup(Context context, int preferenceKey, @LayoutRes int layoutResource, LifecycleOwner lifecycleOwner, Intent launchIntent) {
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

		init(context, layoutResource, launchIntent);
	}

	public TooltipPopup(Context context, String preferenceString, @LayoutRes int layoutResource) {
		super(context);
		this.preferenceString = preferenceString;
		this.context = context;

		if (isDismissed(context, preferenceString)) {
			return;
		}

		init(context, layoutResource, null);
	}

	private void init(Context context, int layoutResource, Intent launchIntent) {
		LayoutInflater layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout popupLayout = (LinearLayout) layoutInflater.inflate(layoutResource, null, false);

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

		ImageView closeButton = popupLayout.findViewById(R.id.close_button);
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

	public void show(Activity activity, final View anchor, String text, int align) {
		show(activity, anchor, text, align, 0);
	}

	public void show(Activity activity, final View anchor, String text, int align, int timeoutMs) {
		if (isDismissed(context, preferenceString)) {
			return;
		}

		int[] originLocation = {0, 0};
		anchor.getLocationInWindow(originLocation);

		show(activity, anchor, text, align, originLocation, timeoutMs);
	}

	public void show(Activity activity, final View anchor, String text, int align, int[] originLocation, int timeoutMs) {
		if (isDismissed(context, preferenceString)) {
			return;
		}

		int popupX;
		int popupY;

		this.textView.setText(text);

		int screenHeight = activity.getWindowManager().getDefaultDisplay().getHeight();
		int screenWidth = activity.getWindowManager().getDefaultDisplay().getWidth();
		int maxWidth = context.getResources().getDimensionPixelSize(R.dimen.tooltip_max_width);

		if (align == ALIGN_ABOVE_ANCHOR_ARROW_LEFT) {
			popupX = originLocation[0];
			popupY = screenHeight - originLocation[1] + ConfigUtils.getNavigationBarHeight(activity);
			int marginRight = context.getResources().getDimensionPixelSize(R.dimen.tooltip_margin_right);
			this.setWidth(Math.min(screenWidth - marginRight - popupX, maxWidth));
			if (activity.isFinishing() || activity.isDestroyed()) {
				return;
			}
			try {
				showAtLocation(anchor, Gravity.LEFT | Gravity.BOTTOM, popupX, popupY);
			} catch (WindowManager.BadTokenException e) {
				return;
			}
		} else if (align == ALIGN_ABOVE_ANCHOR_ARROW_RIGHT) {
			popupX = originLocation[0] + anchor.getWidth();
			popupY = screenHeight - originLocation[1] + ConfigUtils.getNavigationBarHeight(activity);
			int marginLeft = context.getResources().getDimensionPixelSize(R.dimen.tooltip_margin_right);
			int popupWidth = Math.min(popupX - marginLeft, maxWidth);
			this.setWidth(popupWidth);
			if (activity.isFinishing() || activity.isDestroyed()) {
				return;
			}
			try {
				showAtLocation(anchor, Gravity.LEFT | Gravity.BOTTOM, popupX - popupWidth, popupY);
			} catch (WindowManager.BadTokenException e) {
				return;
			}
		} else {
			int marginOnOtherEdge = context.getResources().getDimensionPixelSize(R.dimen.tooltip_margin_right);
			int arrowOffset = context.getResources().getDimensionPixelSize(R.dimen.tooltip_arrow_offset);
			int popupWidth;

			if (align == ALIGN_BELOW_ANCHOR_ARROW_LEFT) {
				popupX = originLocation[0] - arrowOffset;
				popupY = originLocation[1];
				popupWidth = Math.min(screenWidth - popupX - marginOnOtherEdge, maxWidth);
			} else {
				popupX = originLocation[0] + anchor.getWidth();
				popupY = originLocation[1] + anchor.getHeight();
//				popupWidth = Math.min(popupX - (screenWidth - popupX) - marginOnOtherEdge, maxWidth);
				popupWidth = Math.min(popupX + arrowOffset - marginOnOtherEdge, maxWidth);
			}
			this.setWidth(popupWidth);
			if (activity.isFinishing() || activity.isDestroyed()) {
				return;
			}
			try {
				if (align == ALIGN_BELOW_ANCHOR_ARROW_LEFT) {
					showAtLocation(anchor, Gravity.LEFT | Gravity.TOP, popupX, popupY);
				} else {
					showAtLocation(anchor, Gravity.LEFT | Gravity.TOP, popupX - popupWidth, popupY);
				}
			} catch (WindowManager.BadTokenException e) {
				return;
			}
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
