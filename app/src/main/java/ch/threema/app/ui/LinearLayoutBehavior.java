package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class LinearLayoutBehavior extends CoordinatorLayout.Behavior<LinearLayout> {
    private static final Logger logger = getThreemaLogger("LinearLayoutBehavior");

    public LinearLayoutBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, LinearLayout child, View dependency) {
        return dependency instanceof Snackbar.SnackbarLayout;
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, LinearLayout child, View dependency) {
        float translationY = Math.min(0, dependency.getTranslationY() - dependency.getHeight());
        child.setTranslationY(translationY);

        return true;
    }

    @Override
    public void onDependentViewRemoved(CoordinatorLayout parent, LinearLayout child, View dependency) {
        child.setTranslationY(0);
    }
}
