/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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

package ch.threema.app.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.transition.Fade;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;
import ch.threema.app.R;

public class AnimationUtil {
	private static final Logger logger = LoggerFactory.getLogger(AnimationUtil.class);

	public static void expand(final View v) {
		expand(v, null);
	}

	public static void expand(final View v, final Runnable onFinishRunnable) {
		v.measure(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		final int targetHeight = v.getMeasuredHeight();

		// Older versions of android (pre API 21) cancel animations for views with a height of 0.
		v.getLayoutParams().height = 1;
		v.setVisibility(View.VISIBLE);
		Animation a = new Animation() {
			@Override
			protected void applyTransformation(float interpolatedTime, Transformation t) {
				v.getLayoutParams().height = interpolatedTime == 1
						? LinearLayout.LayoutParams.WRAP_CONTENT
						: (int) (targetHeight * interpolatedTime);
				v.requestLayout();
			}

			@Override
			public boolean willChangeBounds() {
				return true;
			}
		};

		// 2dp/ms
		a.setDuration((int) (targetHeight / v.getContext().getResources().getDisplayMetrics().density) * 2);
		if (onFinishRunnable != null) {
			a.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					onFinishRunnable.run();
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
				}
			});
		}
		v.startAnimation(a);
	}

	public static void collapse(final View v) {
		collapse(v, null);
	}

	public static void collapse(final View v, final Runnable onFinishRunnable) {
		final int initialHeight = v.getMeasuredHeight();

		Animation a = new Animation() {
			@Override
			protected void applyTransformation(float interpolatedTime, Transformation t) {
				if (interpolatedTime == 1) {
					v.setVisibility(View.GONE);
				} else {
					v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
					v.requestLayout();
				}
			}

			@Override
			public boolean willChangeBounds() {
				return true;
			}
		};

		// 2dp/ms
		a.setDuration((int) (initialHeight / v.getContext().getResources().getDisplayMetrics().density) * 2);

		if (onFinishRunnable != null) {
			a.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					onFinishRunnable.run();
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
				}
			});
		}
		v.startAnimation(a);
	}

	public static void startActivityForResult(Activity activity, View v, Intent intent, int requestCode) {
		logger.debug("start activity for result " + activity + " " + intent + " " + requestCode);
		if (activity != null) {
			ActivityOptionsCompat options = null;

			if (v != null) {
				intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				options = ActivityOptionsCompat.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight());
			}

			if (requestCode != 0) {
				if (options != null) {
					ActivityCompat.startActivityForResult(activity, intent, requestCode, options.toBundle());
				} else {
					activity.startActivityForResult(intent, requestCode);
					activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
				}
			} else {
				if (options != null) {
					ActivityCompat.startActivity(activity, intent, options.toBundle());
				} else {
					activity.startActivity(intent);
					activity.overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
				}
			}
		}
	}

	public static void startActivity(Activity activity, View v, Intent intent) {
		startActivityForResult(activity, v, intent, 0);
	}

	public static void setupTransitions(Context context, Window window) {
		// requestFeature() must be called before adding content
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && window != null && context != null) {
			android.transition.Transition fade = new android.transition.Fade();
			fade.excludeTarget(android.R.id.navigationBarBackground, true);

			window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);
			window.setEnterTransition(fade);
			window.setExitTransition(fade);

			window.setAllowEnterTransitionOverlap(true);
			window.setAllowReturnTransitionOverlap(true);
		}
	}

	public static void getViewCenter(View theView, View containerView, int[] location) {
		if (theView != null) {
			final int[] containerViewLocation = new int[2];

			theView.getLocationOnScreen(location);
			location[0] += theView.getWidth() / 2;
			location[1] += theView.getHeight() / 2;

			if (containerView != null) {
				containerView.getLocationOnScreen(containerViewLocation);
				location[0] -= containerViewLocation[0];
				location[1] -= containerViewLocation[1];
			}
		}
	}

	public static void circularReveal(View theLayout, int cx, int cy, boolean fromBottom) {
		circularReveal(theLayout, cx, cy, 300, fromBottom);
	}

	private static void circularReveal(View theLayout, int cx, int cy, int duration, boolean fromBottom) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !(cx == 0 && cy == 0)) {
			theLayout.setVisibility(View.INVISIBLE);

			theLayout.post(new Runnable() {
				@Override
				public void run() {
					int viewWidth = theLayout.getWidth();
					if (cx > (viewWidth / 2)) {
						viewWidth = cx;
					} else {
						viewWidth = viewWidth - cx;

					}
					int viewHeight = theLayout.getHeight();
					if (cy > (viewHeight / 2)) {
						viewHeight = cy;
					} else {
						viewHeight = viewHeight - cy;
					}

					float finalRadius = (float) Math.sqrt(viewWidth * viewWidth + viewHeight * viewHeight);
					try {
						Animator anim = ViewAnimationUtils.createCircularReveal(theLayout, cx, cy, 0, finalRadius);
						anim.setDuration(duration);

						// make the view visible and start the animation
						theLayout.setVisibility(View.VISIBLE);
						anim.start();
					} catch (IllegalStateException e) {
						theLayout.setVisibility(View.VISIBLE);
					}
				}
			});
		} else {
			slideInAnimation(theLayout, fromBottom, 250);
		}
	}

	public static void circularObscure(final View theLayout, int cx, int cy, boolean toBottom, final Runnable onFinishRunnable) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !(cx == 0 && cy == 0)) {
			int initialRadius = theLayout.getWidth();

			if (theLayout.isAttachedToWindow()) {
				Animator anim = ViewAnimationUtils.createCircularReveal(theLayout, cx, cy, initialRadius, 0);
				anim.setDuration(200);
				anim.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						super.onAnimationEnd(animation);
						theLayout.setVisibility(View.INVISIBLE);
						if (onFinishRunnable != null) {
							onFinishRunnable.run();
						}
					}
				});
				anim.start();
			}
		} else {
			slideOutAnimation(theLayout, toBottom, 1f, onFinishRunnable);
		}
	}

	public static void slideInFromBottomOvershoot(final View theLayout) {
		if (theLayout == null) return;

		AnimationSet animation = new AnimationSet(true);
		Animation slideUp = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 1.4f, Animation.RELATIVE_TO_SELF, 0f);
		animation.addAnimation(slideUp);
		animation.setFillAfter(true);
		animation.setInterpolator(new OvershootInterpolator(1f));
		animation.setDuration(350);
		theLayout.setVisibility(View.VISIBLE);
		theLayout.startAnimation(animation);
	}

	public static void slideInAnimation(final View theLayout, boolean fromBottom, int duration) {
		AnimationSet animation = new AnimationSet(true);
		Animation slideUp = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, fromBottom ? 1f : -1f, Animation.RELATIVE_TO_SELF, 0f);
		animation.addAnimation(slideUp);
		animation.setFillAfter(true);
		animation.setInterpolator(new DecelerateInterpolator());
		animation.setDuration(duration);
		theLayout.setVisibility(View.VISIBLE);
		theLayout.startAnimation(animation);
	}

	public static void slideOutAnimation(final View theLayout, boolean toBottom, float toValue, final Runnable onFinishRunnable) {
		AnimationSet animation = new AnimationSet(true);
		Animation slideDown = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, toBottom ? toValue : toValue * -1f);
		animation.addAnimation(slideDown);
		animation.setFillAfter(true);
		animation.setInterpolator(new AccelerateInterpolator());
		animation.setDuration(200);
		animation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {}

			@Override
			public void onAnimationEnd(Animation animation) {
				Handler handler = new Handler();
				handler.post(new Runnable() {
					@Override
					public void run() {
						if (onFinishRunnable != null) {
							onFinishRunnable.run();
						}
					}
				});
			}

			@Override
			public void onAnimationRepeat(Animation animation) {}
		});
		theLayout.setVisibility(View.INVISIBLE);
		theLayout.startAnimation(animation);
	}

	public static void zoomInAnimate(View view) {
		if (view.getVisibility() != View.VISIBLE) {
			view.setVisibility(View.VISIBLE);
			AnimationSet animation = new AnimationSet(true);
			Animation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

			animation.addAnimation(scale);
			animation.setInterpolator(new LinearInterpolator());
			animation.setDuration(100);
			view.startAnimation(animation);
		}
	}

	public static void zoomOutAnimate(final View view) {
		AnimationSet animation = new AnimationSet(true);
		Animation scale = new ScaleAnimation(1.0f, 0.0f, 1.0f, 0.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

		animation.addAnimation(scale);
		animation.setInterpolator(new LinearInterpolator());
		animation.setDuration(100);
		animation.setAnimationListener(new Animation.AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {

			}

			@Override
			public void onAnimationEnd(Animation animation) {
				if (view != null && view.getVisibility() == View.VISIBLE)
					view.setVisibility(View.GONE);
			}

			@Override
			public void onAnimationRepeat(Animation animation) {

			}
		});
		view.startAnimation(animation);
	}

	public static void bubbleAnimate(View view, int delay) {
		AnimationSet animation = new AnimationSet(true);
		Animation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

		animation.addAnimation(scale);
		animation.setInterpolator(new OvershootInterpolator(1));
		animation.setDuration(300);
		animation.setStartOffset(delay);
		view.startAnimation(animation);
	}

	public static ObjectAnimator pulseAnimate(View view, final int delay) {
		ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view,
				PropertyValuesHolder.ofFloat("scaleX", 1.15f),
				PropertyValuesHolder.ofFloat("scaleY", 1.15f));
		animator.setDuration(200);
		animator.setRepeatMode(ObjectAnimator.REVERSE);
		animator.setRepeatCount(1);
		animator.setInterpolator(new FastOutSlowInInterpolator());
		animator.addListener(new AnimatorListenerAdapter(){
			@Override
			public void onAnimationEnd(final Animator animation) {
				new Handler().postDelayed(new Runnable() {
					@Override
					public void run() {
						animation.start();
					}
				}, delay);
			}
		});
		animator.start();
		return animator;
	}

	public static void popupAnimateIn(View view) {
		AnimationSet animation = new AnimationSet(true);
		Animation scale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		Animation fade = new AlphaAnimation(0.0f, 1.0f);

		animation.addAnimation(scale);
		animation.addAnimation(fade);
		animation.setInterpolator(new OvershootInterpolator(1));
		animation.setDuration(250);

		view.startAnimation(animation);
	}

	public static void popupAnimateOut(View view, final Runnable onFinishRunnable) {
		AnimationSet animation = new AnimationSet(true);
		Animation scale = new ScaleAnimation(1.0f, 0.0f, 1.0f, 0.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		Animation fade = new AlphaAnimation(1.0f, 0.0f);

		animation.addAnimation(scale);
		animation.addAnimation(fade);
		animation.setInterpolator(new AccelerateInterpolator());
		animation.setDuration(100);
		if (onFinishRunnable != null) {
			animation.setAnimationListener(new Animation.AnimationListener() {
				@Override
				public void onAnimationStart(Animation animation) {
				}

				@Override
				public void onAnimationEnd(Animation animation) {
					Handler handler = new Handler();
					handler.post(new Runnable() {
						@Override
						public void run() {
							onFinishRunnable.run();
						}
					});
				}

				@Override
				public void onAnimationRepeat(Animation animation) {
				}
			});
		}
		view.startAnimation(animation);
	}

	public static void fadeViewVisibility(final View view, final int visibility) {
		view.animate().cancel();
		view.animate().setListener(null);

		if (visibility == View.VISIBLE) {
			view.animate().alpha(1f).start();
			view.setVisibility(View.VISIBLE);
		}
		else {
			view.animate().setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					view.setVisibility(visibility);
				}
			}).alpha(0f).start();
		}
	}


	public static void slideDown(Context context, View v){
		Animation a = AnimationUtils.loadAnimation(context, R.anim.slide_down);
		if (a != null){
			a.reset();
			if(v != null){
				v.clearAnimation();
				v.startAnimation(a);
			}
		}
	}

	public static void slideUp(Context context, View v){
		Animation a = AnimationUtils.loadAnimation(context, R.anim.slide_up);
		if (a != null){
			a.reset();
			if(v != null){
				v.clearAnimation();
				v.startAnimation(a);
			}
		}
	}

	/**
	 * Changes the visibility of a view by fading in or out
	 * @param view View to change visibility of
	 * @param visibility Visibility of the view after transition
	 */
	public static void setFadingVisibility(View view, int visibility) {
		if (view.getVisibility() != visibility) {
			Transition transition = new Fade();
			transition.setDuration(150);
			transition.addTarget(view);

			TransitionManager.endTransitions((ViewGroup) view.getParent());
			TransitionManager.beginDelayedTransition((ViewGroup) view.getParent(), transition);
			view.setVisibility(visibility);
		}
	}
}
