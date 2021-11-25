/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.services;

import android.content.Intent;

public interface LifetimeService {

	interface LifetimeServiceListener {
		boolean connectionStopped();

	}
    /**
     * Acquire the connection. If not already connected, the connection will be started immediately,
     * and kept active until releaseConnection() is called. A call to acquireConnection() must always
     * be balanced with a call to releaseConnection().
     *
     * Note: The source is used purely for diagnostic purposes and not for deduplication.
     * If you call this method twice with the same source string, then two connection counters
     * will be acquired.
     *
     * @param source identifier of the calling object (for debugging)
     */
    void acquireConnection(String source);

    /**
     * Release the connection. If no other callers have acquired the connection anymore, it will
     * be closed.
     *
     * @param source identifier of the calling object (for debugging)
     */
    void releaseConnection(String source);

    /**
     * Release the connection, but keep it active ("linger") for the given amount of milliseconds
     *
     * @param source identifier of the calling object (for debugging)
     * @param timeoutMs time in milliseconds that the connection should linger
     */
    void releaseConnectionLinger(String source, long timeoutMs);

    /**
     * Set polling interval.
     *
     * @param intervalMs polling interval in milliseconds, or 0 to disable
     */
    void setPollingInterval(long intervalMs);

	/**
	 * Alarm elapsed (for use by AlarmManagerBroadcastReceiver)
	 */
    void alarm(Intent intent);

	boolean isActive();

	void addListener(LifetimeServiceListener listener);
	void removeListener(LifetimeServiceListener listener);
	void clearListeners();
}
