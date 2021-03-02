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

import ch.threema.base.Contact;

import java.util.Collection;

public interface ContactStoreInterface {

	/**
	 * Obtain the public key for the given identity.
	 *
	 * @param identity desired identity
	 * @param fetch if true, attempt to synchronously fetch the key from the server if necessary
	 * @return public key, or null if not found (or fetch failed)
	 */
	byte[] getPublicKeyForIdentity(String identity, boolean fetch);

	Contact getContactForIdentity(String identity);

	Collection<Contact> getAllContacts();

	void addContact(Contact contact);

	void hideContact(Contact contact, boolean hide);

	void removeContact(Contact contact);

	void addContactStoreObserver(ContactStoreObserver observer);
	void removeContactStoreObserver(ContactStoreObserver observer);
}
