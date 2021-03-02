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

import com.neilalexander.jnacl.NaCl;

class NonceCounter {
	private final byte[] cookie;
	private long nextNonce;

	public NonceCounter(byte[] cookie) {
		this.cookie = cookie;
		nextNonce = 1;
	}

	public synchronized byte[] nextNonce() {
		byte[] nonce = new byte[NaCl.NONCEBYTES];
		System.arraycopy(cookie, 0, nonce, 0, ProtocolDefines.COOKIE_LEN);
		for (int i = 0; i < 8; i++) {
			nonce[i+ProtocolDefines.COOKIE_LEN] = (byte)(nextNonce >> (i * 8));
		}

		nextNonce++;
		return nonce;
	}
}
