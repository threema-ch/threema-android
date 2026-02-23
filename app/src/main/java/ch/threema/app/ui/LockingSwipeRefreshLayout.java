package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import ch.threema.app.R;

public class LockingSwipeRefreshLayout extends SwipeRefreshLayout {
    private int tolerancePx;

    /**
     * Prevents SwipeRefreshLayout from activating when fastscroll handle is touched
     */

    public LockingSwipeRefreshLayout(Context context) {
        super(context);

        init(context);
    }

    public LockingSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(context);
    }

    private void init(Context context) {
        tolerancePx = context.getResources().getDimensionPixelSize(R.dimen.contacts_scrollbar_tolerance);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (event.getX() > this.getWidth() - tolerancePx) {
                return false;
            }
        }
        return super.onInterceptTouchEvent(event);
    }
}
