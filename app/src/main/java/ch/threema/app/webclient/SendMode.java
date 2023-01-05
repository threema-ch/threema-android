/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2023 Threema GmbH
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

package ch.threema.app.webclient;

/**
 * Send mode to be used by the web client.
 */
public enum SendMode {
	/**
	 * Will either complete the send operation immediately or queue it to be
	 * sent.
	 */
	ASYNC,

	/**
	 * Will block the thread until the sending operation has been completed.
	 *
	 * Important: This bypasses the message queue and is therefore dangerous!
	 *            Only use this to dispatch messages when you know that the
	 *            queue is empty or when you don't care (e.g. for *last will*
	 *            messages)
	 */
	UNSAFE_SYNC,
}
