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

package ch.threema.app.webclient.services.instance.message.receiver;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;

/**
 * Webclient sending key persisted information
 */
@WorkerThread
public class KeyPersistedRequestHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("KeyPersistedRequestHandler");

    private Listener listener;

    @WorkerThread
    public interface Listener {
        void onReceived();
    }

    @AnyThread
    public KeyPersistedRequestHandler(Listener listener) {
        super(Protocol.SUB_TYPE_KEY_PERSISTED);
        this.listener = listener;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Received key persisted request");
        if (this.listener != null) {
            this.listener.onReceived();
        }
        //do not respond
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}
