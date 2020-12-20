/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2020 Threema GmbH
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

import org.msgpack.core.MessagePackException;
import org.msgpack.value.Value;

import java.util.Map;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;

/**
 * Receive messages from the webclient.
 */
@WorkerThread
abstract public class MessageReceiver extends MessageHandler {
	@AnyThread
	public MessageReceiver(String subType) {
		super(subType);
	}

	protected abstract void receive(Map<String, Value> message) throws MessagePackException;

	protected boolean receive(String subType, Map<String, Value> message) throws MessagePackException {
		// Are we receiving this sub type?
		if (subType.equals(this.subType)) {
			this.receive(message);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * If this method returns `true`, then after processing the data the dispatcher will check
	 * whether any outgoing messages have been enqueued. If there are outgoing messages in the queue
	 * and the app is disconnected, the connection will be opened to send those messages.
	 *
	 * A handler like the `TextMessageCreateHandler` should return `true`, while a handler like
	 * `BatteryStatusRequestHandler` should return `false`.
	 */
	protected abstract boolean maybeNeedsConnection();
}
