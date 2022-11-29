/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.domain.models.Contact;

public class DummyContactStore implements ContactStore {
	private Map<String,Contact> contactMap;

	public DummyContactStore() {
		this.contactMap = new HashMap<>();
	}

	@Nullable
	@Override
	public Contact getContactForIdentity(@NonNull String identity, boolean fetch, boolean saveContact) {
		return getContactForIdentity(identity);
	}

	@Nullable
	@Override
	public Contact getContactForIdentity(@NonNull String identity) {
		return contactMap.get(identity);
	}

	@Override
	public void addContact(@NonNull Contact contact) {
		contactMap.put(contact.getIdentity(), contact);
	}

	@Override
	public void addContact(@NonNull Contact contact, boolean hide) {
		contactMap.put(contact.getIdentity(), contact);
	}

	@Override
	public void removeContact(@NonNull Contact contact) {
		contactMap.remove(contact.getIdentity());
	}
}
