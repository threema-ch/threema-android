package ch.threema.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import org.slf4j.Logger;

import androidx.viewpager.widget.ViewPager;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

public class LockableViewPager extends ViewPager {
    private static final Logger logger = getThreemaLogger("LockableViewPager");

    private boolean locked = false;

    public LockableViewPager(Context context) {
        super(context);
    }

    public LockableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void lock(boolean lock) {
        this.locked = lock;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!this.locked) {
            boolean result = false;

            try {
                result = super.onTouchEvent(event);
            } catch (Exception e) {
                logger.debug(e.getMessage());
            }
            return result;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!this.locked) {
            boolean result = false;

            try {
                result = super.onInterceptTouchEvent(event);
            } catch (Exception e) {
                logger.debug(e.getMessage());
            }
            return result;
        }
        return false;
    }
}
