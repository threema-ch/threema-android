/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

package ch.threema.app.webclient.services;

import androidx.annotation.WorkerThread;

import ch.threema.storage.models.WebClientSessionModel;

/**
 * Handling the WebClient Wakelock
 */
@WorkerThread
public interface WakeLockService {
	/**
	 * acquire a new (or existing) wakelock for given session
	 * @param session session who acquire the wakelock
	 * @return success
	 */
	boolean acquire(WebClientSessionModel session);

	/**
	 * return true if one ore more webclient session
	 * acquired the wakelock
	 *
	 * @return if the wakelock is held
	 */
	boolean isHeld();

	/**
	 * Release the wakelock for given session.
	 * The implementation must not crash if the resources have already been released!
	 * @return success
	 */
	boolean release(WebClientSessionModel session);

	/**
	 * release all running webclient wakelocks
	 * @return success
	 */
	boolean releaseAll();
}
