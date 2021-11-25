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
import androidx.annotation.Nullable;
import ch.threema.domain.models.Contact;
import ch.threema.domain.stores.ContactStore;

import java.util.*;

/**
 * An in-memory contact store, used for testing.
 */
public class InMemoryContactStore implements ContactStore {
	private final Map<String, Contact> contacts = new HashMap<>();

	@Override
	public @Nullable byte[] getPublicKeyForIdentity(@NonNull String identity, boolean fetch) {
		final Contact contact = this.contacts.get(identity);
		if (contact != null) {
			return contact.getPublicKey();
		}
		return null;
	}

	@Override
	public Contact getContactForIdentity(@NonNull String identity) {
		return this.contacts.get(identity);
	}

	@Override
	public void addContact(@NonNull Contact contact) {
		this.contacts.put(contact.getIdentity(), contact);
	}

	@Override
	public void removeContact(@NonNull Contact contact) {
		this.contacts.remove(contact.getIdentity());
	}
}
