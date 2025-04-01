/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

package ch.threema.app.webclient.services.instance;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import org.saltyrtc.client.crypto.CryptoException;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.nio.ByteBuffer;

import ch.threema.app.webclient.SendMode;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.storage.models.WebClientSessionModel;

/**
 * Interface of the Webclient service.
 */
@WorkerThread
public interface SessionInstanceService {
    /**
     * Return whether this session is in a non-terminal state.
     */
    boolean isRunning();

    /**
     * Return the current state of the session.
     */
    @NonNull
    WebClientSessionState getState();

    /**
     * Return whether the session needs to be restarted (due to a different affiliation id).
     */
    boolean needsRestart(@Nullable String affiliationId);

    /**
     * Return the session model.
     */
    @AnyThread
    @NonNull
    WebClientSessionModel getModel();

    /**
     * Start a session.
     * <p>
     * Note: Will be ignored when the session is running!
     */
    void start(@NonNull byte[] permanentKey, @NonNull byte[] authToken, @Nullable String affiliationId);

    /**
     * Resume a session by the saved permanent key.
     * <p>
     * Note: Will be ignored when the session is running!
     */
    void resume(@Nullable String affiliationId) throws CryptoException;

    /**
     * Stop a session.
     * <p>
     * Note: Will be ignored when the session is not running!
     */
    void stop(@NonNull DisconnectContext reason);

    /**
     * Send data to the peer.
     *
     * @param message Msgpack encoded bytes
     */
    void send(@NonNull ByteBuffer message, @NonNull SendMode mode);
}
