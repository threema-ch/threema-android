/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;
import org.slf4j.Logger;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import ch.threema.app.webclient.services.instance.MessageReceiver;
import ch.threema.base.utils.LoggingUtil;

/**
 * A handler that logs and ignores incoming messages for the specified subtype.
 */
@WorkerThread
public class IgnoreRequestHandler extends MessageReceiver {
    private static final Logger logger = LoggingUtil.getThreemaLogger("IgnoreRequestHandler");

    private final @NonNull String type;

    @AnyThread
    public IgnoreRequestHandler(final @NonNull String type, final @NonNull String subType) {
        super(subType);
        this.type = type;
    }

    @Override
    protected void receive(Map<String, Value> message) throws MessagePackException {
        logger.debug("Ignoring incoming {}/{} message", this.type, this.subType);
    }

    @Override
    protected boolean maybeNeedsConnection() {
        return false;
    }
}
