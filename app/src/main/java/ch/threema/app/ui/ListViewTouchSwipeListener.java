/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2023 Threema GmbH
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

/*
 * Heavily modified from source code Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.threema.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListView;

import org.slf4j.Logger;

import java.util.Timer;
import java.util.TimerTask;

import ch.threema.app.R;
import ch.threema.base.utils.LoggingUtil;

public class ListViewTouchSwipeListener implements View.OnTouchListener {
	private static final Logger logger = LoggingUtil.getThreemaLogger("ListViewSwipeListener");

	// Cached ViewConfiguration and system-wide constant values
	private int mSlop;
	private long mAnimationTime;

	// Fixed properties
	private ListView mListView;
	private DismissCallbacks mCallbacks;
	private int mViewWidth = 1; // 1 and not 0 to prevent dividing by zero

	// Transient properties
	private float mDownX;
	private float mDownY;
	private boolean mSwiping;
	private boolean mHasSwipeStarted = false;
	private int mSwipingSlop;
	private int mDownPosition;
	private View mDownView;
	private boolean mPaused;
	private ImageView quoteIcon;
	private Timer mLongPressTimer = null;

	public interface DismissCallbacks {
		boolean canSwipe(int position);
		void onSwiped(int position);
	}

	public ListViewTouchSwipeListener(ListView listView, DismissCallbacks callbacks) {
		ViewConfiguration vc = ViewConfiguration.get(listView.getContext());
		mSlop = vc.getScaledTouchSlop();
		mAnimationTime = listView.getContext().getResources().getInteger(
			android.R.integer.config_shortAnimTime);
		mListView = listView;
		mCallbacks = callbacks;
	}

	public void setEnabled(boolean enabled) {
		mPaused = !enabled;
	}

	public AbsListView.OnScrollListener makeScrollListener() {
		return new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView absListView, int scrollState) {
				setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
			}

			@Override
			public void onScroll(AbsListView absListView, int i, int i1, int i2) {
			}
		};
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouch(View view, MotionEvent motionEvent) {
		if (mViewWidth < 2) {
			mViewWidth = mListView.getWidth();
		}

		switch (motionEvent.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				logger.debug("*** ACTION_DOWN");

				mLongPressTimer = new Timer();
				mLongPressTimer.schedule(new AwaitLongPressTask(view), ViewConfiguration.getLongPressTimeout());

				if (mPaused) {
					return false;
				}

				// Find the child view that was touched (perform a hit test)
				Rect rect = new Rect();
				int childCount = mListView.getChildCount();
				int[] listViewCoords = new int[2];
				mListView.getLocationOnScreen(listViewCoords);
				int x = (int) motionEvent.getRawX() - listViewCoords[0];
				int y = (int) motionEvent.getRawY() - listViewCoords[1];
				View child;
				for (int i = 0; i < childCount; i++) {
					child = mListView.getChildAt(i);
					child.getHitRect(rect);
					if (rect.contains(x, y)) {
						mDownView = child;
						break;
					}
				}

				if (mDownView != null) {
					mDownX = motionEvent.getRawX();
					mDownY = motionEvent.getRawY();
					mDownPosition = mListView.getPositionForView(mDownView);
					if (!mCallbacks.canSwipe(mDownPosition)) {
						mDownView = null;
						return false;
					} else {
						/*
						quoteIcon = mDownView.findViewById(R.id.quote_icon);
						*/
						quoteIcon = null;

						View messageBlock = mDownView.findViewById(R.id.message_block);

						if (messageBlock != null) {
							mViewWidth = messageBlock.getWidth();
						}
					}
				} else {
					return false;
				}
				showRipple(view);
				return true;
			}

			case MotionEvent.ACTION_CANCEL: {
				logger.debug("*** ACTION_CANCEL");

				if (mDownView != null && mSwiping) {
					// cancel
					mDownView.animate()
						.translationX(0)
						.alpha(1)
						.setDuration(mAnimationTime)
						.setListener(null);
				}
				mLongPressTimer.cancel();
				resetSwipeStates();
				break;
			}

			case MotionEvent.ACTION_UP: {
				logger.debug("*** ACTION_UP");

				if (mDownView != null) {
					float deltaX = motionEvent.getRawX() - mDownX;
					if (Math.abs(deltaX) > mViewWidth / 4 && mSwiping && mDownPosition != ListView.INVALID_POSITION) {
						// ok
						final int downPosition = mDownPosition;
						mDownView.animate()
							.translationX(0)
							.setDuration(mAnimationTime)
							.setListener(new AnimatorListenerAdapter() {
								@Override
								public void onAnimationEnd(Animator animation) {
									mCallbacks.onSwiped(downPosition);
									mDownPosition = ListView.INVALID_POSITION;
								}
							});
					} else {
						// cancel
						mDownView.animate()
							.translationX(0)
							.setDuration(mAnimationTime)
							.setListener(null);

						if (!mHasSwipeStarted) {
							view.performClick();
						}
					}
					resetSwipeStates();
				}
				return false;
			}

			case MotionEvent.ACTION_MOVE: {
				logger.debug("*** ACTION_MOVE");

				if (mPaused || mDownView == null) {
					break;
				}

				float deltaX = motionEvent.getRawX() - mDownX;
				float deltaY = motionEvent.getRawY() - mDownY;

				if (deltaX < 0) {
					deltaX = 0;
				}

				if (deltaX > mSlop && Math.abs(deltaY) < Math.abs(deltaX) / 2) {
					mSwiping = true;
					mSwipingSlop = mSlop;
					mListView.requestDisallowInterceptTouchEvent(true);

					if (deltaX > 0) {
						setQuoteIconVisibility(View.VISIBLE);
					}

					// Cancel ListView's touch (un-highlighting the item)
					MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
					cancelEvent.setAction(MotionEvent.ACTION_CANCEL |
						(motionEvent.getActionIndex()
							<< MotionEvent.ACTION_POINTER_INDEX_SHIFT));
					mListView.onTouchEvent(cancelEvent);
					cancelEvent.recycle();
				}

				if (mSwiping) {
					mLongPressTimer.cancel();
					mHasSwipeStarted = true;
					mDownView.setTranslationX(deltaX - mSwipingSlop);
				}
				return true;
			}
		}
		return false;
	}

	private void setQuoteIconVisibility(int visibility) {
		if (quoteIcon != null) {
			if (quoteIcon.getVisibility() != visibility) {
				if (visibility == View.VISIBLE) {
					quoteIcon.setVisibility(View.VISIBLE);
					ObjectAnimator.ofFloat(quoteIcon, View.ALPHA, 0.2f, 1.0f).setDuration(300).start();
				} else {
					quoteIcon.setVisibility(View.GONE);
				}
			}
		}
	}

	private void resetSwipeStates() {
		mDownX = 0;
		mDownY = 0;
		mDownView = null;
		mDownPosition = ListView.INVALID_POSITION;
		mSwiping = false;
		mHasSwipeStarted = false;
		setQuoteIconVisibility(View.GONE);
	}

	private void showRipple(View view) {
		view.setPressed(true);
		view.setPressed(false);
	}

	private class AwaitLongPressTask extends TimerTask {
		private final Handler handler = new Handler();
		private final View view;

		public AwaitLongPressTask(View view) {
			this.view = view;
		}

		@Override
		public void run() {
			handler.post(() -> {
				if (mDownView != null) {
					view.performLongClick();
				}
			});
			mLongPressTimer.cancel();
		}
	}
}
