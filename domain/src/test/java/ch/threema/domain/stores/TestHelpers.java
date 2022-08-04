/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2017-2022 Threema GmbH
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

package ch.threema.domain.stores;

import androidx.annotation.NonNull;
import ch.threema.base.ThreemaException;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.crypto.NonceStoreInterface;
import ch.threema.domain.models.Contact;
import ch.threema.domain.protocol.csp.coders.MessageBox;
import ch.threema.domain.protocol.csp.coders.MessageCoder;
import ch.threema.domain.protocol.csp.messages.AbstractMessage;
import ch.threema.domain.protocol.csp.messages.BadMessageException;
import ch.threema.domain.protocol.csp.messages.MissingPublicKeyException;

public class TestHelpers {

	public final static String myIdentity = "TESTTST";

	public static ContactStore getNoopContactStore() {
		return new ContactStore() {
			@Override
			public Contact getContactForIdentity(@NonNull String identity, boolean fetch, boolean saveContact) {
				return new Contact(identity, new byte[256]);
			}

			@Override
			public Contact getContactForIdentity(@NonNull String identity) {
				return null;
			}

			@Override
			public void addContact(@NonNull Contact contact) { }

			@Override
			public void addContact(@NonNull Contact contact, boolean hide) { }

			@Override
			public void removeContact(@NonNull Contact contact) { }
		};
	}

	public static IdentityStoreInterface getNoopIdentityStore() {
		return new IdentityStoreInterface() {
			@Override
			public byte[] encryptData(byte[] plaintext, byte[] nonce, byte[] receiverPublicKey) {
				return plaintext;
			}

			@Override
			public byte[] decryptData(byte[] ciphertext, byte[] nonce, byte[] senderPublicKey) {
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
			public String getPublicNickname() {
				return null;
			}

			@Override
			public void storeIdentity(String identity, String serverGroup, byte[] publicKey, byte[] privateKey) { }
		};
	}

	public static NonceFactory getNoopNonceFactory() {
		return new NonceFactory(new NonceStoreInterface() {
			@Override
			public boolean exists(byte[] nonce) {
				return false;
			}

			@Override
			public boolean store(byte[] nonce) {
				return true;
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
		return messageCoder.encode(msg, getNoopNonceFactory());
	}

	public static AbstractMessage decodeMessageFromBox(@NonNull MessageBox boxedMessage) throws MissingPublicKeyException, BadMessageException {
		MessageCoder messageCoder = new MessageCoder(getNoopContactStore(), getNoopIdentityStore());
		return messageCoder.decode(
			boxedMessage,
			true);
	}


}
