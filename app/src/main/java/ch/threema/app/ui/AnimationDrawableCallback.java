/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2022 Threema GmbH
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

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;

/**
 * Provides a callback when a non-looping {@link AnimationDrawable} completes its animation sequence. More precisely,
 * {@link #onAnimationCompleted()} is triggered when {@link View#invalidateDrawable(Drawable)} has been called on the
 * last frame.
 *
 * @author Benedict Lau
 */
public abstract class AnimationDrawableCallback implements Drawable.Callback {

    /**
     * The total number of frames in the {@link AnimationDrawable}.
     */
    private final int mTotalFrames;

    /**
     * The last frame of {@link Drawable} in the {@link AnimationDrawable}.
     */
    private final Drawable mLastFrame;

    /**
     * The current frame of {@link Drawable} in the {@link AnimationDrawable}.
     */
    private int mCurrentFrame = 0;

    /**
     * The client's {@link Callback} implementation. All calls are proxied to this wrapped {@link Callback}
     * implementation after intercepting the events we need.
     */
    private Drawable.Callback mWrappedCallback;

    /**
     * Flag to ensure that {@link #onAnimationCompleted()} is called only once, since
     * {@link #invalidateDrawable(Drawable)} may be called multiple times.
     */
    private boolean mIsCallbackTriggered = false;

    /**
     * Constructor.
     *
     * @param animationDrawable the {@link AnimationDrawable}.
     * @param callback          the client's {@link Callback} implementation. This is usually the {@link View} the has the
     *                          {@link AnimationDrawable} as background.
     */
    public AnimationDrawableCallback(AnimationDrawable animationDrawable, Drawable.Callback callback) {
        mTotalFrames = animationDrawable.getNumberOfFrames();
        mLastFrame = animationDrawable.getFrame(mTotalFrames - 1);
        mWrappedCallback = callback;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        if (mWrappedCallback != null) {
            mWrappedCallback.invalidateDrawable(who);
        }

        if (!mIsCallbackTriggered && mLastFrame != null && mLastFrame.equals(who.getCurrent())) {
            mIsCallbackTriggered = true;
            onAnimationCompleted();
        }
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        if (mWrappedCallback != null) {
            mWrappedCallback.scheduleDrawable(who, what, when);
        }

        onAnimationAdvanced(mCurrentFrame, mTotalFrames);
        mCurrentFrame++;
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        if (mWrappedCallback != null) {
            mWrappedCallback.unscheduleDrawable(who, what);
        }
    }

    //
    // Public methods.
    //

    /**
     * Callback triggered when a new frame of {@link Drawable} has been scheduled.
     *
     * @param currentFrame the current frame number.
     * @param totalFrames  the total number of frames in the {@link AnimationDrawable}.
     */
    public abstract void onAnimationAdvanced(int currentFrame, int totalFrames);

    /**
     * Callback triggered when {@link View#invalidateDrawable(Drawable)} has been called on the last frame, which marks
     * the end of a non-looping animation sequence.
     */
    public abstract void onAnimationCompleted();
}
