/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2024 Threema GmbH
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

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import com.neilalexander.jnacl.NaCl;

import java.security.SecureRandom;
import java.util.List;

/**
 * Interface for identity stores.
 */
final public class NonceFactory {
	private final SecureRandom secureRandom;
	private final NonceStore nonceStore;

	public NonceFactory(NonceStore nonceStore) {
		this(new SecureRandom(), nonceStore);
	}
	public NonceFactory(SecureRandom secureRandom,
						NonceStore nonceStore) {
		this.secureRandom = secureRandom;
		this.nonceStore = nonceStore;
	}

	/**
	 * Create the next unique nonce
	 * @return nonce
	 */
	public synchronized byte[] next() throws ThreemaException {
		return this.next(true);
	}

	/**
	 * Create the next unique nonce
	 * @param save
	 * @return nonce
	 */
	public synchronized byte[] next(boolean save) {
		byte[] nonce = new byte[NaCl.NONCEBYTES];
		boolean success;
		do {
			this.secureRandom.nextBytes(nonce);
			// The nonce has been created successfully if it does not exist yet.
			if (save) {
				success = this.store(nonce);
			} else {
				success = !this.exists(nonce);
			}
		} while(!success);

		return nonce;
	}

	/**
	 * Store the nonce into the nonce store
	 * @param nonce
	 * @return
	 */
	public synchronized boolean store(byte[] nonce) {
		return this.nonceStore.store(nonce);
	}

	/**
	 * Return true if the given nonce already exists
	 *
	 * @param nonce
	 * @return
	 */
	public boolean exists(byte[] nonce) {
		return this.nonceStore.exists(nonce);
	}

	@NonNull
	public List<byte[]> getAllHashedNonces() {
		return this.nonceStore.getAllHashedNonces();
	}
}
