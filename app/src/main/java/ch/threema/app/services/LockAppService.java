/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

public interface LockAppService {

    public interface OnLockAppStateChanged {
        /**
         * return true if the event will be removed from the queue
         *
         * @param locked
         * @return
         */
        boolean changed(boolean locked);
    }

    /**
     * return if app locking is enabled
     *
     * @return
     */
    boolean isLockingEnabled();

    /**
     * return if the application is locked
     *
     * @return
     */
    boolean isLocked();

    /**
     * try to unlock the application
     *
     * @param pin
     * @return
     */
    boolean unlock(String pin);

    /**
     * lock the application
     *
     * @return
     */
    void lock();

    boolean checkLock();

    /**
     * reset the timer
     */
    LockAppService resetLockTimer(boolean restartAfterReset);

    void addOnLockAppStateChanged(OnLockAppStateChanged c);

    void removeOnLockAppStateChanged(OnLockAppStateChanged c);
}
