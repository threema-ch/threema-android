package ch.threema.app.ui;

import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.AttributeSet;

public class LockableLinearLayoutManager extends LinearLayoutManager {
    private boolean isScrollEnabled = true;

    public LockableLinearLayoutManager(Context context) {
        super(context);
    }

    public LockableLinearLayoutManager(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public LockableLinearLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }

    public void setScrollEnabled(boolean flag) {
        this.isScrollEnabled = flag;
    }

    @Override
    public boolean canScrollHorizontally() {
        return isScrollEnabled && super.canScrollHorizontally();
    }
}
