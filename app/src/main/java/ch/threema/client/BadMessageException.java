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

public class BadMessageException extends Exception {

	private final boolean drop;

	public BadMessageException(String msg, boolean shouldDrop) {
		super(msg);
		this.drop = shouldDrop;
	}

	public BadMessageException(String msg) {
		this(msg, false);
	}

	public BadMessageException(String msg, boolean shouldDrop, Throwable cause) {
		super(msg, cause);
		this.drop = shouldDrop;
	}

	public BadMessageException(String msg, Throwable cause) {
		this(msg, false, cause);
	}

	/**
	 * Return whether this message should be dropped and acked.
	 * If set to false, no ack should be sent, resulting in a retransmission
	 * of the message by the server.
	 */
	public boolean shouldDrop() {
		return drop;
	}
}
