/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2025 Threema GmbH
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

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Handle Web wakeups (via push notifications) that should be processed later.
 */
@AnyThread
public interface SessionWakeUpService {
    /**
     * Resume a web client session. If the session cannot be resumed immediately, a pending wakeup
     * will be stored.
     * <p>
     * Note: The starting operation will be performed asynchronously from the worker thread.
     *
     * @param publicKeySha256String The SHA256 hash of the public session key.
     * @param version               The protocol version
     * @param affiliationId         An optional affiliation id assigned to a group of connection attempts.
     */
    void resume(@NonNull String publicKeySha256String, int version, @Nullable String affiliationId);

    /**
     * Go through all pending wakeups and start the corresponding sessions, if possible.
     * <p>
     * Note: This can only be run from the worker thread.
     */
    @WorkerThread
    void processPendingWakeups();

    /**
     * Go through all pending wakeups and start the corresponding sessions, if possible.
     * <p>
     * Note: The wakeup will be dispatched asynchronously to the worker thread.
     */
    void processPendingWakeupsAsync();

    /**
     * Discard all pending wakeups.
     */
    @WorkerThread
    void discardPendingWakeups();
}
