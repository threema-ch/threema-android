/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2024 Threema GmbH
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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import ch.threema.app.R;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.utils.LoggingUtil;

public class ShutterButtonView extends AppCompatImageView {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ShutterButtonView");
	private static final float DEAD_ZONE_HEIGHT = 0.05F;

	private ShutterButtonListener shutterButtonListener;
	private final Interpolator decelerateInterpolator = new DecelerateInterpolator();
	private float previousFactor = 0f;
	private int[] locationInWindow = new int[2];
	private boolean videoEnable = false;
	private final Object recordingLock = new Object();
	private long recordingStartTime;
	private GestureDetector gestureDetector;

	private boolean isRecording;

	public ShutterButtonView(@NonNull Context context) {
		this(context, null);
	}

	public ShutterButtonView(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ShutterButtonView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		setImageResource(R.drawable.ic_shutter_button_normal);

		gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				logger.debug("onSingleTapUp");

				onClick();

				return true;
			}

			@Override
			public void onLongPress(MotionEvent e) {
				logger.debug("onLongPress");

				previousFactor = 0F;
				startRecording();
			}


		});
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		getLocationInWindow(this.locationInWindow);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		logger.debug("onAttachedToWindow");

		getParent().requestDisallowInterceptTouchEvent(true);
	}

	@Override
	protected void onDetachedFromWindow() {
		getParent().requestDisallowInterceptTouchEvent(false);

		logger.debug("onDetachedFromWindow");

		super.onDetachedFromWindow();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!gestureDetector.onTouchEvent(event)) {
			int action = event.getAction();
			switch (action) {
				case MotionEvent.ACTION_MOVE:
					synchronized (recordingLock) {
						if (isRecording) {
							if (event.getY() < 0) {
								float factor = Math.abs(event.getY()) / (this.locationInWindow[1] * (1F - DEAD_ZONE_HEIGHT));

								if (factor > 1F) {
									factor = 1F;
								}

								// prevent touchscreen noise
								if (Math.abs(previousFactor - factor) > 0.001) {
									previousFactor = factor;
									changeZoomFactor(decelerateInterpolator.getInterpolation(factor));
								}
							} else {
								if (previousFactor != 0) {
									previousFactor = 0;
									changeZoomFactor(0);
								}
							}
						}
					}
					break;
				case MotionEvent.ACTION_DOWN:
					logger.debug("ACTION_DOWN");
					break;
				case MotionEvent.ACTION_CANCEL:
					logger.debug("ACTION_CANCEL");
					// fallthrough
				case MotionEvent.ACTION_UP:
					logger.debug("ACTION_UP");
					stopRecording();
					break;
			}
			return true;
		}
		return super.onTouchEvent(event);
	}

	public void reset() {
		synchronized (recordingLock) {
			isRecording = false;
			setImageResource(R.drawable.ic_shutter_button_normal);
		}
	}

	public void setVideoEnable(boolean enable) {
		videoEnable = enable;
	}

	public void setShutterButtonListener(@Nullable ShutterButtonListener shutterButtonListener) {
		synchronized (recordingLock) {
			if (!isRecording) {
				this.shutterButtonListener = shutterButtonListener;
			}
		}
	}

	private void startRecording() {
		if (videoEnable) {
			synchronized (recordingLock) {
				if (!isRecording) {
					setImageResource(R.drawable.ic_shutter_button_recording);
					isRecording = true;
					shutterButtonListener.onRecordStart();
					recordingStartTime = System.currentTimeMillis();
				}
			}
		}
	}

	private void stopRecording() {
		if (videoEnable) {
			synchronized (recordingLock) {
				if (isRecording) {
					long recordingLength = System.currentTimeMillis() - recordingStartTime;

					// record at least 1 second to avoid race conditions in camerax code
					if (recordingLength < DateUtils.SECOND_IN_MILLIS) {
						setImageResource(R.drawable.ic_shutter_button_normal);
						new Handler().postDelayed(new Runnable() {
							@Override
							public void run() {
								RuntimeUtil.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										endRecording();
									}
								});
							}
						}, DateUtils.SECOND_IN_MILLIS - recordingLength);
					} else {
						endRecording();
					}
				}
			}
		}
	}

	private void endRecording() {
		shutterButtonListener.onRecordEnd();
		reset();
	}

	private void changeZoomFactor(final float factor) {
		if (videoEnable) {
			synchronized (recordingLock) {
				if (isRecording) {
					shutterButtonListener.onZoomChanged(factor < 0 ? 0 : factor);
				}
			}
		}
	}

	private void onClick() {
		if (!isRecording) {
			shutterButtonListener.onClick();
			performClick();
		} else {
			stopRecording();
		}
	}

	/**
	 * Simulate a button click, including a small delay while it is being pressed to trigger the
	 * appropriate animations.
	 */
	public void simulateClick() {
		onClick();
		setPressed(true);
		invalidate();
		postDelayed(() -> {
			invalidate();
			setPressed(false);
		}, 50);
	}

	interface ShutterButtonListener {
		void onRecordStart();
		void onRecordEnd();
		void onZoomChanged(float zoomFactor);
		void onClick();
	}
}
