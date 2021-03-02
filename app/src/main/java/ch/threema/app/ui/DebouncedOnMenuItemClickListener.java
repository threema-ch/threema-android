/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2021 Threema GmbH
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
	 * this after a previous click will be rejected
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
