/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.domain.taskmanager

import ch.threema.domain.protocol.connection.ConnectionLock

/**
 * Handle several connection locks. This allows to release multiple locks at once. Adding and
 * releasing the locks is synchronized.
 */
class ConnectionLockManager {

    private val connectionLocks: MutableList<ConnectionLock> = mutableListOf()

    /**
     * Add a new connection lock. This also performs a cleanup operation to remove old unused locks.
     */
    @Synchronized
    fun addConnectionLock(connectionLock: ConnectionLock) {
        // Add the lock if it is held
        connectionLocks.add(connectionLock)
        // Remove all locks that are not held anymore because they timed out. This reduces the
        // amount of locks in case the locks are not often released.
        connectionLocks.removeIf { lock -> !lock.isHeld() }
    }

    /**
     * Release all connection locks.
     */
    @Synchronized
    fun releaseConnectionLocks() {
        for (connectionLock in connectionLocks) {
            connectionLock.release()
        }
        connectionLocks.clear()
    }
}
