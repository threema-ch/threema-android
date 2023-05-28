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

package ch.threema.domain.fs;

import java.security.SecureRandom;
import java.util.Arrays;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;

/**
 * Value Class for a DH session ID (consisting of {@link ProtocolDefines.FS_SESSION_ID_LENGTH} bytes).
 */
public class DHSessionId implements Comparable<DHSessionId> {
	private final @NonNull byte[] value;

	/**
	 * Generate a new random session ID.
	 */
	public DHSessionId() {
		SecureRandom random = new SecureRandom();
		this.value = new byte[ProtocolDefines.FS_SESSION_ID_LENGTH];
		random.nextBytes(this.value);
	}

	public DHSessionId(@NonNull byte[] value) throws DHSessionId.InvalidDHSessionIdException {
		if (value.length != ProtocolDefines.FS_SESSION_ID_LENGTH) {
			throw new DHSessionId.InvalidDHSessionIdException("Invalid session ID length " + value.length);
		}
		this.value = value;
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
		final DHSessionId that = (DHSessionId) o;
		return Arrays.equals(this.value, that.value);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.value);
	}

	@Override
	public int compareTo(@NonNull DHSessionId o) {
		for (int i = 0; i < ProtocolDefines.FS_SESSION_ID_LENGTH; i++) {
			if (this.value[i] != o.value[i]) {
				return ((int)this.value[i] & 0xff) - ((int)o.value[i] & 0xff);
			}
		}
		return 0;
	}

	public static class InvalidDHSessionIdException extends ThreemaException {
		public InvalidDHSessionIdException(final String msg) {
			super(msg);
		}
	}
}
