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

package ch.threema.domain.helpers;

import androidx.annotation.NonNull;
import ch.threema.domain.stores.IdentityStoreInterface;
import com.neilalexander.jnacl.NaCl;

/**
 * An in-memory identity store, used for testing.
 */
public class InMemoryIdentityStore implements IdentityStoreInterface {
	private String identity;
	private String serverGroup;
	private byte[] publicKey;
	private byte[] privateKey;
	private String publicNickname;

	public InMemoryIdentityStore(String identity, String serverGroup, byte[] privateKey, String publicNickname) {
		this.identity = identity;
		this.serverGroup = serverGroup;
		this.publicKey = NaCl.derivePublicKey(privateKey);
		this.privateKey = privateKey;
		this.publicNickname = publicNickname;
	}

	@Override
	public byte[] encryptData(@NonNull byte[] plaintext, @NonNull byte[] nonce, @NonNull byte[] receiverPublicKey) {
		NaCl nacl = new NaCl(privateKey, receiverPublicKey);
		return nacl.encrypt(plaintext, nonce);
	}

	@Override
	public byte[] decryptData(@NonNull byte[] ciphertext, @NonNull byte[] nonce, @NonNull byte[] senderPublicKey) {
		NaCl nacl = new NaCl(privateKey, senderPublicKey);
		return nacl.decrypt(ciphertext, nonce);
	}

	@Override
	public byte[] calcSharedSecret(@NonNull byte[] publicKey) {
		NaCl nacl = new NaCl(privateKey, publicKey);
		return nacl.getPrecomputed();
	}

	@Override
	public String getIdentity() {
		return identity;
	}

	@Override
	public String getServerGroup() {
		return serverGroup;
	}

	@Override
	public byte[] getPublicKey() {
		return publicKey;
	}

	@Override
	public String getPublicNickname() {
		return publicNickname;
	}

	public void setPublicNickname(String publicNickname) {
		this.publicNickname = publicNickname;
	}

	@Override
	public void storeIdentity(
		@NonNull String identity,
		@NonNull String serverGroup,
		@NonNull byte[] publicKey,
		@NonNull byte[] privateKey
	) {
		this.identity = identity;
		this.serverGroup = serverGroup;
		this.publicKey = publicKey;
		this.privateKey = privateKey;
	}
}
