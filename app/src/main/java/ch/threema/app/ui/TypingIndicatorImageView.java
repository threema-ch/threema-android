package ch.threema.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;

public class TypingIndicatorImageView extends androidx.appcompat.widget.AppCompatImageView {
    private AnimatedVectorDrawableCompat animatedVectorDrawableCompat;

    public TypingIndicatorImageView(Context context) {
        super(context);
        init();
    }

    public TypingIndicatorImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TypingIndicatorImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        animatedVectorDrawableCompat = AnimatedVectorDrawableCompat.create(getContext(), R.drawable.typing_indicator);
        animatedVectorDrawableCompat.setTint(ConfigUtils.isTheDarkSide(getContext()) ? Color.WHITE : Color.BLACK);
        setImageDrawable(animatedVectorDrawableCompat);
    }

    private void startAnimation() {
        if (animatedVectorDrawableCompat != null) {
            animatedVectorDrawableCompat.registerAnimationCallback(new Animatable2Compat.AnimationCallback() {
                @Override
                public void onAnimationEnd(Drawable drawable) {
                    post(() -> animatedVectorDrawableCompat.start());
                }
            });
            animatedVectorDrawableCompat.start();
        }
    }

    private void stopAnimation() {
        if (animatedVectorDrawableCompat != null) {
            animatedVectorDrawableCompat.clearAnimationCallbacks();
            animatedVectorDrawableCompat.stop();
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == View.VISIBLE) {
            post(this::startAnimation);
        } else {
            post(this::stopAnimation);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        post(this::stopAnimation);

        super.onDetachedFromWindow();
    }
}
