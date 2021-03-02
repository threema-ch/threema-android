/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
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

package ch.threema.app.stores;

import com.neilalexander.jnacl.NaCl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ch.threema.app.managers.ListenerManager;
import ch.threema.base.ThreemaException;
import ch.threema.client.IdentityStoreInterface;
import ch.threema.client.ProtocolDefines;

public class IdentityStore implements IdentityStoreInterface {
	private static final Logger logger = LoggerFactory.getLogger(IdentityStore.class);

	private String identity;
	private String serverGroup;
	private byte[] publicKey;
	private byte[] privateKey;
	private String publicNickname;
	private final PreferenceStoreInterface preferenceStore;

	private Map<KeyPair,NaCl> naClCache;

	public IdentityStore(PreferenceStoreInterface preferenceStore) throws ThreemaException {

		this.preferenceStore = preferenceStore;
		this.naClCache = Collections.synchronizedMap(new HashMap<>());

		this.identity = this.preferenceStore.getString(PreferenceStore.PREFS_IDENTITY);
		if (this.identity == null) {
			return;
		}

		//store private key in a crypted file

		this.serverGroup = this.preferenceStore.getString(PreferenceStore.PREFS_SERVER_GROUP);
		this.publicKey = this.preferenceStore.getBytes(PreferenceStore.PREFS_PUBLIC_KEY);
		this.privateKey = this.preferenceStore.getBytes(PreferenceStore.PREFS_PRIVATE_KEY, true);
		this.publicNickname = this.preferenceStore.getString(PreferenceStore.PREFS_PUBLIC_NICKNAME);

		if (this.identity.length() == ProtocolDefines.IDENTITY_LEN &&
				this.publicKey.length == NaCl.PUBLICKEYBYTES) {
				if (this.privateKey.length == NaCl.SECRETKEYBYTES) {
					return;
				}
				if (this.privateKey.length == 0) {
					this.privateKey = null;
					logger.debug("Private key missing");
					return;
				}
		}
		throw new ThreemaException("Bad identity file format");
	}

	public byte[] encryptData(byte[] boxData, byte[] nonce, byte[] receiverPublicKey) {
		if (privateKey != null) {
			NaCl nacl = getCachedNaCl(privateKey, receiverPublicKey);
			return nacl.encrypt(boxData, nonce);
		}
		return null;
	}

	public byte[] decryptData(byte[] boxData, byte[] nonce, byte[] senderPublicKey) {
		if (privateKey != null) {
			NaCl nacl = getCachedNaCl(privateKey, senderPublicKey);
			return nacl.decrypt(boxData, nonce);
		}
		return null;
	}

	public String getIdentity() {
		return this.identity;
	}

	public String getServerGroup() {
		return this.serverGroup;
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	public byte[] getPrivateKey() {
		return this.privateKey;
	}

	public String getPublicNickname() {
		return this.publicNickname;
	}

	public void setPublicNickname(String publicNickname) {
		this.publicNickname = publicNickname;
		this.preferenceStore.save(PreferenceStore.PREFS_PUBLIC_NICKNAME, publicNickname);
		ListenerManager.profileListeners.handle(listener -> listener.onNicknameChanged(publicNickname));
	}

	public void storeIdentity(String identity, String serverGroup, byte[] publicKey, byte[] privateKey) {

		this.identity = identity;
		this.serverGroup = serverGroup;
		this.publicKey = publicKey;
		this.privateKey = privateKey;

		this.preferenceStore.save(PreferenceStore.PREFS_IDENTITY, identity);
		this.preferenceStore.save(PreferenceStore.PREFS_SERVER_GROUP, serverGroup);
		this.preferenceStore.save(PreferenceStore.PREFS_PUBLIC_KEY, publicKey);
		this.preferenceStore.save(PreferenceStore.PREFS_PRIVATE_KEY, privateKey, true);

		//default identity
		this.setPublicNickname(identity);
	}

	public void clear() {
		this.identity = null;
		this.serverGroup = null;
		this.publicKey = null;
		this.privateKey = null;
		this.publicNickname = null;

		//remove settings
		this.preferenceStore.remove(Arrays.asList(
				PreferenceStore.PREFS_IDENTITY,
				PreferenceStore.PREFS_PRIVATE_KEY,
				PreferenceStore.PREFS_SERVER_GROUP,
				PreferenceStore.PREFS_PUBLIC_KEY,
				PreferenceStore.PREFS_PRIVATE_KEY));
	}

	private NaCl getCachedNaCl(byte[] privateKey, byte[] publicKey) {
		// Check for cached NaCl instance to save heavy Curve25519 computation
		KeyPair hashKey = new KeyPair(privateKey, publicKey);
		NaCl nacl = naClCache.get(hashKey);
		if (nacl == null) {
			nacl = new NaCl(privateKey, publicKey);
			naClCache.put(hashKey, nacl);
		}
		return nacl;
	}

	private class KeyPair {
		private final byte[] privateKey;
		private final byte[] publicKey;

		public KeyPair(byte[] privateKey, byte[] publicKey) {
			this.privateKey = privateKey;
			this.publicKey = publicKey;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			KeyPair keyPair = (KeyPair) o;
			return Arrays.equals(privateKey, keyPair.privateKey) &&
				Arrays.equals(publicKey, keyPair.publicKey);
		}

		@Override
		public int hashCode() {
			int result = Arrays.hashCode(privateKey);
			result = 31 * result + Arrays.hashCode(publicKey);
			return result;
		}
	}
}
