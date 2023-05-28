/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2023 Threema GmbH
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
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A Debounced OnClickListener Rejects clicks that are too close together in time. This class is
 * safe to use as an OnClickListener for multiple views, and will debounce each one separately.
 */
public abstract class DebouncedOnClickListener implements View.OnClickListener {

	private final long minimumInterval;
	private Map<View, Long> lastClickMap;

	/**
	 * Implement this in your subclass instead of onClick
	 *
	 * @param v The view that was clicked
	 */
	public abstract void onDebouncedClick(View v);

	/**
	 * The one and only constructor
	 *
	 * @param minimumIntervalMsec The minimum allowed time between clicks - any click sooner than
	 * this after a previous click will be rejected
	 */
	public DebouncedOnClickListener(long minimumIntervalMsec) {
		this.minimumInterval = minimumIntervalMsec;
		this.lastClickMap = new WeakHashMap<View, Long>();
	}

	@Override
	public void onClick(View clickedView) {
		Long previousClickTimestamp = lastClickMap.get(clickedView);
		long currentTimestamp = SystemClock.uptimeMillis();

		lastClickMap.put(clickedView, currentTimestamp);
		if (previousClickTimestamp == null || (currentTimestamp - previousClickTimestamp > minimumInterval)) {
			onDebouncedClick(clickedView);
		}
	}
}
