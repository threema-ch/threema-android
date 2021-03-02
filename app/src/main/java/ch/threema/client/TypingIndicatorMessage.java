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

/**
 * A "throw-away" message that signals that the sender is currently typing a message or has
 * stopped typing (depending on the boolean flag {@code typing}.
 */
public class TypingIndicatorMessage extends AbstractMessage {

	private boolean typing;

	public TypingIndicatorMessage() {
		super();
	}

	@Override
	public int getType() {
		return ProtocolDefines.MSGTYPE_TYPING_INDICATOR;
	}

	@Override
	public byte[] getBody() {
		byte[] body = new byte[1];
		body[0] = typing ? (byte)1 : (byte)0;
		return body;
	}

	@Override
	public boolean isImmediate() {
		return true;
	}

	@Override
	public boolean isNoAck() {
		return true;
	}

	public boolean isTyping() {
		return typing;
	}

	public void setTyping(boolean typing) {
		this.typing = typing;
	}
}
