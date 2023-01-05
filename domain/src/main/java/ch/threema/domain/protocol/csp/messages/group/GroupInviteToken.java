/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2023 Threema GmbH
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

package ch.threema.domain.protocol.csp.messages.group;

import java.util.Arrays;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;

/**
 * Value class for a Group Invite Token
 */
public class GroupInviteToken {
	private final @NonNull byte[] value;

	public GroupInviteToken(@NonNull byte[] value) throws InvalidGroupInviteTokenException {
		if (value.length != ProtocolDefines.GROUP_INVITE_TOKEN_LEN) {
			throw new InvalidGroupInviteTokenException("Invalid token size " + value.length);
		}
		this.value = value;
	}

	public static @NonNull GroupInviteToken fromHexString(@NonNull String value) throws InvalidGroupInviteTokenException {
		return new GroupInviteToken(Utils.hexStringToByteArray(value));
	}

	@NonNull
	public byte[] get() {
		return this.value;
	}

	/**
	 * Returns a hexadecimal string representation of the token.
	 */
	@Override
	public @NonNull String toString() {
		return Utils.byteArrayToHexString(this.value);
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || this.getClass() != o.getClass()) return false;
		final GroupInviteToken that = (GroupInviteToken) o;
		return Arrays.equals(this.value, that.value);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.value);
	}

	public static class InvalidGroupInviteTokenException extends ThreemaException {
		public InvalidGroupInviteTokenException(final String msg) {
			super(msg);
		}
	}
}
