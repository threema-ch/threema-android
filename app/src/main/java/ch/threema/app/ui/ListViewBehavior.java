package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.snackbar.Snackbar;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class ListViewBehavior extends CoordinatorLayout.Behavior<View> {
    private static final Logger logger = getThreemaLogger("ListViewBehavior");

    public ListViewBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
        return dependency instanceof Snackbar.SnackbarLayout;
    }

    @Override
    public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull View child, @NonNull View dependency) {
        logger.debug("onDependentViewChanged");

        ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
        final int height = parent.getHeight() - dependency.getHeight();

        if (height != layoutParams.height) {
            layoutParams.height = height;
            child.setLayoutParams(layoutParams);
            logger.debug("*** height: " + layoutParams.height);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onDependentViewRemoved(@NonNull CoordinatorLayout parent, View child, @NonNull View dependency) {
        logger.debug("onDependentViewRemoved");

        ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
        layoutParams.height = MATCH_PARENT;
        child.setLayoutParams(layoutParams);
    }

    @Override
    public boolean onLayoutChild(@NonNull CoordinatorLayout parent, @NonNull View child, int layoutDirection) {
        return super.onLayoutChild(parent, child, layoutDirection);
    }
}
