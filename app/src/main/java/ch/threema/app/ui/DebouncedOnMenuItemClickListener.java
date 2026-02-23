package ch.threema.app.ui;

import android.os.SystemClock;
import android.view.MenuItem;
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A Debounced OnClickListener Rejects clicks that are too close together in time. This class is
 * safe to use as an OnClickListener for multiple views, and will debounce each one separately.
 */
public abstract class DebouncedOnMenuItemClickListener implements MenuItem.OnMenuItemClickListener {

    private final long minimumInterval;
    private Map<MenuItem, Long> lastClickMap;

    /**
     * Implement this in your subclass instead of onClick
     *
     * @param item The MenuItem that was clicked
     */
    public abstract boolean onDebouncedMenuItemClick(MenuItem item);

    /**
     * @param minimumIntervalMsec The minimum allowed time between clicks - any click sooner than
     *                            this after a previous click will be rejected
     */
    public DebouncedOnMenuItemClickListener(long minimumIntervalMsec) {
        this.minimumInterval = minimumIntervalMsec;
        this.lastClickMap = new WeakHashMap<>();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        Long previousClickTimestamp = lastClickMap.get(item);
        long currentTimestamp = SystemClock.uptimeMillis();

        lastClickMap.put(item, currentTimestamp);
        if (previousClickTimestamp == null || (currentTimestamp - previousClickTimestamp > minimumInterval)) {
            return onDebouncedMenuItemClick(item);
        }
        // mark as consumed
        return true;
    }
}
