/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2014-2025 Threema GmbH
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

package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.storage.models.ballot.BallotModel;

public interface BallotListener {
    @AnyThread
    void onClosed(final BallotModel ballotModel);

    @AnyThread
    void onModified(final BallotModel ballotModel);

    @AnyThread
    void onCreated(final BallotModel ballotModel);

    @AnyThread
    void onRemoved(final BallotModel ballotModel);

    /**
     * return true, if the event have to be handled!
     *
     * @param ballotModel
     * @return
     */
    @AnyThread
    boolean handle(final BallotModel ballotModel);
}
