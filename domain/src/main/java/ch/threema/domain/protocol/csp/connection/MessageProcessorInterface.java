/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.connection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor.PeerRatchetIdentifier;

/**
 * Interface for objects that wish to process incoming messages from the server.
 */
public interface MessageProcessorInterface {
	class ProcessIncomingResult {
		private final boolean processed;
		private final @Nullable Integer type;
		private final @Nullable PeerRatchetIdentifier peerRatchet;

		private ProcessIncomingResult(boolean processed, @Nullable Integer type, @Nullable PeerRatchetIdentifier peerRatchet) {
			this.processed = processed;
			this.type = type;
			this.peerRatchet = peerRatchet;
		}

		/**
		 * Processing a message failed exceptionally and processing should be retried in a
		 * subsequent connection. It should not be acked towards the chat server.
		 */
		@NonNull
		public static ProcessIncomingResult failed() {
			return new ProcessIncomingResult(false, null, null);
		}

		/**
		 * A message was successfully processed. It should be acked towards the chat server.
		 */
		@NonNull
		public static ProcessIncomingResult processed(@Nullable PeerRatchetIdentifier peerRatchet) {
			return new ProcessIncomingResult(true, null, peerRatchet);
		}

		/**
		 * A message was successfully processed. It should be acked towards the chat server.
		 *
		 * @param type The type of the message if known.
		 */
		@NonNull
		public static ProcessIncomingResult processed(@Nullable Integer type, @Nullable PeerRatchetIdentifier peerRatchet) {
			return new ProcessIncomingResult(true, type, peerRatchet);
		}

		public boolean wasProcessed() {
			return this.processed;
		}

		public boolean hasType(int type) {
			return this.type != null && this.type == type;
		}

		public @Nullable PeerRatchetIdentifier getPeerRatchetIdentifier() {
			return this.peerRatchet;
		}
	}

	/**
	 * Process an incoming message. This method should return true if the message has been processed
	 * successfully, or false on error. An ACK will only be sent to the server if the return value
	 * is true.
	 *
	 * @param boxmsg boxed message to be processed
	 */
	@WorkerThread
	@NonNull
	ProcessIncomingResult processIncomingMessage(MessageBox boxmsg);

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
