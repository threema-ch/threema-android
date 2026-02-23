package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

public class LockableScrollView extends ScrollView {

    private boolean enableScrolling = true;

    public LockableScrollView(Context context) {
        super(context);
    }

    public LockableScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LockableScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setScrollingEnabled(boolean enabled) {
        this.enableScrolling = enabled;
    }

    public boolean isScrollable() {
        return this.enableScrolling;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // if we can scroll pass the event to the superclass
            if (this.enableScrolling) {
                return super.onTouchEvent(ev);
            }
            return this.enableScrolling;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!this.enableScrolling) {
            return false;
        } else {
            return super.onInterceptTouchEvent(ev);
        }
    }
}
