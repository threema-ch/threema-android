/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2021-2024 Threema GmbH
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

package ch.threema.domain.helpers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.base.crypto.NonceStore;

/**
 * An in-memory identity store, used for testing.
 */
public class InMemoryNonceStore implements NonceStore {
	private final HashSet<byte[]> nonces = new HashSet<>();

	@Override
	public boolean exists(@NonNull byte[] nonce) {
		return this.nonces.contains(nonce);
	}

	@Override
	public boolean store(@NonNull byte[] nonce) {
		return this.nonces.add(nonce);
	}

	@NonNull
	@Override
	public List<byte[]> getAllHashedNonces() {
		List<byte[]> hashedNonces = new ArrayList<>(nonces.size());
		hashedNonces.addAll(nonces);
		return hashedNonces;
	}
}
