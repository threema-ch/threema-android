/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema Java Client
 * Copyright (c) 2017-2021 Threema GmbH
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

import ch.threema.base.ThreemaException;
import com.neilalexander.jnacl.NaCl;

import java.security.SecureRandom;

/**
 * Interface for identity stores.
 */
final public class NonceFactory {
	private static final int RANDMON_TRIES = 5;
	private final SecureRandom secureRandom;
	private final NonceStoreInterface nonceStore;

	public NonceFactory(NonceStoreInterface nonceStore) {
		this(new SecureRandom(), nonceStore);
	}
	public NonceFactory(SecureRandom secureRandom,
						NonceStoreInterface nonceStore) {
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
	public synchronized byte[] next(boolean save) throws ThreemaException {
		byte[] nonce = new byte[NaCl.NONCEBYTES];
		int tries = 0;
		boolean success = !save;
		do {
			this.secureRandom.nextBytes(nonce);
			if (save) {
				success = this.store(nonce);
				if (!success && tries++ > RANDMON_TRIES) {
					throw new ThreemaException("failed to generate a random nonce");
				}
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
}
