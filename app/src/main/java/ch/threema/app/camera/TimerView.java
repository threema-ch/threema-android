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

package ch.threema.app.camera;

import android.content.Context;
import android.os.Handler;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import ch.threema.app.R;
import ch.threema.app.ui.CircularProgressBar;
import ch.threema.app.utils.LocaleUtil;

public class TimerView extends FrameLayout {
	View parentView;
	TextView counterView;
	CircularProgressBar circularProgressBar;
	private Handler timeDisplayHandler = new Handler();
	private Runnable timeDisplayRunnable;
	private OnTimerExpiredListener timerExpiredListener;
	private long startTimeMs;
	private long maxTimeMs = 2 * DateUtils.MINUTE_IN_MILLIS;

	public TimerView(@NonNull Context context) {
		this(context, null);
	}

	public TimerView(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public TimerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context);
	}

	private void init(Context context) {
		LayoutInflater layoutInflater = LayoutInflater.from(context);
		parentView = layoutInflater.inflate(R.layout.view_timer, this, true);
		counterView = parentView.findViewById(R.id.counter);
		circularProgressBar = parentView.findViewById(R.id.progress_circular);

		ViewCompat.setOnApplyWindowInsetsListener(parentView, (v, insets) -> {
			ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
			lp.topMargin = insets.getSystemWindowInsetTop();
			v.setLayoutParams(lp);

			return insets;
		});

		setVisibility(GONE);
	}

	public void start(long durationMs, OnTimerExpiredListener listener) {
		startTimeMs = System.currentTimeMillis();
		maxTimeMs = durationMs - DateUtils.SECOND_IN_MILLIS; // deduct one second to compensate for timer delay
		timerExpiredListener = listener;

		timeDisplayRunnable = () -> {
			long elapsedTime = System.currentTimeMillis() - startTimeMs;
			if (elapsedTime > maxTimeMs) {
				stop();
				timerExpiredListener.onExpired(elapsedTime);
			} else {
				updateTimeDisplay(elapsedTime);
				timeDisplayHandler.postDelayed(timeDisplayRunnable, DateUtils.SECOND_IN_MILLIS);
			}
		};
		timeDisplayHandler.postDelayed(timeDisplayRunnable, DateUtils.SECOND_IN_MILLIS);

		setVisibility(VISIBLE);
	}

	public void stop() {
		timeDisplayHandler.removeCallbacksAndMessages(null);

		setVisibility(GONE);
	}

	private void updateTimeDisplay(long elapsedTime) {
		counterView.setText(LocaleUtil.formatTimerText(elapsedTime, false));
		circularProgressBar.setProgress(elapsedTime * 100 / maxTimeMs);
	}

	public interface OnTimerExpiredListener {
		/** Called when the timer has expired */
		void onExpired(long time);
	}
}
