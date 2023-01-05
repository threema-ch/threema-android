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

package ch.threema.domain.models;

import java.util.Arrays;
import java.util.Locale;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.Utils;
import ch.threema.domain.protocol.csp.ProtocolDefines;

/**
 * Value Class for a Threema Contact Identity (consisting of {@link ProtocolDefines.IDENTITY_LEN} bytes).
 */
public class IdentityValue {

	private final byte[] value;

	private IdentityValue(byte[] value) throws InvalidIdentityException {
		if (value.length != ProtocolDefines.IDENTITY_LEN) {
			throw new InvalidIdentityException("Invalid Identity length" + value.length);
		}
		this.value = value;
	}


	public static @NonNull
	IdentityValue fromByteArray(@NonNull byte[] value) throws InvalidIdentityException {
		return new IdentityValue(value);
	}

	/**
	 * @param value little endian encoded Threema ID
	 * @throws InvalidIdentityException if the long value is not a valid Threema ID
	 */
	public static @NonNull IdentityValue fromLong(long value) throws InvalidIdentityException {
		return new IdentityValue(Utils.longToByteArray(value));
	}

	public static @NonNull IdentityValue fromHexString(@NonNull String value) throws InvalidIdentityException {
		return new IdentityValue(Utils.hexStringToByteArray(value));
	}

	public byte[] toByteArray() {
		return this.value;
	}

	@Override
	public String toString() {
		return Utils.byteArrayToHexString(this.value).toUpperCase(Locale.ROOT);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (obj.getClass() != this.getClass())
			return false;

		return Arrays.equals(this.value, ((IdentityValue)obj).value);
	}

	@Override
	public int hashCode() {
		/* Identities are usually random, so just taking the first four bytes is fine */
		return this.value[0] << 24 | (this.value[1] & 0xFF) << 16 | (this.value[2] & 0xFF) << 8 | (this.value[3] & 0xFF);
	}

	public static class InvalidIdentityException extends ThreemaException {
		public InvalidIdentityException(final String msg) {
			super(msg);
		}
	}
}
