/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2025 Threema GmbH
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
import androidx.annotation.WorkerThread;

/**
 * A message updater is a handler that continually sends updates to the webclient.
 * It can be registered and unregistered.
 */
@WorkerThread
abstract public class MessageUpdater extends MessageHandler {
    @AnyThread
    public MessageUpdater(String subType) {
        super(subType);
    }

    @AnyThread
    public abstract void register();

    @AnyThread
    public abstract void unregister();
}
