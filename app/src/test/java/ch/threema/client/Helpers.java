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

import ch.threema.base.Contact;
import ch.threema.client.*;

import java.util.Collection;

public class Helpers {

	public final static String myIdentity = "TESTTST";

	public static ContactStoreInterface getContactStore() {
		return new ContactStoreInterface() {
			@Override
			public byte[] getPublicKeyForIdentity(String identity, boolean fetch) {
				return new byte[256];
			}

			@Override
			public Contact getContactForIdentity(String identity) {
				return null;
			}

			@Override
			public Collection<Contact> getAllContacts() {
				return null;
			}

			@Override
			public void addContact(Contact contact) { }

			@Override
			public void hideContact(Contact contact, boolean hide) {

			}

			@Override
			public void removeContact(Contact contact) { }

			@Override
			public void addContactStoreObserver(ContactStoreObserver observer) { }

			@Override
			public void removeContactStoreObserver(ContactStoreObserver observer) { }
		};
	}


	public static IdentityStoreInterface getIdentityStore() {
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

	public static NonceFactory getNonceFactory() {
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
}
