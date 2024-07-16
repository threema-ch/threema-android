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

package ch.threema.domain.testhelpers;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.NonceStore;
import ch.threema.domain.models.Contact;
import ch.threema.domain.models.VerificationLevel;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;
import ch.threema.domain.stores.ContactStore;
import ch.threema.domain.stores.IdentityStoreInterface;

public class TestHelpers {

	public final static String myIdentity = "TESTTST";

	public static ContactStore getNoopContactStore() {
		return new ContactStore() {
			@Override
			public Contact getContactForIdentity(@NonNull String identity) {
				return new Contact(identity, new byte[256], VerificationLevel.UNVERIFIED);
			}

			@Override
			public void addCachedContact(@NonNull Contact contact) {

			}

			@Override
			public Contact getContactForIdentityIncludingCache(@NonNull String identity) {
				return getContactForIdentity(identity);
			}

			@Override
			public void addContact(@NonNull Contact contact) { }

			@Override
			public void removeContact(@NonNull Contact contact) { }
		};
	}

	public static IdentityStoreInterface getNoopIdentityStore() {
		return new IdentityStoreInterface() {
			@Override
			public byte[] encryptData(@NonNull byte[] plaintext, @NonNull byte[] nonce, @NonNull byte[] receiverPublicKey) {
				return plaintext;
			}

			@Override
			public byte[] decryptData(@NonNull byte[] ciphertext, @NonNull byte[] nonce, @NonNull byte[] senderPublicKey) {
				return ciphertext;
			}

			@Override
			public byte[] calcSharedSecret(@NonNull byte[] publicKey) {
				return new byte[32];
			}

			@Override
			public String getIdentity() {
				return myIdentity;
			}

			@Override
			public String getServerGroup() {
				return null;
			}

			@Override
			public byte[] getPublicKey() {
				return new byte[256];
			}

			@Override
			public byte[] getPrivateKey() {
				return new byte[32];
			}

			@Override
			@NonNull
			public String getPublicNickname() {
				return "";
			}

			@Override
			public void storeIdentity(@NonNull String identity, @NonNull String serverGroup, @NonNull byte[] publicKey, @NonNull byte[] privateKey) { }
		};
	}

	public static NonceFactory getNoopNonceFactory() {
		return new NonceFactory(new NonceStore() {
			@Override
			public boolean exists(@NonNull byte[] nonce) {
				return false;
			}

			@Override
			public boolean store(@NonNull byte[] nonce) {
				return true;
			}

			@NonNull
			@Override
			public List<byte[]> getAllHashedNonces() {
				return Collections.emptyList();
			}
		});
	}

	/**
	 * Adds a default sender and receiver to a message
	 */
	public static AbstractMessage setMessageDefaults(AbstractMessage msg) {
		final String toIdentity = "ABCDEFGH";

		msg.setFromIdentity(toIdentity);
		msg.setToIdentity(myIdentity);

		return msg;
	}

	public static MessageBox boxMessage(AbstractMessage msg) throws ThreemaException {
		MessageCoder messageCoder = new MessageCoder(getNoopContactStore(), getNoopIdentityStore());
		NonceFactory nonceFactory = getNoopNonceFactory();
		byte[] nonce = nonceFactory.next(false);
		return messageCoder.encode(msg, nonce, nonceFactory);
	}

	public static AbstractMessage decodeMessageFromBox(@NonNull MessageBox boxedMessage) throws MissingPublicKeyException, BadMessageException {
		MessageCoder messageCoder = new MessageCoder(getNoopContactStore(), getNoopIdentityStore());
		return messageCoder.decode(boxedMessage);
	}


}
