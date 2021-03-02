/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.client;

import androidx.annotation.WorkerThread;

/**
 * Interface for objects that wish to process incoming messages from the server.
 */
public interface MessageProcessorInterface {
	class ProcessIncomingResult {
		public final boolean processed;
		public final AbstractMessage abstractMessage;

		public ProcessIncomingResult(boolean processed, AbstractMessage abstractMessage) {
			this.processed = processed;
			this.abstractMessage = abstractMessage;
		}

		public static ProcessIncomingResult failed() {
			return new ProcessIncomingResult(false, null);
		}
		public static ProcessIncomingResult ignore() {
			return new ProcessIncomingResult(true, null);
		}
		public static ProcessIncomingResult ok(AbstractMessage abstractMessage) {
			return new ProcessIncomingResult(true, abstractMessage);
		}
	}
	/**
	 * Process an incoming message. This method should return true if the message has been processed
	 * successfully, or false on error. An ACK will only be sent to the server if the return value is true.
	 *
	 * @param boxmsg boxed message to be processed
	 */
	@WorkerThread
	ProcessIncomingResult processIncomingMessage(BoxedMessage boxmsg);

	/**
	 * Process a server alert message. This is an informative message that the server may send
	 * at any time during the connection (but most commonly directly after login in the form of a
	 * "message of the day". Such a message does not consist an error, and it should be displayed
	 * to the user in a suitable form (e.g. alert box).
	 *
	 * ThreemaConnection remembers previous messages (in memory), so that each message will only
	 * be shown once.
	 *
	 * @param alertmsg the alert message text
	 */
	@WorkerThread
	void processServerAlert(String alertmsg);

	/**
	 * Process a server error message. This is a fatal error message that the server may send
	 * at any time during the connection. The connection is disconnected by the server after
	 * sending the message. The boolean flag indicates whether the connection may be re-established
	 * automatically, or whether this is not allowed (e.g. because the error is likely to re-occur,
	 * leading to an endless reconnect loop).
	 *
	 * @param errormsg the error message text
	 * @param reconnectAllowed if true, the connection may be reconnected automatically
	 */
	@WorkerThread
	void processServerError(String errormsg, boolean reconnectAllowed);
}
