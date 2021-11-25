/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021 Threema GmbH
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

package ch.threema.base.crypto;

import ove.crypto.digest.Blake2b;

public class ThreemaKDF {
	private final byte[] personal;

	public ThreemaKDF(byte[] personal) {
		this.personal = personal;
	}

	public ThreemaKDF(String personal) {
		this.personal = personal.getBytes();
	}

	/**
	 * Derive a key from a secret key and a salt with BLAKE2b.
	 * @param salt Salt for key derivation
	 * @param secretKey Secret key of 32 bytes length
	 * @return
	 */
	public byte[] deriveKey(byte[] salt, byte[] secretKey) throws IllegalArgumentException {
		if (secretKey.length != 32) {
			throw new IllegalArgumentException("Wrong secret key size");
		}

		Blake2b.Param param = new Blake2b.Param()
			.setDigestLength(32)
			.setKey(secretKey)
			.setPersonal(personal)
			.setSalt(salt);

		return Blake2b.Digest.newInstance(param).digest();
	}

	public byte[] deriveKey(String salt, byte[] secretKey) throws IllegalArgumentException {
		return this.deriveKey(salt.getBytes(), secretKey);
	}
}
